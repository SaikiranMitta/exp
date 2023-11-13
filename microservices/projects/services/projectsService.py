import json
import os
from datetime import datetime

import boto3
from sqlalchemy import desc, exc

from common.customExceptions import *
from common.decorator import *
from common.paginate import Paginate as Pagination
from microservices.accounts.services.accountsService import Account as AccountClass
from microservices.domains.services.domainsService import Domain as DomainClass
from microservices.roles.services.rolesService import Role as RoleClass
from microservices.users.services.usersService import User as UserClass
from models.accountModels import Account as AccountModel
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import AssessmentStatus
from models.database.dbConnection import session
from models.projectModels import Frequency
from models.projectModels import Project as ProjectModel
from models.projectUserModels import ProjectUser as ProjectUserModel
from models.userRoleModels import UserRole as UserRoleModel


class Project:
    def _validateDate(self, date_string):
        """
        Function to check if the date format is YYYY-MM-DD
        Input: date string whose format is to be checked
        Output: Boolean field (True -> if the date string is according to the specified format else False)
        """
        try:
            datetime.strptime(date_string, "%Y-%m-%d")
            return True
        except ValueError:
            return None

    # @decor
    def getProjectList(self, **kwargs):
        """
        Fetch List of Projects+Username in the system.
        Input: None
        Output: List [] containing details of projects in the system

        """

        projects = session.query(ProjectModel)
        project_filters = []

        if (
            "Project_Manager" in kwargs["authenticated_user_roles"]
            or "Product_Owner" in kwargs["authenticated_user_roles"]
            or "Engineer" in kwargs["authenticated_user_roles"]
        ):
            projects = projects.join(ProjectUserModel)
            project_filters.append(
                ProjectUserModel.user_id == kwargs["authenticated_user_id"]
            )

        if kwargs["domain_id"]:
            domain_id = kwargs["domain_id"]
            domain_object = DomainClass()
            domain = domain_object._getDomainById(domain_id)
            if domain is None:
                raise AttributeIdNotFound("Domain")

            projects = projects.join(AccountModel)
            project_filters.append(AccountModel.domain_id == domain_id)

        if kwargs["account_id"]:
            account_id = kwargs["account_id"]
            account_object = AccountClass()
            account = account_object._getAccountById(account_id)
            if account is None:
                raise AttributeIdNotFound("Account")

            project_filters.append(ProjectModel.account_id == account_id)

        if kwargs["active"]:
            active = kwargs["active"]
            if active not in ["true", "false"]:
                raise InvalidAttribute("Project", "status value")
            if active == "true":
                active = True
            else:
                active = False
            project_filters.append(ProjectModel.is_active == active)

        if kwargs["audit_frequency"]:
            audit_frequency = kwargs["audit_frequency"]
            if not Frequency.has_value(audit_frequency):
                raise InvalidAttribute("Project", "audit frequency value")
            project_filters.append(ProjectModel.audit_frequency == audit_frequency)

        if kwargs["search"]:
            search = kwargs["search"]
            projects = projects.filter(ProjectModel.name.ilike(f"%{search}%"))

        # paginate the final filtered response | start

        if project_filters:
            projects = projects.filter(*project_filters)

        total_data_count = projects.count()

        paginate = Pagination(
            ProjectModel,
            **{
                "page_size": kwargs["page_size"],
                "page_number": kwargs["page_number"],
                "sort_key": kwargs["sort_key"],
                "sort_order": kwargs["sort_order"],
                "total_data_count": total_data_count,
            },
        )

        pagination_setting = paginate._getPaginationSetting()
        pagination_response_attributes = paginate._getResponseAttribute()

        projects = (
            projects.order_by(pagination_setting["sort_key_order"])
            .limit(pagination_setting["page_size"])
            .offset(pagination_setting["offset_value"])
            .all()
        )

        # paginate the final filtered response | end

        ## Note : code block commented due to code restructed for project filter and pagination | start

        # if kwargs.get("queryStringParameters"):
        #     if kwargs.get("queryStringParameters").get("active"):
        #         active = kwargs.get("queryStringParameters").get("active")
        #         if active not in ["true", "false"]:
        #             raise InvalidAttribute("Project", "status value")
        #         if active == "true":
        #             active = True
        #         else:
        #             active = False
        #         projects = projects.filter(ProjectModel.is_active == active)
        #     if kwargs.get("queryStringParameters").get("account_id"):
        #         account_id = kwargs.get(
        #             "queryStringParameters").get("account_id")
        #         account_object = AccountClass()
        #         account = account_object._getAccountById(account_id)
        #         if account is None:
        #             raise AttributeIdNotFound("Account")

        #         projects = projects.filter(ProjectModel.account_id == account_id)

        # if (
        #     "Project_Manager" in kwargs["authenticated_user_roles"]
        #     or "Product_Owner" in kwargs["authenticated_user_roles"]
        # ):
        #     projects = self._getProjectsByUser(kwargs["authenticated_user_id"])
        # else:
        #     projects = projects.all()

        ## code block commented due to code restructed for project filter and pagination | end

        request_filters = []
        request_filters_without_date = []
        if kwargs["from_date"]:
            request_filters.append(
                AssessmentModel.start_date.between(
                    kwargs["from_date"], kwargs["to_date"]
                )
            )
            request_filters_without_date.append(
                AssessmentModel.start_date <= kwargs["from_date"]
            )

        if kwargs["min_overall_score"]:
            request_filters.append(
                AssessmentModel.overall_score.between(
                    kwargs["min_overall_score"], kwargs["max_overall_score"]
                )
            )
            request_filters_without_date.append(
                AssessmentModel.overall_score.between(
                    kwargs["min_overall_score"], kwargs["max_overall_score"]
                )
            )

        user_object = UserClass()
        role_object = RoleClass()

        results = []
        for project in projects:
            project_dict = project.as_dict()
            _users = []

            project_users = session.query(ProjectUserModel).filter(
                ProjectUserModel.project_id == f"{project.id}",
                ProjectUserModel.end_date == None,
            )

            for project_user in project_users:
                _roles = []
                user = user_object._getUserById(project_user.user_id)

                user_roles = session.query(UserRoleModel).filter(
                    UserRoleModel.user_id == f"{user.id}"
                )

                for user_role in user_roles:
                    role = role_object._getRoleById(user_role.role_id)
                    _roles.append({"id": str(role.id), "name": role.name})
                user_dict = {"id": str(user.id), "name": user.name}
                user_dict["roles"] = _roles
                _users.append(user_dict)
            project_assessment_filter = []
            project_assessment_without_date_filter = []
            project_assessment_filter.extend(request_filters)
            project_assessment_without_date_filter.extend(
                request_filters_without_date
            )

            project_assessment_filter.append(
                AssessmentModel.project_id == str(project.id)
            )
            project_assessment_without_date_filter.append(
                AssessmentModel.project_id == str(project.id)
            )

            # project_assessment_filter.append(AssessmentModel.status==AssessmentStatus.Reviewed)
            project_assessment = (
                session.query(
                    AssessmentModel.overall_score,
                    AssessmentModel.tech_debt,
                    AssessmentModel.id,
                    AssessmentModel.status,
                )
                .order_by(desc(AssessmentModel.start_date))
                .filter(*project_assessment_filter)
                .first()
            )
            project_dict["users"] = _users
            project_dict["overall_score"] = None
            project_dict["tech_debt"] = None
            project_dict["assessment_id"] = None
            project_dict["assessment_status"] = None
            project_dict["previous_overall_score"] = None
            project_dict["previous_tech_debt"] = None
            # print("project_assessment-", project_assessment, project_assessment)
            # Assesment for given date range
            if project_assessment:
                # Assessment for Project with Reviewed status
                project_dict["assessment_status"] = str(project_assessment.status)
                if project_assessment.status == AssessmentStatus.Reviewed:
                    project_dict["assessment_id"] = str(project_assessment.id)
                    project_dict["overall_score"] = project_assessment.overall_score
                    project_dict["tech_debt"] = project_assessment.tech_debt
                # Getting Previous Reviewed Assessment
                else:
                    project_assessment_without_date_filter.append(
                        AssessmentModel.status == AssessmentStatus.Reviewed
                    )
                    project_reviewed_assessment = (
                        session.query(
                            AssessmentModel.overall_score,
                            AssessmentModel.tech_debt,
                            AssessmentModel.id,
                            AssessmentModel.status,
                        )
                        .order_by(desc(AssessmentModel.start_date))
                        .filter(*project_assessment_without_date_filter)
                        .first()
                    )
                    # print("project_reviewed_assessment-", project_assessment)

                    if project_reviewed_assessment:
                        project_dict["assessment_id"] = str(
                            project_reviewed_assessment.id
                        )
                        # project_dict["overall_score"] = project_reviewed_assessment.overall_score
                        # project_dict["tech_debt"] = project_reviewed_assessment.tech_debt
                        project_dict[
                            "previous_overall_score"
                        ] = project_reviewed_assessment.overall_score
                        project_dict[
                            "previous_tech_debt"
                        ] = project_reviewed_assessment.tech_debt
            # Latest reviewed Assesment before given date range
            else:
                project_assessment_without_date_filter.append(
                    AssessmentModel.status == AssessmentStatus.Reviewed
                )
                project_reviewed_assessment = (
                    session.query(
                        AssessmentModel.overall_score,
                        AssessmentModel.tech_debt,
                        AssessmentModel.id,
                        AssessmentModel.status,
                    )
                    .order_by(desc(AssessmentModel.start_date))
                    .filter(*project_assessment_without_date_filter)
                    .first()
                )
                # print("project_reviewed_assessment when no assessment for filter date range-", project_assessment)

                if project_reviewed_assessment:
                    project_dict["assessment_id"] = str(
                        project_reviewed_assessment.id
                    )
                    project_dict[
                        "previous_overall_score"
                    ] = project_reviewed_assessment.overall_score
                    project_dict[
                        "previous_tech_debt"
                    ] = project_reviewed_assessment.tech_debt
            results.append(project_dict)
            # break

        return results, pagination_response_attributes

    def _getProjectById(self, id):
        """
        Check if the given Id belong to a project in the system
        Input: Id of the project that needs to be checked
        Output: Project object if a project exists with the given project Id else None

        """
        try:
            project = (
                session.query(ProjectModel).filter(ProjectModel.id == id).first()
            )
        except Exception:
            return None
        finally:
            session.close()

        return project

    def _checkGetProjectDetailsParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getProjectDetails(self, **kwargs):
        """
        Fetch details of the requested Project
        Input: {"project_id":123}
        Output: {} containing details of the project

        """
        self._checkGetProjectDetailsParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project = self._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")

        user_object = UserClass()
        role_object = RoleClass()

        results = []
        project_dict = project.as_dict()
        _users = []

        project_users = session.query(ProjectUserModel).filter(
            ProjectUserModel.project_id == f"{project.id}",
            ProjectUserModel.end_date == None,
        )

        for project_user in project_users:
            _roles = []
            user = user_object._getUserById(project_user.user_id)

            user_roles = session.query(UserRoleModel).filter(
                UserRoleModel.user_id == f"{user.id}"
            )

            for user_role in user_roles:
                role = role_object._getRoleById(user_role.role_id)
                _roles.append({"id": str(role.id), "name": role.name})
            user_dict = {"id": str(user.id), "name": user.name}
            user_dict["roles"] = _roles
            _users.append(user_dict)

        project_dict["users"] = _users
        results.append(project_dict)

        # return project.as_dict()
        return results

    def _checkCreateProjectParameters(self, **kwargs):

        required_parameters = {
            "name": "Name",
            "details": "Details",
            "trello_link": "Trello Link",
            "start_date": "Start date",
            "audit_frequency": "Audit Frequency",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    def _checkUpdateProjectParameters(self, **kwargs):

        required_parameters = {
            "id": "Id",
            "name": "Name",
            "details": "Details",
            "trello_link": "Trello Link",
            "start_date": "Start date",
            "audit_frequency": "Audit Frequency",
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
            # "authenticated_user_id": "Authenticated User Id",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:

                raise AttributeNotPresent(value)

    @decor
    def createProject(self, **kwargs):
        """

        Add Project in the system.
        Input: dict { "name", "account_id","trello_link" , \
            "start_date","audit_frequency", "details"}

        Output: dict { "name", "account_id","trello_link" ,\
             "start_date","audit_frequency", "created_by", \
                 "created_on" ,  "modified_by" , " modified_on"} 

        """
        self._checkCreateProjectParameters(**kwargs)
        name = kwargs.get("name")
        details = kwargs.get("details")
        account_id = kwargs.get("account_id")

        account_object = AccountClass()
        account = account_object._getAccountById(account_id)
        if account is None:
            raise AttributeIdNotFound("Account")

        trello_link = kwargs.get("trello_link")

        start_date = kwargs.get("start_date")
        date = self._validateDate(start_date)
        if date is None:
            raise IncorrectFormat("Start Date")

        audit_frequency = kwargs.get("audit_frequency")
        if not Frequency.has_value(audit_frequency):
            raise InvalidAttribute("Project", "Audit Frequency value")

        authenticated_user_id = kwargs.get("authenticated_user_id")

        project = ProjectModel()
        project.name = name
        project.trello_link = trello_link
        project.details = details
        project.start_date = start_date
        project.audit_frequency = audit_frequency
        project.account_id = account_id
        project.created_by = authenticated_user_id

        try:
            session.add(project)
            session.commit()
            session.refresh(project)
            print("session Commited")
            kwargs["project_id"] = str(project.id)
            client = boto3.client("sns")
            published_message = client.publish(
                TargetArn=os.getenv("SNS_PROJECT_CREATED_ARN"),
                Message=json.dumps(kwargs),
            )
            print("SNS triggered")

        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Project", "name")
        except Exception as ex:
            print("Error creating project", ex)
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return project.as_dict()

    @decor
    def deleteProject(self, **kwargs):
        """
        Delete the requested Project
        Input: {"project_id" : 123 }
        Output: str -> success/ failure message

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("project_id"):
        #     raise URLAttributeNotFound("Project Id")

        project_id = kwargs.get("project_id")
        project = self._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")
        if not project.is_active:
            raise AnyExceptionHandler("Cannot delete an inactive project")

        project.is_active = False
        try:
            session.commit()
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return "Project deleted successfully"

    @decor
    def updateProject(self, **kwargs):
        """
        Update the requested Project details
        Input: {"project_id":123,, "id":345 ,"name": "ZS Project" , "trello_link": "www.trello.com/v1" , "start_date": "2022-12-18",\
             "audit_frequency": "Monthly", "account_id" : 43443}
        Output: {}  Updated Project object / Error message in case of failure

        """
        self._checkUpdateProjectParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project = self._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")
        if not project.is_active:
            raise AnyExceptionHandler("Cannot make changes to an inactive project!")
        id = kwargs.get("id")
        if not str(id) == str(project_id):
            raise RequestBodyAndURLAttributeNotSame("Project Id")
        name = kwargs.get("name")
        account_id = kwargs.get("account_id")
        trello_link = kwargs.get("trello_link")
        start_date = kwargs.get("start_date")
        details = kwargs.get("details")
        audit_frequency = kwargs.get("audit_frequency")
        if not Frequency.has_value(audit_frequency):
            raise InvalidAttribute("Project", "audit frequency value")
        account_id = kwargs.get("account_id")
        account_object = AccountClass()
        account = account_object._getAccountById(account_id)
        if account is None:
            raise AttributeIdNotFound("Account")
        if not str(account_id) == str(project.account_id):
            raise InvalidAttribute("Project", "Account Id")
        start_date = kwargs.get("start_date")
        date = self._validateDate(start_date)
        if date is None:
            raise IncorrectFormat("Start date")
        authenticated_user_id = kwargs.get("authenticated_user_id")

        project.name = name
        project.audit_frequency = audit_frequency
        project.trello_link = trello_link
        project.start_date = start_date
        project.details = details
        # To Implement
        project.modified_by = authenticated_user_id
        # project.modified_by = "d30847c7-3c46-4e08-8955-c11b97a63db1"
        project.modified_on = datetime.now()
        try:
            session.add(project)
            session.commit()
            session.refresh(project)
        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Project", "name")

        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)

        return project.as_dict()

    def _getProjectsByUser(self, user_id):
        """
        Fetch List of Projects mapped to user
        Input: authenticated_user_id
        Output: List []

        """
        projects = (
            session.query(ProjectModel)
            .join(ProjectUserModel)
            .filter(ProjectUserModel.user_id == user_id)
            .all()
        )
        return projects
