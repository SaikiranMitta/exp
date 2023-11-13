import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.rolePolicies.services.rolePoliciesService import RolePolicy


def seedHandler(event, context):
    try:
        role_policy = RolePolicy()
        response_builder = ResponseBuilder()

        responses = []
        for _role_policy in event:

            kwargs = {}
            resource = _role_policy.get("Resource")
            action = _role_policy.get("Action")
            role_name = _role_policy.get("Role")
            kwargs["resource"] = resource
            kwargs["action"] = action
            kwargs["role_name"] = role_name
            kwargs["authenticated_user_id"] = "8aa1329e-b7c5-41a5-acba-5cbb9fe3c774"
            kwargs["authenticated_user_roles"] = ["Admin"]

            _response = role_policy.seedRolePolicy(**kwargs)
            _response = response_builder.buildResponse(None, _response, 201)
            responses.append(_response)

    except Exception as error:
        responses = response_builder.buildResponse(error)
    return responses


def createHandler(event, context):
    try:
        role_policy = RolePolicy()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = role_policy.createRolePolicy(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("resource"):
        raise RequestBodyAttributeNotFound("Resource")
    if not body.get("action"):
        raise RequestBodyAttributeNotFound("Action")
    if not event.get("pathParameters").get("role_id"):
        raise URLAttributeNotFound("Role Id")
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

    resource = body.get("resource")
    action = body.get("action")
    role_id = event.get("pathParameters").get("role_id")
    kwargs["resource"] = resource
    kwargs["action"] = action
    kwargs["role_id"] = role_id
    # authenticated_user_id = "d30847c7-3c46-4e08-8955-c11b97a63db1"
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
