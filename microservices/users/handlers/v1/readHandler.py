from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.users.services.usersService import User


def readHandler(event, context):

    try:
        user = User()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user.getUserDetails(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    kwargs = {}

    print("Inside checkParameterFunction")
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

    if not event.get("pathParameters").get("user_id"):
        raise URLAttributeNotFound("User Id")

    user_id = event.get("pathParameters").get("user_id").strip()
    kwargs["user_id"] = user_id
    print("Read kwargs", {user_id})

    return kwargs
