import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.projectUsers.services.projectUsersService import (
    ProjectUser,
)


def createHandler(event, context):

    try:
        print('projectUsers createHandler event:: ', event)
        project_users = ProjectUser()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = project_users.addProjectUser(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("user_id"):
        raise RequestBodyAttributeNotFound("User Id")
    if not event.get('requestContext'):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get('requestContext', {}).get('authorizer'):
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

    project_id = event.get("pathParameters").get("project_id")
    user_id = body.get("user_id")
    kwargs["project_id"] = project_id
    kwargs["user_id"] = user_id
    # authenticated_user_id = "d30847c7-3c46-4e08-8955-c11b97a63db1"
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
