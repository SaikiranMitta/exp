import json
from datetime import date, datetime, timedelta

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.assessments.services.assessmentsService import Assessment


def triggerAssessment(event, context):

    try:

        assessment = Assessment()
        response_builder = ResponseBuilder()
        json_response = []
        for _record in event["Records"]:
            kwargs = {}
            _message = json.loads(_record.get("Sns", {}).get("Message", {}))

            if not _message.get("project_id"):
                raise URLAttributeNotFound("Project Id")
            if not _message.get("start_date"):
                raise RequestBodyAttributeNotFound("Start Date")
            start_date = _message.get("start_date")

            if not _message.get("end_date"):
                _end_date = datetime.strptime(start_date, "%Y-%m-%d")
                end_date = _end_date + timedelta(days=+30)
                end_date = str(end_date.date())
            else:
                end_date = _message.get("end_date")

            project_id = _message.get("project_id")
            authenticated_user_id = _message.get("authenticated_user_id")
            authenticated_user_roles = _message.get("authenticated_user_roles")

            kwargs["start_date"] = start_date
            kwargs["end_date"] = end_date
            kwargs["project_id"] = project_id
            kwargs["authenticated_user_id"] = authenticated_user_id
            kwargs["authenticated_user_roles"] = authenticated_user_roles

            response = assessment.createAssessment(**kwargs)
            json_response.append(response)

        response = response_builder.buildResponse(None, json_response, 201)
        print(response)
    except Exception as error:
        response = response_builder.buildResponse(error)

    return response


def createHandler(event, context):

    try:

        assessment = Assessment()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = assessment.createAssessment(**kwargs)
        response = response_builder.buildResponse(None, response, 201)

    except Exception as error:
        response = response_builder.buildResponse(error)

    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("start_date"):
        raise RequestBodyAttributeNotFound("Start Date")
    if not body.get("end_date"):
        raise RequestBodyAttributeNotFound("end Date")
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
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

    start_date = body.get("start_date")
    end_date = body.get("end_date")
    project_id = event.get("pathParameters").get("project_id")

    kwargs["start_date"] = start_date
    kwargs["end_date"] = end_date
    kwargs["project_id"] = project_id
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
