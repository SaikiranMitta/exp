from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.userRoles.services.userRolesService import UserRole


def listHandler(event, context):

    try:
        user_role = UserRole()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user_role.getUserRoleList(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    kwargs = {}
    authenticated_user_id = (
        event.get("requestContext", {})
        .get("authorizer", {})
        .get("lambda", {})
        .get("sub")
    )

    authenticated_user_roles = (
        event.get("requestContext", {})
        .get("authorizer", {})
        .get("lambda", {})
        .get("cognito:groups")
    )

    if not event.get("pathParameters").get("user_id"):
        raise URLAttributeNotFound("User Id")
    user_id = event.get("pathParameters").get("user_id")
    kwargs["user_id"] = user_id

    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
