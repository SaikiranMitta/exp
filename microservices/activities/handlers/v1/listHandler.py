import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.activities.services.activitiesService import Activity


def listHandler(event, context):

    try:

        activity = Activity()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = activity.getActivityList(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)

    return response


def _checkAndCreateFunctionParametersDictionary(event):
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
    kwargs = {}
    if not event.get("pathParameters").get("project_id"):
        raise URLAttributeNotFound("Project Id")
    if not event.get("pathParameters").get("assessment_id"):
        raise URLAttributeNotFound("Assessment Id")
    if not event.get("pathParameters").get("area_id"):
        raise URLAttributeNotFound("Area Id")
    if not event.get("pathParameters").get("subarea_id"):
        raise URLAttributeNotFound("Subarea Id")
    if not event.get("pathParameters").get("item_id"):
        raise URLAttributeNotFound("Item Id")
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    project_id = event.get("pathParameters").get("project_id")
    assessment_id = event.get("pathParameters").get("assessment_id")
    area_id = event.get("pathParameters").get("area_id")
    subarea_id = event.get("pathParameters").get("subarea_id")
    item_id = event.get("pathParameters").get("item_id")
    kwargs["project_id"] = project_id
    kwargs["assessment_id"] = assessment_id
    kwargs["area_id"] = area_id
    kwargs["subarea_id"] = subarea_id
    kwargs["item_id"] = item_id
    return kwargs
