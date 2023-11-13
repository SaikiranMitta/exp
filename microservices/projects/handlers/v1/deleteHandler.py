import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.projects.services.projectsService import Project


def deleteHandler(event, context):
    try:

        project = Project()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = project.deleteProject(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    kwargs = {}
    if not event.get("pathParameters").get("project_id"):
        raise AttributeNotPresent("Project Id")
    kwargs["project_id"] = event.get("pathParameters").get(
        "project_id"
    )
    return kwargs
