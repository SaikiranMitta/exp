import json
import os
from datetime import datetime
from pathlib import Path
from sys import audit
from typing import Any

import boto3
from common.customExceptions import *
from common.decorator import decor
from dotenv import load_dotenv
from models.database.dbConnection import session
from models.roleModels import Role as RoleModel
from sqlalchemy import exc


class Role:
    # def __init__(self):
    #     self.client = boto3.client(
    #         "cognito-idp",
    #         aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID_KEYS"),
    #         aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY_KEYS"),
    #         region_name=os.getenv("REGION_NAME"),
    #     )

    def _getClient(self):
        client = boto3.client(
            "cognito-idp",
            # aws_access_key_id=os.getenv("AWS_ACCESS_KEY_ID_KEYS"),
            # aws_secret_access_key=os.getenv("AWS_SECRET_ACCESS_KEY_KEYS"),
            region_name=os.getenv("REGION_NAME"),
        )

        return client

    def _getRoleById(self, id):
        try:
            role = session.query(RoleModel).filter(RoleModel.id == id).first()
        except Exception:
            return None
        return role

    def _getRoleByName(self, name):
        try:
            role = session.query(RoleModel).filter(RoleModel.name == name).first()
        except Exception:
            return None
        finally:
            session.close()
        return role

    @decor
    def getRoleList(self, **kwargs):
        """
        Fetch List of Roles in the system.
        Input: None
        Output: List []

        """
        roles = session.query(RoleModel).all()
        rolesSerializedObject = [role.as_dict() for role in roles]
        return rolesSerializedObject

    def _checkGetRoleDetailsParameters(self, **kwargs):
        required_parameters = {
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getRoleDetails(self, **kwargs):
        """
        Fetch details of the requested Role
        Input:
        Output: {} containing details of the role

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("role_id"):
        #     raise URLAttributeNotFound("Role Id")

        self._checkGetRoleDetailsParameters(**kwargs)
        role_id = kwargs.get("role_id")
        role = self._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")
        return role.as_dict()

    def _checkCreateRoleParameters(self, **kwargs):
        required_parameters = {
            "name": "Name",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def createRole(self, **kwargs):
        """
        Add Role in the system.
        1. Creates role in AWS cognito
        2. Updates the role in database
        Input: dict { "name"}
        Output: dict { "name", "created_by", \
                 "created_on" ,  "modified_by" , " modified_on"} 
        """

        self._checkCreateRoleParameters(**kwargs)
        name = kwargs.get("name")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        name = name.strip()
        cognito_name = str(name).replace(" ", "_")
        client = self._getClient()

        # 1. Creates role in AWS cognito
        try:
            response = client.create_group(
                GroupName=cognito_name,
                UserPoolId=os.getenv("USER_POOL_ID"),
            )

        except client.exceptions.GroupExistsException as e:
            raise AlreadyExists("Role", "name")

        except Exception as e:
            raise AnyExceptionHandler(e)

        role = RoleModel()
        role.name = name
        role.created_by = authenticated_user_id

        # 2. Add the role in database
        try:
            session.add(role)
            session.commit()
            session.refresh(role)
        except Exception as ex:
            session.rollback()
            try:
                client = self._getClient()
                response = client.delete_group(
                    GroupName=cognito_name,
                    UserPoolId=os.getenv("USER_POOL_ID"),
                )
            except Exception as ex:
                raise AnyExceptionHandler(ex)
            raise AlreadyExists("Role", "name")
        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Role", "name")
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return role.as_dict()

    def _checkDeleteRoleParameters(self, **kwargs):

        required_parameters = {
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            print(kwargs.get(key))
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def deleteRole(self, **kwargs):
        """
        Delete the requested role
        Input:  {"role_id": 3443}
        Output: str -> success/ failure message

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("role_id"):
        #     raise URLAttributeNotFound("Role Id")
        self._checkDeleteRoleParameters(**kwargs)
        role_id = kwargs.get("role_id")
        role = self._getRoleById(role_id)

        if role is None:
            raise AttributeIdNotFound("Role")
        cognito_role_name = str(role.name).replace(" ", "_")
        try:
            client = self._getClient()
            response = client.delete_group(
                GroupName=cognito_role_name,
                UserPoolId=os.getenv("USER_POOL_ID"),
            )

        except client.exceptions.InvalidParameterException as e:
            raise InvalidAttribute("Role", "name")

        except Exception as e:
            raise AnyExceptionHandler(e)
        try:
            session.delete(role)
            session.commit()
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return "Role deleted Successfully"

    def _checkUpdateRoleParameters(self, **kwargs):

        required_parameters = {
            "id": "Id",
            "role_id": "Role Id",
            "name": "Name",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def updateRole(self, **kwargs):
        """
        Update the requested role details
        Input: {"role_id": 123, "id" : 123,"name": "Manager" }
        Output: {}  Updated role object / Error in case of failure

        """
        self._checkUpdateRoleParameters(**kwargs)
        id = kwargs.get("id")
        name = kwargs.get("name").strip()
        authenticated_user_id = kwargs.get("authenticated_user_id")
        role_id = kwargs.get("role_id")
        if not role_id == id:
            raise RequestBodyAndURLAttributeNotSame("Id")
        role = self._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")
        # name = role.name
        # cognito_name = str(name).replace(" ", "_")
        cognito_name = str(role.name).replace(" ", "_")
        if str(role.name) == name:
            role.modified_by = authenticated_user_id
            # role.modified_by = "d30847c7-3c46-4e08-8955-c11b97a63db1"
            role.modified_on = datetime.now()
            try:
                session.add(role)
                session.commit()
                session.refresh(role)
            except Exception as ex:
                session.rollback()
                raise AnyExceptionHandler(ex)
            finally:
                session.close()
            return role.as_dict()

        try:
            client = self._getClient()
            response = client.delete_group(
                GroupName=cognito_name,
                UserPoolId=os.getenv("USER_POOL_ID"),
            )

        except client.exceptions.InvalidParameterException as e:
            raise InvalidAttribute("Role", "name")

        except client.exceptions.ResourceNotFoundException as e:
            # raise AnyExceptionHandler(e)
            raise AttributeIdNotFound("Role")
        except Exception as e:
            raise AnyExceptionHandler(e)
        name = kwargs.get("name").strip()
        cognito_group_name = str(name).replace(" ", "_")
        print(cognito_group_name)

        try:
            client = self._getClient()
            response = client.create_group(
                GroupName=cognito_group_name,
                UserPoolId=os.getenv("USER_POOL_ID"),
            )

        except client.exceptions.GroupExistsException as e:
            raise AnyExceptionHandler(e)

        except Exception as e:
            raise AnyExceptionHandler(e)

        role.name = name
        # To Implement
        role.modified_by = authenticated_user_id
        # role.modified_by = "d30847c7-3c46-4e08-8955-c11b97a63db1"
        role.modified_on = datetime.now()
        try:
            session.add(role)
            session.commit()
            session.refresh(role)
        except Exception as ex:
            session.rollback()
            try:
                client = self._getClient()
                response = client.delete_group(
                    GroupName=cognito_group_name,
                    UserPoolId=os.getenv("USER_POOL_ID"),
                )
            except Exception as ex:
                raise AnyExceptionHandler(ex)
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return role.as_dict()

    def _getAllRoleById(self, id):
        try:
            role = (
                session.query(RoleModel)
                .filter(RoleModel.created_by == str(id))
                .all()
            )
        except Exception:
            return None
        finally:
            session.close()
        return role
