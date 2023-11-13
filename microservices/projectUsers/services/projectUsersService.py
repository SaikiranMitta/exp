from datetime import datetime
import os
import json
import boto3
from common.boto3Client import Boto3Client
from common.customExceptions import *
from common.decorator import decor
from microservices.projects.services.projectsService import Project as ProjectClass
from microservices.roles.services.rolesService import Role as RoleClass
from microservices.users.services.usersService import User as UserClass
from models.database.dbConnection import session
from models.projectUserModels import ProjectUser as ProjectUserModel
from sqlalchemy import exc


class ProjectUser:
    def _checkAddProjectUserParameters(self, **kwargs):

        required_parameters = {
            "user_id": "User Id",
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def addProjectUser(self, **kwargs):

        """

        Add user to Project in the system.
        Input: dict {"project_id:123} ,{"id":563}

        Output: str -> success/ failure message

        """
        print("addProjectUser kwargs:: ", kwargs)
        self._checkAddProjectUserParameters(**kwargs)
        project_id = kwargs.get("project_id")
        user_id = kwargs.get("user_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if not project:
            raise AttributeIdNotFound("Project Id")
        if not project.is_active:
            raise AnyExceptionHandler("Cannot add user to an inactive project!")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if not user:
            raise AttributeIdNotFound("User Id")
        # if user.status.name == "Inactive":
        #     raise AnyExceptionHandler(
        #         "Inactive user cannot be added to the given Project!"
        #     )
        # if user.status.name == "Unverified":
        #     raise AnyExceptionHandler(
        #         "Unverified user cannot be added to the given Project!"
        #     )
        ongoing_projects = session.query(ProjectUserModel).filter(
            ProjectUserModel.project_id==str(project_id),
            ProjectUserModel.user_id==str(user_id),
            ProjectUserModel.created_on <= datetime.now(),
            ProjectUserModel.end_date == None,
            ).all()

        if ongoing_projects:
            raise AlreadyExists("Project", "user")

        project_user_model = ProjectUserModel()
        project_user_model.user_id = user_id
        project_user_model.project_id = project_id
        project_user_model.created_by = authenticated_user_id

        # To Be changed

        try:
            session.add(project_user_model)
            session.commit()
            client = boto3.client("sns")
            published_message = client.publish(
                TargetArn=os.getenv("SNS_PROJECT_USER_CREATED_ARN"),
                Message=json.dumps(project_user_model.as_dict())
            )
            print("addProjectUser published_message", published_message)

        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Project", "user")

        except Exception as ex:
            raise AnyExceptionHandler(ex)

        finally:
            session.close()

        return "User added to the given project successfully"

    def _checkGetProjectUserListParameters(self, **kwargs):

        required_parameters = {
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getProjectUserList(self, **kwargs):

        """
        Fetch List of Project Users in the system.
        Input: Dict { {"project_id"}}
        Output: List []

        """

        self._checkGetProjectUserListParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if not project:
            raise AttributeIdNotFound("Project")

        project_users = (
            session.query(ProjectUserModel)
            .filter(ProjectUserModel.project_id == project_id)
            .all()
        )

        responses = []
        user_object = UserClass()
        boto3_client = Boto3Client()
        for project_user in project_users:
            user = user_object._getUserById(project_user.user_id)
            if user is None:
                raise AttributeIdNotFound("User")
            username = user.username
            try:
                client = boto3_client.getBoto3Client()
                response = client.admin_list_groups_for_user(
                    Username=username,
                    UserPoolId=os.getenv("USER_POOL_ID"),
                )
            except Exception as e:
                raise AnyExceptionHandler(e)
            roles = response.get("Groups")
            results = []
            role_info = {}
            responses = []
            response_dict = {}
            role_object = RoleClass()
            for role in roles:
                name = role.get("GroupName")
                name = str(name)
                name = (name).replace("_", " ")

                if name is not None:
                    role = role_object._getRoleByName(name)
                    if role is None:
                        raise InvalidAttribute("Role", "name")
                    results.append(role.as_dict())

            project_user_info = project_user.as_dict()
            project_user_info["role_info"] = results
            responses.append(project_user_info)

        return responses

    def _checkGetProjectuserDetailsParameters(self, **kwargs):
        required_parameters = {
            "user_id": "User Id",
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getProjectUserDetails(self, **kwargs):
        """
        Fetch Project User details .
        Input: Dict { "project_id", "user_id"}
        Output: List []
        """

        self._checkGetProjectuserDetailsParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if not project:
            raise AttributeIdNotFound("Project")
        user_id = kwargs.get("user_id")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if not user:
            raise AttributeIdNotFound("User")
        project_user = (
            session.query(ProjectUserModel)
            .filter(
                ProjectUserModel.project_id == project_id,
                ProjectUserModel.user_id == user_id,
            )
            .first()
        )

        if project_user is None:
            raise AnyExceptionHandler("User is not a part of the given project!")
        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()
            response = client.admin_list_groups_for_user(
                Username=user.username,
                UserPoolId=os.getenv("USER_POOL_ID"),
            )

        except Exception as e:
            raise AnyExceptionHandler(e)

        roles = response.get("Groups")
        results = []
        role_info = {}
        response_dict = {}
        for role in roles:
            name = role.get("GroupName")
            name = str(name)
            name = (name).replace("_", " ")
            print(name)
            if name is not None:
                role_object = RoleClass()

                role = role_object._getRoleByName(name)

                if role is None:
                    raise InvalidAttribute("Role", "name")

                results.append(role.as_dict())

        project_user_info = project_user.as_dict()
        project_user_info["role_info"] = results

        return project_user_info

    def _checkDeleteProjectUserParameters(self, **kwargs):

        required_parameters = {
            "user_id": "User Id",
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def deleteProjectUser(self, **kwargs):

        """
        Mark the end date of the requested user for the Project
        Input: {"project_id", "user_id"}
        Output: str -> success/ failure message

        """

        self._checkDeleteProjectUserParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()

        project = project_object._getProjectById(project_id)
        if not project:
            raise AttributeIdNotFound("Project")
        if not project.is_active:
            raise AnyExceptionHandler("Cannot delete user from an inactive project!")
        user_id = kwargs.get("user_id")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        # if user.status.name == "Inactive":
        #     raise AnyExceptionHandler(
        #         "Inactive user cannot be deleted from the given Project!"
        #     )
        # if user.status.name == "Unverified":
        #     raise AnyExceptionHandler(
        #         "Unverified user cannot be deleted from the given Project!"
        #     )

        project_user = (
            session.query(ProjectUserModel)
            .filter(
                ProjectUserModel.project_id == project_id,
                ProjectUserModel.user_id == user_id,
            )
            .first()
        )
        if project_user is None:
            raise AnyExceptionHandler("User is not a part of the given project!")
        
        project_user.end_date = datetime.now()
        
        try:

            session.add(project_user)
            session.commit()
        except Exception as e:
            session.rollback()
            raise AnyExceptionHandler(e)
        finally:
            session.close()

        return "User's project allocation end_date updated successfully"
