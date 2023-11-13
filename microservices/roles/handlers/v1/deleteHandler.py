import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.roles.services.rolesService import Role


def deleteHandler(event, context):

    try:

        role = Role()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = role.deleteRole(**kwargs)
        response = response_builder.buildResponse(None, response)

    except Exception as error:

        response = response_builder.buildResponse(error)

    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("pathParameters").get("role_id"):
        raise AttributeIdNotFound("Role Id")
    role_id = event.get("pathParameters").get("role_id")
    kwargs["role_id"] = role_id
    return kwargs
