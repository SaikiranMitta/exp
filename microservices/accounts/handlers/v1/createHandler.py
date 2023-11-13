import datetime
import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.accounts.services.accountsService import Account


def createHandler(event, context):
    try:
        account = Account()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = account.createAccount(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("body"):
        raise RequestBodyNotFound()
    body = json.loads(event.get("body"))
    if not body.get("name"):
        raise RequestBodyAttributeNotFound("Name")
    if not body.get("domain_id"):
        raise RequestBodyAttributeNotFound("Domain Id")
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

    name = body.get("name")
    domain_id = body.get("domain_id")
    is_active = body.get("is_active") or False
    created_by = authenticated_user_id
    kwargs["name"] = name
    kwargs["is_active"] = is_active
    kwargs["domain_id"] = domain_id
    kwargs["created_by"] = created_by
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    return kwargs
