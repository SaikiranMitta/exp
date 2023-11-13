import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.assessments.services.assessmentsService import Assessment


def updateHandler(event, context):

    try:
        assessment = Assessment()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = assessment.updateAssessmentStatus(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        print(error)
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

    if not event.get("pathParameters").get("assessment_id"):
        raise URLAttributeNotFound("Assessment Id")
    assessment_id = event.get("pathParameters").get("assessment_id")
    kwargs["assessment_id"] = assessment_id

    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))

    if not body.get("status"):
        raise RequestBodyAttributeNotFound("Status")

    # if not event.get("queryStringParameters").get("status"):
    #     raise URLAttributeNotFound("Status")
    kwargs["status"] = body.get("status")
    return kwargs
