import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.domains.services.domainsService import Domain


def readHandler(event, context):
    try:
        domain = Domain()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = domain.getDomainDetails(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
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

    if not event.get("pathParameters").get("domain_id"):
        raise RequestBodyAttributeNotFound("Domain Id")
    domain_id = event.get("pathParameters").get("domain_id")
    kwargs["domain_id"] = domain_id
    return kwargs
