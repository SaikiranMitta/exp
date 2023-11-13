import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.roles.services.rolesService import Role


def updateHandler(event, context):

    try:
        role = Role()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = role.updateRole(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}

    if not event.get("pathParameters").get("role_id"):
        raise RequestBodyAttributeNotFound("Role Id")
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("id"):
        raise RequestBodyAttributeNotFound("Id")
    if not body.get("name"):
        raise RequestBodyAttributeNotFound("Name")
    role_id = event.get("pathParameters").get("role_id")
    id = body.get("id")
    name = body.get("name")
    if not id == role_id:
        raise RequestBodyAndURLAttributeNotSame("Id")
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

    kwargs["role_id"] = role_id
    kwargs["id"] = id
    kwargs["name"] = name
    # authenticated_user_id = "d30847c7-3c46-4e08-8955-c11b97a63db1"
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
