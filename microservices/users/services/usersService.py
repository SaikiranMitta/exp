import io
import os
import random
from datetime import datetime

from openpyxl import Workbook
from openpyxl.writer.excel import save_virtual_workbook
from sqlalchemy import String, cast, exc, or_
from urllib3 import HTTPResponse

from common.boto3Client import Boto3Client
from common.customExceptions import *
from common.decorator import decor
from common.paginate import Paginate as Pagination
from microservices.roles.services.rolesService import Role as RoleClass
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import AssessmentStatus
from models.database.dbConnection import session
from models.projectModels import Project as ProjectModel
from models.projectUserModels import ProjectUser as ProjectUserModel
from models.userModels import User as UserModel
from models.userModels import UserStatus
from models.userRoleModels import UserRole as UserRoleModel


def get_random_pass(pass_length=16):
    return "".join(
        [
            random.choice(
                random.choice(
                    [
                        ["a", "e", "f", "g", "h", "m", "n", "t", "y"],
                        [
                            "A",
                            "B",
                            "E",
                            "F",
                            "G",
                            "H",
                            "J",
                            "K",
                            "L",
                            "M",
                            "N",
                            "Q",
                            "R",
                            "T",
                            "X",
                            "Y",
                        ],
                        ["2", "3", "4", "5", "6", "7", "8", "9"],
                        ["/", "*", "+", "~", "@", "#", "%", "^", "&", "//"],
                    ]
                )
            )
            for i in range(pass_length)
        ]
    )


