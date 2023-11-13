import json

from common.responseBuilder import ResponseBuilder
from microservices.projects.services.projectsService import Project


def readHandler(event, context):
    try:
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        project = Project()
        response_builder = ResponseBuilder()
        response = project.getProjectDetails(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    """Function to check and create service function Parameters"""

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

    project_id = event.get("pathParameters").get("project_id")
    kwargs["project_id"] = project_id
    return kwargs
