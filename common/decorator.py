from models.accessControlModels import (
    FunctionDescription as FunctionDescriptionModel,
)
from models.database.dbConnection import session
from models.roleModels import Role as RoleModel
from models.rolePolicyModels import RolePolicy as RolePolicyModel

from common.customExceptions import *

# def _addPolicy():
#     print("***********" * 9)

#     a = session.query(RolePolicyModel).filter().first()
#     print(a.resource)
#     print(a.action)
#     print(a.role_id)
#     print("***" * 9)
# session.delete(a)
# session.commit()
# role_policy_object = RolePolicyModel()
# role_policy_object.resource = "Project"
# role_policy_object.role_id = (
#     "c80847c7-3c46-4e08-8955-c11b97a63db8"
# )
# role_policy_object.action = "Read"
# role_policy_object.ptype = "p"
# role_policy_object.created_by = (
#     "d30847c7-3c46-4e08-8955-c11b97a63db1"
# )
# session.add(role_policy_object)
# session.commit()


# _addPolicy()


def _getRoleByName(name):
    try:
        role = session.query(RoleModel).filter(RoleModel.name == name).first()
    except Exception:
        return None
    finally:
        session.close()
    return role


def _getResourceAndAction(function_name):

    try:
        function_description = (
            session.query(FunctionDescriptionModel)
            .filter(FunctionDescriptionModel.name == function_name)
            .first()
        )
    except Exception:
        return None
    finally:
        session.close()
    resource = function_description.resource.name
    action = function_description.action.name
    return resource, action


def _getRolePolicy(role_id, resource, action):
    try:

        policy = (
            session.query(RolePolicyModel)
            .filter(
                RolePolicyModel.role_id == str(role_id),
                RolePolicyModel.action == action,
                RolePolicyModel.resource == resource,
            )
            .first()
        )
    except Exception:
        return None
    finally:
        session.close()
    return policy


def decor(func):
    def inner(self, **kwargs):
        if not kwargs.get("authenticated_user_roles"):
            raise AnyExceptionHandler("Authenticated User Role needed!")
        authenticated_user_roles = kwargs.get("authenticated_user_roles")
        # authenticated_user_roles = ["Project_Manager"]
        if "Admin" not in authenticated_user_roles:
            flag = 0
            for role in authenticated_user_roles:
                # print(f"id: {role.id}, role: {role_name}")
                if role is not None:
                    role_name = role.replace("_", " ")
                    role = _getRoleByName(role_name)
                    resource, action = _getResourceAndAction(func.__name__)
                    # print("resource", resource, "action", action)
                    policy = _getRolePolicy(role.id, resource, action)

                    if policy is not None:
                        flag = 1
                        return func(self, **kwargs)

                    else:
                        policy = _getRolePolicy(role.id, "All", action)
                        if policy is not None:
                            flag = 1
                            return func(self, **kwargs)
                        else:
                            policy = _getRolePolicy(role.id, resource, "All")
                            if policy is not None:
                                flag = 1
                                return func(self, **kwargs)
                            else:
                                policy = _getRolePolicy(role.id, "All", "All")
                                if policy is not None:
                                    flag = 1
                                    return func(self, **kwargs)
                                else:
                                    raise AnyExceptionHandler(
                                        "Unauthorized access! - No policy found"
                                    )
                else:
                    raise AnyExceptionHandler("Role not found")
            if flag == 0:
                raise AnyExceptionHandler("Unauthorized access!")
        else:
            return func(self, **kwargs)

    return inner
