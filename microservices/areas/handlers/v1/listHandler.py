from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.areas.services.areasService import Area


def listHandler(event, context):

    try:

        area = Area()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = area.getAreaList(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    if not event.get("pathParameters").get("assessment_id"):
        raise URLAttributeNotFound("Assessment Id")
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
    assessment_id = event.get("pathParameters").get("assessment_id")
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    kwargs["project_id"] = project_id
    kwargs["assessment_id"] = assessment_id
    return kwargs
