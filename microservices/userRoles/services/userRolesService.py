import json
import os
from http import client
from typing import Any

import boto3
from common.boto3Client import Boto3Client
from common.customExceptions import *
from common.decorator import decor
from microservices.roles.services.rolesService import Role as RoleClass
from microservices.users.services.usersService import User as UserClass
from models.database.dbConnection import session
from models.roleModels import Role as RoleModel
from models.userRoleModels import UserRole as UserRoleModel
from sqlalchemy import exc


class UserRole:
    def _checkGetUserRoleListParameters(self, **kwargs):
        required_parameters = {
            "user_id": "User Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):

                raise AttributeNotPresent(value)

    @decor
    def getUserRoleList(self, **kwargs):

        """
        Fetch List of  User Roles in the system.
        Input: { "pathParameters" {"user_id"}}
        Output: List []

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("user_id"):
        #     raise URLAttributeNotFound("User Id")
        self._checkGetUserRoleListParameters(**kwargs)
        user_id = kwargs.get("user_id")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")

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
            role_name = name.replace("_", " ")

            if role_name is None:
                return {"body": {"results": []}}
            role_object = RoleClass()
            role = role_object._getRoleByName(role_name)
            if role is None:

                raise InvalidAttribute("Role", "name")
                # Role exists in cognito groups but not in local db

            results.append(role.as_dict())
        return results

    def _checkAddUserRoleParameters(self, **kwargs):

        required_parameters = {
            "user_id": "User Id",
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def addUserRole(self, **kwargs):
        """
        Add user to Project in the system.
        Input: dict {"project_id:123} ,{"id":563}

        Output: str -> success/ failure message

        """

        self._checkAddUserRoleParameters(**kwargs)
        role_id = kwargs.get("role_id")
        user_id = kwargs.get("user_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        authenticated_user_roles = kwargs.get("authenticated_user_roles")

        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if not role:
            raise AttributeIdNotFound("Role Id")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if not user:
            raise AttributeIdNotFound("User Id")

        user_role_model = UserRoleModel()
        user_role_model.user_id = user_id
        user_role_model.role_id = role_id
        user_role_model.created_by = authenticated_user_id

        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()
            rolename = role.name.replace(" ", "_")
            response = client.admin_add_user_to_group(
                UserPoolId=os.getenv("USER_POOL_ID"),
                Username=user.username,
                GroupName=rolename,
            )
        except Exception as e:
            raise AnyExceptionHandler(e)

        try:
            session.add(user_role_model)
            session.commit()
        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("user", "role")
        except Exception as ex:
            raise AnyExceptionHandler(ex)

        return "Role successfully assigned to User"

    def _checkDeleteUserRoleParameters(self, **kwargs):

        required_parameters = {
            "user_id": "User Id",
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def deleteUserRole(self, **kwargs):

        """
        Remove the requested role from the given user
        Input: {"pathParameters": {"user_id", "role_id"}}
        Output: str -> success/ failure message

        """

        print(kwargs)
        self._checkDeleteUserRoleParameters(**kwargs)
        role_id = kwargs.get("role_id")
        user_id = kwargs.get("user_id")
        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        if user.status.name == "Unverified":
            raise AnyExceptionHandler("Cannot remove role from an unverified user")
        if user.status.name == "Inactive":
            raise AnyExceptionHandler("Cannot remove role from an Inactive user")

        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()
            rolename = role.name.replace(" ", "_")
            response = client.admin_remove_user_from_group(
                UserPoolId=os.getenv("USER_POOL_ID"),
                Username=user.username,
                GroupName=rolename,
            )
        except Exception as e:
            raise AnyExceptionHandler(e)

        return "Role successfully removed from the given user"

    @decor
    def getUserRoleListForDeleteAndCreate(self, **kwargs):

        """
        Remove the requested role from the given user
        Input: {"pathParameters": {"user_id", "role_id"}}
        Output: str -> success/ failure message

        """
        self._checkGetUserRoleListParameters(**kwargs)
        user_id = kwargs.get("user_id")
        names = kwargs.get("names")
        role_object = RoleClass()
        user_object = UserClass()
        user = user_object._getUserById(user_id)
        if user is None:
            raise AttributeIdNotFound("User")
        if user.status.name == "Unverified":
            raise AnyExceptionHandler("Cannot remove role from an unverified user")
        if user.status.name == "Inactive":
            raise AnyExceptionHandler("Cannot remove role from an Inactive user")

        try:
            boto3_client = Boto3Client()
            client = boto3_client.getBoto3Client()
            rolename = role.name.replace(" ", "_")
            response = client.admin_remove_user_from_group(
                UserPoolId=os.getenv("USER_POOL_ID"),
                Username=user.username,
                GroupName=rolename,
            )
        except Exception as e:
            raise AnyExceptionHandler(e)

        try:
            roles = (
                session.query(RoleModel)
                .filter(RoleModel.created_by == user_id)
                .all()
            )
            if roles is None:
                raise AttributeIdNotFound("Role")
            for role in roles:
                session.delete(role)
                session.commit()
            for name in names:
                kwargs["name"] = name
                role = role_object.createRole(**kwargs)

        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        except Exception as error:
            return None
        finally:
            session.close()
        return "Role successfully removed from the given user and Created new role"
