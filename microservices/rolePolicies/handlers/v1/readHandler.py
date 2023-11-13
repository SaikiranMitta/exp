import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.rolePolicies.services.rolePoliciesService import (
    RolePolicy,
)


def readHandler(event, context):

    try:
        role_policy = RolePolicy()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = role_policy.getRolePolicyDetails(**kwargs)
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
    role_id = event.get("pathParameters").get("role_id")
    policy_id = event.get("pathParameters").get("policy_id")
    kwargs["role_id"] = role_id
    kwargs["policy_id"] = policy_id
    return kwargs
