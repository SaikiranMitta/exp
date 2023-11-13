import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.users.services.usersService import User


def updateHandler(event, context):

    try:
        user = User()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user.updateUserDetails(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("name"):
        raise RequestBodyAttributeNotFound("Name")
    name = body.get("name")
    if not body.get("ps_no"):
        raise RequestBodyAttributeNotFound("PS_No")
    ps_no = body.get("ps_no")
    status = body.get("status")
    if not event.get("pathParameters", {}).get("user_id"):
        raise RequestBodyAttributeNotFound("User ID")
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
    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    kwargs["user_id"] = user_id
    kwargs["name"] = name
    kwargs["status"] = status
    kwargs["ps_no"] = ps_no
    return kwargs
