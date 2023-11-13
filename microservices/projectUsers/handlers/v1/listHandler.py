import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.projectUsers.services.projectUsersService import (
    ProjectUser,
)


def listHandler(event, context):

    try:
        project_users = ProjectUser()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = project_users.getProjectUserList(**kwargs)
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
    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    project_id = event.get("pathParameters").get("project_id")
    kwargs["project_id"] = project_id
    return kwargs
