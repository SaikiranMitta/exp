import boto3
import json
from models.projectUserModels import ProjectUser as ProjectUserModel
from models.notificationModels import NotificationStatus, NotificationSeverity, Notification
from models.commonImports import *
from common.customExceptions import AnyExceptionHandler, AttributeNotPresent
from dotenv import load_dotenv
from models.database.dbConnection import session
from models.userRoleModels import UserRole as UserRoleModel
from models.roleModels import Role as RoleModel
from models.projectModels import Project as ProjectModel
from models.assessmentModels import Assessment as AssessmentModel
from sqlalchemy import or_
load_dotenv()


class NotificationSender:
    def __init__(self):
        self.sqs = boto3.client('sqs')
        self.queue_url = os.getenv("EMAIL_QUEUE_URL")

    def createNotificationPayload(self, event_type, description, user_id):
        notification_payload = {"description": description, "status": NotificationStatus.NEW,
                                "severity": NotificationSeverity.HIGH, "user_id": user_id}
        if event_type == "assessment-created":
            notification_payload.update({"message": "New assessment created"})
        elif event_type == "project-user-created":
            notification_payload.update({"message": "New project created"})
        elif event_type == "assessment-updated":
            notification_payload.update({"message": "Assessment updated"})
        elif event_type == "assessment-reviewed":
            notification_payload.update({"message": "Assessment reviewed"})

        return notification_payload

    def storeNotifications(self, notifications):
        # save Notifications in DB
        for notification in notifications:
            notification_record = Notification(
                user_id=notification["user_id"],
                message=notification["message"],
                description=notification["description"],
                status=notification["status"],
                severity=notification["severity"]
            )
            try:
                session.add(notification_record)
                session.commit()
                session.refresh(notification_record)
            except Exception as ex:
                session.rollback()
                raise AnyExceptionHandler(ex)

        session.close()

        for notification in notifications:
            email_payload = {
                'user_id': notification['user_id'],
                'message': notification['message'],
                'description': notification['description']
            }
            # sending message to sqs queue
            response = self.sqs.send_message(
                QueueUrl=self.queue_url, MessageBody=json.dumps(email_payload))

        return response

    def _checkCreateNotificationParameters(self, **kwargs):
        topic_arn = kwargs.get("TopicArn")
        if "assessment-created" in topic_arn:
            required_parameters = {"project_id": "Project Id"}
        elif "project-user-created" in topic_arn:
            required_parameters = {"project_id": "Project Id",
                                   "user_id": "User Id"}
        elif "assessment-updated" or "assessment-reviewed" in topic_arn:
            required_parameters = {"project_id": "Project Id",
                                   "assessment_id": "Assessment Id"}
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    def create_notification(self, **kwargs):
        self._checkCreateNotificationParameters(**kwargs)
        topic_arn = kwargs.get("TopicArn")
        project_id = kwargs.get("project_id")
        project_name = (session.query(ProjectModel).filter(
                ProjectModel.id == project_id).first())

        if "assessment-created" in topic_arn:
            project_users = session.query(ProjectUserModel).filter(
                ProjectUserModel.project_id == project_id)
            Notifications = []
            assessment_id = kwargs.get("assessment_id")
            assessment_info = (session.query(AssessmentModel).filter(
                AssessmentModel.id == assessment_id).first())
            description = "Assessment with name {0} has been created for {1} \
                    with due date as {2}".format(assessment_info.name,
                                                 project_name.name, assessment_info.end_date)

            for project_user in project_users:
                message = self.createNotificationPayload("assessment-created",
                                                         description, project_user.user_id)
                Notifications.append(message)

        elif "project-user-created" in topic_arn:
            user_id = kwargs.get("user_id")
            user_role = (session.query(UserRoleModel).filter(
                UserRoleModel.user_id == user_id).first())
            project_role = (session.query(RoleModel).filter(
                RoleModel.id == user_role.role_id).first())

            project_users = session.query(ProjectUserModel).filter(
                ProjectUserModel.project_id == project_id,
                ProjectUserModel.user_id == user_id)
            Notifications = []
            description = "You have been assigned to {0} project \
                    with {1} as a role".format(project_name.name, project_role.name)

            for project_user in project_users:
                message = self.createNotificationPayload("project-user-created",
                                                         description, project_user.user_id)
                Notifications.append(message)

        elif "assessment-updated" in topic_arn and kwargs.get("status") == "Submitted":
            reviewers_and_admin = session.query(RoleModel).filter(
                or_(RoleModel.name == 'Reviewer', RoleModel.name == 'Admin'))
            
            reviewer_and_admin_role_id = []
            for reviewer in reviewers_and_admin:
                reviewer_and_admin_role_id.append(str(reviewer.id))

            Notifications = []
            assessment_id = kwargs.get("assessment_id")
            assessment_info = (session.query(AssessmentModel).filter(
                AssessmentModel.id == assessment_id).first())
            description = "Assessment for {0} with name {1} has been submitted \
                    for review with due date as {2}".format(project_name.name, assessment_info.name, 
                                                            assessment_info.end_date)
            
            reviewers = session.query(UserRoleModel).filter(
                UserRoleModel.role_id.in_(reviewer_and_admin_role_id))

            for reviewer_id in reviewers:
                message = self.createNotificationPayload("assessment-updated",
                                                         description, reviewer_id.user_id)
                Notifications.append(message)

        elif "assessment-updated" in topic_arn and kwargs.get("status") == "Reviewed":
            project_managers_and_admin = []
            admin_role_id = session.query(RoleModel).filter(
                (RoleModel.name == 'Admin')).first()
            admins = session.query(UserRoleModel).filter(
                UserRoleModel.role_id == str(admin_role_id.id))
            for admin in admins:
                project_managers_and_admin.append(str(admin.user_id))

            project_manager_role_id = session.query(RoleModel).filter(
                (RoleModel.name == 'Project Manager')).first()
            project_managers = session.query(UserRoleModel).filter(
                UserRoleModel.role_id == str(project_manager_role_id.id))
            for pm in project_managers:
                manager_info = session.query(ProjectUserModel).filter(
                    ProjectUserModel.project_id == str(project_id),
                    ProjectUserModel.user_id == str(pm.user_id)).first()
                if manager_info:
                    project_managers_and_admin.append(str(manager_info.user_id))

            Notifications = []
            assessment_id = kwargs.get("assessment_id")
            assessment_info = (session.query(AssessmentModel).filter(
                AssessmentModel.id == assessment_id).first())
            description = "Assessment for {0} with name \
                    {1} has been reviewed".format(project_name.name, assessment_info.name)
            for project_manager_id in project_managers_and_admin:
                message = self.createNotificationPayload("assessment-reviewed",
                                                         description, project_manager_id)
                Notifications.append(message)

        response = self.storeNotifications(Notifications)
        return response
