import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.userRoles.services.userRolesService import UserRole


def createHandler(event, context):

    try:
        user_role = UserRole()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user_role.addUserRole(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("role_id"):
        raise RequestBodyAttributeNotFound("Role Id")
    if not event.get("pathParameters").get("user_id"):
        raise URLAttributeNotFound("User Id")
    role_id = body.get("role_id")
    user_id = event.get("pathParameters").get("user_id")

    if not event.get("requestContext"):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get("requestContext", {}).get("authorizer"):
        raise AnyExceptionHandler("Authentication token not present")

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

    kwargs["role_id"] = role_id
    kwargs["user_id"] = user_id

    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