class User:
    def _getAllUserRoleById(self, user_id):
        user_role = []
        try:
            user_role = session.query(UserRoleModel).filter(
                UserRoleModel.user_id == f"{user_id}"
            )
        except Exception as err:
            session.rollback()
        return user_role

    # @decor
    def getUserList(self, **kwargs):
        """
        Fetch List of Users in the system.
        Input: None
        Output: List []
        """

        response = []

        if kwargs["role"]:
            role = kwargs["role"]
            print("role - ", type(role))
            try:
                boto3_client = Boto3Client()
                client = boto3_client.getBoto3Client()
                response = client.list_users_in_group(
                    UserPoolId=os.getenv("USER_POOL_ID"),
                    GroupName=role,
                )
            except client.exceptions.ResourceNotFoundException as ex:
                raise InvalidAttribute("Role", "name")
            except Exception as ex:
                raise AnyExceptionHandler(ex)
            users_list_id = []
            if response:
                users_list = response.get("Users")
                users_list_id = []
                for user in users_list:
                    users_list_id.append(user.get("Username"))
                # users = users.filter(UserModel.id.in_(users_list_id))
                users = session.query(UserModel).filter(
                    UserModel.id.in_(users_list_id)
                )

        else:
            users = session.query(UserModel)

        if kwargs["status"]:
            user_status = str(kwargs["status"])
            if not UserStatus.has_value(user_status):
                raise InvalidAttribute("User", "status value")
            users = users.filter(UserModel.status == user_status)

        if kwargs["search"]:
            search = kwargs["search"]
            if search.isnumeric():
                users = users.filter(
                    cast(UserModel.ps_no, String).contains(f"%{search}%")
                )
            else:
                users = users.filter(UserModel.name.ilike(f"%{search}%"))

        # Pagination logic | Start
        total_data_count = users.count()

        paginate = Pagination(
            UserModel,
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
        users = (
            users.order_by(pagination_setting["sort_key_order"])
            .limit(pagination_setting["page_size"])
            .offset(pagination_setting["offset_value"])
        )
        # Pagination logic | End

        results = []
        for user in users:

            user_dict = user.as_dict()
            role_object = RoleClass()

            # roles = role_object._getAllRoleById(user.id)
            user_roles = self._getAllUserRoleById(user.id)
            roles_lst = []

            for user_role in user_roles:
                role = role_object._getRoleById(user_role.role_id)
                role_dict = {"id": str(role.id), "name": role.name}
                roles_lst.append(role_dict)
            user_dict["roles"] = roles_lst
            results.append(user_dict)

            # Fetch user's last Audited Project with the assessment details and Current Projects
            current_project = (
                session.query(ProjectModel, ProjectUserModel)
                .join(ProjectUserModel)
                .filter(
                    ProjectUserModel.user_id == str(user.id),
                    ProjectUserModel.end_date == None,
                )
                .first()
            )
            user_dict["current_project"] = (
                current_project[0].as_dict() if current_project else dict()
            )
            user_dict["current_project_user"] = (
                current_project[1].as_dict() if current_project else dict()
            )

            projects = (
                session.query(ProjectModel, ProjectUserModel)
                .join(ProjectUserModel)
                .filter(
                    ProjectUserModel.user_id == str(user.id),
                    ProjectUserModel.end_date != None,
                )
                .all()
            )

            project_ids = [str(project[0].id) for project in projects]
            user_dict["last_audited_project"] = {}

            assessments = (
                session.query(AssessmentModel)
                .filter(AssessmentModel.project_id.in_(project_ids))
                .order_by(AssessmentModel.end_date.desc())
            )

            if kwargs["download"]:
                # Append all the past projects of the users along with the latest project assessment details
                user_dict["project_project_user_tuple_list"] = list()
                for project in projects:
                    last_audited_project = None
                    for assessment in assessments:
                        if (
                            assessment.end_date
                            and assessment.status == AssessmentStatus.Reviewed
                        ):
                            last_audited_project = (
                                session.query(ProjectModel)
                                .filter(ProjectModel.id == assessment.project_id)
                                .first()
                            )
                            last_audited_project = last_audited_project.as_dict()
                            last_audited_project["assessment"] = assessment.as_dict()
                            user_dict["last_audited_project"] = last_audited_project
                            break
                    user_dict["project_project_user_tuple_list"].append(project)

            else:
                for assessment in assessments:
                    if (
                        assessment.end_date
                        and assessment.status == AssessmentStatus.Reviewed
                    ):
                        last_audited_project = (
                            session.query(ProjectModel)
                            .filter(ProjectModel.id == assessment.project_id)
                            .first()
                        )
                        last_audited_project = last_audited_project.as_dict()
                        last_audited_project["assessment"] = assessment.as_dict()
                        user_dict["last_audited_project"] = last_audited_project

                        break

        return results, pagination_response_attributes

    def _checkCreateUserParameters(self, **kwargs):
        required_parameters = {
            "username": "Username",
            "name": "Name",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def createUser(self, **kwargs):

        """
        Add user to the system.
        Input: dict { "name" , "username"}
        Output: {} containing added user object details/ failure message

        """
        self._checkCreateUserParameters(**kwargs)
        username = email = kwargs.get("username")
        name = kwargs.get("name")
        ps_no = kwargs.get("ps_no")
        authenticated_user_id = kwargs.get("authenticated_user_id")

        ps_no_exists = session.query(UserModel).filter(UserModel.ps_no == ps_no)
        ps_no_exists = session.execute(ps_no_exists).first()

        if ps_no_exists is not None:
            raise AlreadyExists("User", "ps_no")

        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()

            temp_pass = get_random_pass()
            response = client.admin_create_user(
                UserPoolId=os.getenv("USER_POOL_ID"),
                Username=email,
                UserAttributes=[
                    {"Name": "name", "Value": name},
                ],
                TemporaryPassword=temp_pass,
                ForceAliasCreation=True,
                DesiredDeliveryMediums=["EMAIL"],
            )

        except client.exceptions.UsernameExistsException as e:
            user = self._getUserByUsername(username)
            if user is None:
                raise InvalidAttribute("User's", "username/email")
                # Username exists in cognito but not in local database

            # if user.status.name == "Unverified":
            raise AlreadyExists("User", "username")

        except client.exceptions.InvalidPasswordException as e:
            raise IncorrectFormat("Password")

        except client.exceptions.UserLambdaValidationException as e:
            raise AlreadyExists("User's", "Username/email")

        except Exception as e:
            raise AnyExceptionHandler(e)

        sub = None

        sub = response.get("User").get("Username")
        user = UserModel()
        user.id = sub
        user.name = name
        user.username = username
        user.status = "Unverified"
        user.created_by = authenticated_user_id
        user.ps_no = ps_no
        # To Be changed
        try:
            session.add(user)
            session.commit()
            session.refresh(user)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()

        return user.as_dict()

    def _getUserById(self, id):
        try:

            user = session.query(UserModel).filter(UserModel.id == id).first()
        except Exception:
            session.rollback()
            return None
        return user

    def _getUserByUsername(self, username):

        user = (
            session.query(UserModel).filter(UserModel.username == username).first()
        )
        return user

    def _getUserByUsername(self, username):

        user = (
            session.query(UserModel).filter(UserModel.username == username).first()
        )
        return user

    def _checkGetUserDetailsParameters(self, **kwargs):

        required_parameters = {
            "user_id": "User Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def _checkListUserParameters(self, **kwargs):
        required_parameters = {
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getUserDetails(self, **kwargs):

        """
        Fetch details of the requested user.
        Input: dict {"user_id": "2344" }
        Output: {} containing added user object details/ failure message

        """
        self._checkListUserParameters(**kwargs)
        user_id = kwargs.get("user_id")
        user = self._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User Id")
        else:
            user_dict = user.as_dict()
            current_project = (
                session.query(ProjectModel, ProjectUserModel)
                .join(ProjectUserModel)
                .filter(
                    ProjectUserModel.user_id == str(user.id),
                    ProjectUserModel.end_date == None,
                )
                .first()
            )
            user_dict["current_project"] = (
                current_project[0].as_dict() if current_project else dict()
            )

            projects = (
                session.query(ProjectModel, ProjectUserModel)
                .join(ProjectUserModel)
                .filter(
                    ProjectUserModel.user_id == str(user.id),
                    ProjectUserModel.end_date != None,
                )
                .all()
            )

            project_ids = [str(project[0].id) for project in projects]
            user_dict["last_audited_project"] = {}

            assessments = (
                session.query(AssessmentModel)
                .filter(AssessmentModel.project_id.in_(project_ids))
                .order_by(AssessmentModel.end_date.desc())
            )

            for assessment in assessments:
                if (
                    assessment.end_date
                    and assessment.status == AssessmentStatus.Reviewed
                ):
                    last_audited_project = (
                        session.query(ProjectModel)
                        .filter(ProjectModel.id == assessment.project_id)
                        .first()
                    )
                    last_audited_project = last_audited_project.as_dict()
                    last_audited_project["assessment"] = assessment.as_dict()
                    user_dict["last_audited_project"] = last_audited_project

        return user_dict

    def _checkUpdateUserDetails(self, **kwargs):
        required_parameters = {
            "user_id": "User Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def verifiedUser(self, user_id):
        status = "Verified"
        user = self._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        user.status = status
        try:
            session.add(user)
            session.commit()
            session.refresh(user)
        except Exception as ex:
            session.rollback()
        return user

    @decor
    def updateUserDetails(self, **kwargs):

        """
        Update the requested User details
        Input: dict {"user_id": "2344"}, "name" :"Hemangi K"}
        Output: {} containing added user object details/ failure message

        """
        self._checkUpdateUserDetails(**kwargs)
        user_id = kwargs.get("user_id")
        name = kwargs.get("name")
        status = kwargs.get("status")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        ps_no = kwargs.get("ps_no")
        user = self._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        authenticated_user = self._getUserById(authenticated_user_id)
        if authenticated_user is None:
            raise AttributeIdNotFound("Authenticated User Id")
        previous_name = user.name

        ps_no_exists = session.query(UserModel).filter(
            (UserModel.username != user.username) and (UserModel.ps_no == ps_no)
        )
        ps_no_exists = session.execute(ps_no_exists).first()

        if ps_no_exists is not None:
            raise AlreadyExists("User", "ps_no")
        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()
            response = client.admin_update_user_attributes(
                UserPoolId=os.getenv("USER_POOL_ID"),
                Username=user.username,
                UserAttributes=[
                    {"Name": "name", "Value": name},
                ],
            )

        except Exception as e:
            raise AnyExceptionHandler(e)
        if not status:
            if user.status.name == "Inactive":
                raise AnyExceptionHandler("Cannot make changes to an inactive user!")
            if user.status.name == "Unverified":
                raise AnyExceptionHandler(
                    "Cannot make changes to an unverified user!"
                )
        else:
            user.status = status
        user.name = name
        user.ps_no = ps_no
        user.modified_by = authenticated_user_id
        user.modified_on = datetime.now()

        # user.modified_by = "d30847c7-3c46-4e08-8955-c11b97a63db1"
        # To be changed
        try:
            session.add(user)
            session.commit()
            session.refresh(user)
        except Exception as ex:
            session.rollback()
            try:
                boto3_client = Boto3Client()
                client = boto3_client.getBoto3Client()
                response = client.admin_update_user_attributes(
                    UserPoolId=os.getenv("USER_POOL_ID"),
                    Username=user.username,
                    UserAttributes=[
                        {"Name": "name", "Value": previous_name},
                    ],
                )
            except Exception as e:
                raise AnyExceptionHandler(e)
        return user.as_dict()

    def _checkDeleteUserParameters(self, **kwargs):
        required_parameters = {
            "user_id": "User Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def deleteUser(self, **kwargs):
        """
        Delete  the requested user.
        Input: dict {"user_id": "2344" }
        Output: {} containing added user object details/ failure message

        """
        # if not kwargs.get("pathParameters").get("user_id"):
        #     raise URLAttributeNotFound("User Id")
        self._checkDeleteUserParameters(**kwargs)
        user_id = kwargs.get("user_id")
        user = self._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        if user.status.name == "Inactive":
            raise AnyExceptionHandler("Cannot delete an inactive user!")

        user.status = "Inactive"
        try:
            session.add(user)
            session.commit()

        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        return "User successfully deleted"

    @decor
    def getUserProjectList(self, **kwargs):

        """
        Fetch List of Project Users in the system.
        Input: Dict { {"project_id"}}
        Output: List []
        """

        user_id = kwargs.get("user_id")
        user = self._getUserById(user_id)

        print("user_id", user_id)
        if not user:
            raise AttributeIdNotFound("User")
        user_project_ids = (
            session.query(ProjectUserModel.project_id)
            .filter(ProjectUserModel.user_id == user_id)
            .all()
        )
        print("user_project_ids", user_project_ids)
        project_id_list = []
        if user_project_ids:
            project_id_list = [id[0] for id in user_project_ids]

        user_projects = session.query(ProjectModel).filter(
            ProjectModel.id.in_(project_id_list)
        )

        user_projects = user_projects.all()
        project_ids = [str(project.id) for project in user_projects]

        assessments = (
            session.query(AssessmentModel)
            .filter(AssessmentModel.project_id.in_(project_ids))
            .order_by(AssessmentModel.end_date.desc())
        )
        print("assessments", assessments)
        results = []
        for project in user_projects:
            project_dict = project.as_dict()
            project_dict["assessment"] = list()
            print("Inside 1st For")
            for assessment in assessments:
                print("Inside 2nd For")
                project_users = (
                    session.query(ProjectUserModel)
                    .filter(
                        ProjectUserModel.project_id == str(project.id),
                        ProjectUserModel.created_on <= assessment.end_date,
                        or_(
                            ProjectUserModel.end_date >= assessment.end_date,
                            ProjectUserModel.end_date == None,
                        ),
                    )
                    .first()
                )
                print("Checnking condition")
                if (
                    assessment.end_date
                    and assessment.status == AssessmentStatus.Reviewed
                    and project_users
                    and assessment.project_id == str(project.id)
                ):
                    project_dict["assessment"].append(assessment.as_dict())
                    print("inside If")
            results.append(project_dict)
            print("results")
        return results
