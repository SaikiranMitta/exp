import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.projects.services.projectsService import Project


def updateHandler(event, context):
    try:
        project = Project()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = project.updateProject(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):

    """Function to check and create service function Parameters"""

    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    if not body.get("name"):
        raise RequestBodyAttributeNotFound("Name")
    if not body.get("id"):
        raise RequestBodyAttributeNotFound("Id")
    if not body.get("account_id"):
        raise RequestBodyAttributeNotFound("Account Id")
    if not body.get("trello_link"):
        raise RequestBodyAttributeNotFound("Trello link")
    if not body.get("start_date"):
        raise RequestBodyAttributeNotFound("Start date")
    if not body.get("audit_frequency"):
        raise RequestBodyAttributeNotFound("Audit Frequency")
    if not body.get("details"):
        raise RequestBodyAttributeNotFound("Details")

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
    name = body.get("name")
    id = body.get("id")
    details = body.get("details")
    account_id = body.get("account_id")
    trello_link = body.get("trello_link")
    start_date = body.get("start_date")
    audit_frequency = body.get("audit_frequency")
    kwargs["name"] = name
    kwargs["id"] = id
    kwargs["project_id"] = project_id
    kwargs["details"] = details
    kwargs["account_id"] = account_id
    kwargs["trello_link"] = trello_link
    kwargs["start_date"] = start_date
    kwargs["audit_frequency"] = audit_frequency
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    
    return kwargs
