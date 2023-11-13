import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.userRoles.services.userRolesService import UserRole


def updateHandler(event, context):

    try:
        user = UserRole()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user.getUserRoleListForDeleteAndCreate(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    """Function to check and create service function Parameters"""

    kwargs = {}
    if not event.get("pathParameters").get("user_id"):
        raise URLAttributeNotFound("User Id")

    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("names"):
        raise RequestBodyAttributeNotFound("Names")
    if not event.get("requestContext"):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get("requestContext").get("authorizer"):
        # if not event.get("authorizer"):
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

    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]

    names = body.get("names")
    user_id = event.get("pathParameters").get("user_id")
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    kwargs["user_id"] = user_id
    kwargs["names"] = names
    return kwargs
