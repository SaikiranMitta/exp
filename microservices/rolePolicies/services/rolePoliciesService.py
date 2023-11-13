import json
from datetime import datetime
from email import policy
from typing import Any

from common.customExceptions import *
from common.customExceptions import (  # PathParameterNotFound,; URLAttributeNotFound,
    AlreadyExists,
    AnyExceptionHandler,
    AttributeIdNotFound,
    InvalidAttribute,
    RequestBodyAndURLAttributeNotSame,
    RequestBodyAttributeNotFound,
    RequestBodyNotFound,
)
from common.decorator import decor
from microservices.roles.services.rolesService import Role as RoleClass
from models.database.dbConnection import session
from models.roleModels import Role as RoleModel
from models.rolePolicyModels import Action, Resource
from models.rolePolicyModels import RolePolicy as RolePolicyModel
from sqlalchemy import exc


class RolePolicy:
    def _getPolicyById(self, id):
        try:
            policy = (
                session.query(RolePolicyModel)
                .filter(RolePolicyModel.id == id)
                .first()
            )
        except exc.DataError as de:
            return None

        return policy

    def _checkGetRolePolicyListParameters(self, **kwargs):
        required_parameters = {
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getRolePolicyList(self, **kwargs):

        """
        Fetch List of Policies mapped to the given role
        Input: { "role_id": 45563}
        Output: List []

        """
        self._checkGetRolePolicyListParameters(**kwargs)
        role_id = kwargs.get("role_id")
        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")

        role_policies = session.query(RolePolicyModel).filter(
            RolePolicyModel.role_id == role_id
        )
        role_policiesSerializedObject = [
            role_policy.as_dict() for role_policy in role_policies
        ]
        return role_policiesSerializedObject

    def _checkGetRolePolicyDetailsParameters(self, **kwargs):
        required_parameters = {
            "policy_id": "Policy Id",
            "role_id": "Role Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getRolePolicyDetails(self, **kwargs):

        """
        Fetch  Policies details mapped to the given role
        Input: { "role_id" : 3444, "policy_id": 333232}
        Output: {} containing details of the policy or failure message

        """

        self._checkGetRolePolicyDetailsParameters(**kwargs)
        role_id = kwargs.get("role_id")

        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")

        policy_id = kwargs.get("policy_id")
        policy = self._getPolicyById(policy_id)
        if policy is None:
            raise AttributeIdNotFound("Policy")

        return policy.as_dict()

    def _checkCreateRolePolicyParameters(self, **kwargs):
        required_parameters = {
            "resource": "Resource",
            "action": "Action",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
            "role_id": "Role Id",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def seedRolePolicy(self, **kwargs):

        """
        Add Policy to the given role
        Input: {"role_id": 345}
        Output: List []

        """
        authenticated_user_id = kwargs.get("authenticated_user_id")
        role_name = kwargs.get("role_name").strip()

        role_object = RoleClass()
        role = role_object._getRoleByName(role_name)

        if role is None:
            raise AttributeIdNotFound("Role")
        resource = kwargs.get("resource").strip()
        if not Resource.has_value(resource):
            raise InvalidAttribute("Role Policy", "Resource value")

        if not kwargs.get("action"):
            raise InvalidAttribute("Role Policy", "Action value")

        action = str(kwargs.get("action")).strip()
        if not Action.has_value(action):
            raise InvalidAttribute("Role Policy", "action")

        role_policy = RolePolicyModel()
        role_policy.ptype = f"{role_name}_{resource}_{action}"
        role_policy.role_id = str(role.id)
        role_policy.resource = resource
        role_policy.action = action
        role_policy.created_by = str(authenticated_user_id)
        # To Be changed

        try:
            session.add(role_policy)
            session.commit()
            session.refresh(role_policy)
        except exc.IntegrityError as ex:
            session.rollback()
            print(
                f"Role {role_name} already has same policy mapped to it!",
                f"{resource}_{action}",
            )
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()

        return role_policy.as_dict()

    @decor
    def createRolePolicy(self, **kwargs):

        """
        Add Policy to the given role
        Input: {"role_id": 345}
        Output: List []

        """
        self._checkCreateRolePolicyParameters(**kwargs)
        role_id = kwargs.get("role_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")
        resource = kwargs.get("resource")
        if not Resource.has_value(resource):
            raise InvalidAttribute("Role Policy", "Resource value")

        if not kwargs.get("action"):
            raise InvalidAttribute("Role Policy", "Action value")

        action = str(kwargs.get("action")).strip()
        if not Action.has_value(action):
            raise InvalidAttribute("Role Policy", "action")

        role_policy = RolePolicyModel()
        role_policy.ptype = "p"
        role_policy.role_id = role_id
        role_policy.resource = resource
        role_policy.action = action
        role_policy.created_by = authenticated_user_id
        # To Be changed

        try:
            session.add(role_policy)

            session.commit()
            session.refresh(role_policy)
        except exc.IntegrityError as ex:
            session.rollback()
            raise AnyExceptionHandler("Role already has same policy mapped to it!")

        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)

        finally:
            session.close()

        return role_policy.as_dict()

    def _checkUpdateRolePolicyParameters(self, **kwargs):

        required_parameters = {
            "role_id": "Role Id",
            "policy_id": "Policy Id",
            "resource": "Resource",
            "action": "Action",
            "id": "Id (Policy Id)",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def updateRolePolicy(self, **kwargs):

        """
        Update Policy mapped to the given role
        Input: { "role_id":22 , "policy_id": 344, "id" : 344, "Resource": "Assessment", "Action": "Write"}
        Output: List []

        """

        self._checkUpdateRolePolicyParameters(**kwargs)
        role_id = kwargs.get("role_id")
        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")

        policy_id = kwargs.get("policy_id")
        role_policy = self._getPolicyById(policy_id)

        if role_policy is None:
            raise AttributeIdNotFound("Policy")
        if not kwargs.get("id"):
            raise RequestBodyAttributeNotFound("Id")

        id = kwargs.get("id")
        id = str(id).strip()
        if not id == policy_id:
            raise RequestBodyAndURLAttributeNotSame("Id")

        resource = kwargs.get("resource").strip()
        if not Resource.has_value(resource):
            raise InvalidAttribute("Policy", "Resource value")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        action = kwargs.get("action").strip()
        if not Action.has_value(action):
            raise InvalidAttribute("Policy", "Action value")

        role_policy.resource = resource
        role_policy.action = action
        role_policy.modified_by = authenticated_user_id
        role_policy.modified_on = datetime.now()
        # To Be changed
        try:
            session.add(role_policy)
            session.commit()
            session.refresh(role_policy)

        except exc.IntegrityError as ex:
            session.rollback()
            # if ex.pgcode == "23505":
            raise AlreadyExists("Role", "Policy")

        except Exception as ex:
            session.rollback()
            raise AlreadyExists(ex)
        finally:
            session.close()
        return role_policy.as_dict()

    def _checkDeleteRolePolicyParameters(self, **kwargs):

        required_parameters = {
            "role_id": "Role Id",
            "policy_id": "Policy Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():

            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def deleteRolePolicy(self, **kwargs):

        """
        Delete Policy mapped to the given role
        Input: {"pathParameters":{ "role_id":22 , "policy_id": 344}}
        Output: str-> success/Failure message

        """
        # if not kwargs.get("pathParameters").get("role_id"):
        #     raise URLAttributeNotFound("Role Id")

        self._checkDeleteRolePolicyParameters(**kwargs)
        role_id = kwargs.get("role_id")
        role_object = RoleClass()
        role = role_object._getRoleById(role_id)
        if role is None:
            raise AttributeIdNotFound("Role")

        # if not kwargs.get("pathParameters").get("policy_id"):
        #     raise URLAttributeNotFound("Policy Id")

        policy_id = kwargs.get("policy_id")
        policy = self._getPolicyById(policy_id)
        if policy is None:
            raise AttributeIdNotFound("Policy")

        try:
            session.delete(policy)
            session.commit()
        except Exception as e:
            session.rollback()
            raise AnyExceptionHandler(e)

        finally:
            session.close()
        return "Policy Mapped to the given role deleted successfully"
