import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.assessmentResponses.services.assessmentResponses import (
    AssessmentResponse,
)


def updateHandler(event, context):

    try:
        assessment_response = AssessmentResponse()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        responses = assessment_response.updateAssessmentResponses(**kwargs)
        for response in responses:
            if response.get("success"):
                response["statusCode"] = 200

            else:
                if response.get("message"):
                    response["statusCode"] = 404
                else:
                    response["statusCode"] = 400
            response.pop("success", {})

        response = response_builder.buildResponse(None, responses, 207)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkResponseFormat(response, role):
    required_parameters = {
        "id": "Id",
        "value": "Value",
    }
    if not role == "Reviewer":
        for key, value in required_parameters.items():
            if key not in response:
                raise AttributeNotPresent(value)
    else:
        for key, value in required_parameters.items():
            if key not in response:
                raise AttributeNotPresent(value)


def _checkAndCreateFunctionParametersDictionary(event):

    """Function to check and create service function Parameters"""

    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    if not event.get("pathParameters").get("assessment_id"):
        raise URLAttributeNotFound("Assessment Id")
    if not body.get("responses"):
        raise RequestBodyAttributeNotFound("Responses")
    responses = body.get("responses")
    if not isinstance(responses, list):
        raise AnyExceptionHandler("Type of responses field should be list")
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

    roles = authenticated_user_roles
    if not isinstance(roles, list):
        role = roles
    else:
        if "Project_Manager" in roles:
            role = "Manager"
        elif "Reviewer" in roles:
            role = "Reviewer"
        else:
            raise AnyExceptionHandler(
                f"Roles do no belong to Project Manger or Reviewer"
            )

    for response in responses:
        _checkResponseFormat(response, role)

    project_id = event.get("pathParameters").get("project_id")
    assessment_id = event.get("pathParameters").get("assessment_id")
    kwargs["project_id"] = project_id
    kwargs["assessment_id"] = assessment_id
    kwargs["responses"] = responses
    kwargs["role"] = role
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
