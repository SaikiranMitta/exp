import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.rolePolicies.services.rolePoliciesService import (
    RolePolicy,
)


def updateHandler(event, context):

    try:
        role_policy = RolePolicy()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = role_policy.updateRolePolicy(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("pathParameters").get("role_id"):
        raise URLAttributeNotFound("Role Id")
    if not event.get("pathParameters").get("policy_id"):
        raise URLAttributeNotFound("Policy Id")
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("id"):
        raise RequestBodyAttributeNotFound("Id")
    if not body.get("resource"):
        raise RequestBodyAttributeNotFound("Resource")
    if not body.get("action"):
        raise RequestBodyAttributeNotFound("Action")
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

    action = body.get("action")
    resource = body.get("resource")
    id = body.get("id")
    role_id = event.get("pathParameters").get("role_id")
    policy_id = event.get("pathParameters").get("policy_id")
    # authenticated_user_id = "d30847c7-3c46-4e08-8955-c11b97a63db1"
    kwargs["id"] = id
    kwargs["resource"] = resource
    kwargs["action"] = action
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["role_id"] = role_id
    kwargs["policy_id"] = policy_id
    return kwargs
