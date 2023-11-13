import json
import os

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.accounts.services.accountsService import Account


def listHandler(event, context):

    try:
        account = Account()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response, pagination = account.getAccountList(**kwargs)
        response = response_builder.buildResponse(
            None, response, None, **{"pagination": pagination}
        )
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}

    # domain_id, active filter
    domain_id = event.get("queryStringParameters", {}).get("domainId", None)
    active = event.get("queryStringParameters", {}).get("active", None)

    # pagination inputs
    page_size = event.get("queryStringParameters", {}).get("pageSize", None)
    page_number = event.get("queryStringParameters", {}).get("pageNumber", None)
    sort_key = event.get("queryStringParameters", {}).get("sortKey", None)
    sort_order = event.get("queryStringParameters", {}).get("sortOrder", None)

    # search
    search = event.get("queryStringParameters", {}).get("search", None)

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
    print("User ID :", authenticated_user_id)
    print("Roles :", authenticated_user_roles)
    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    # authenticated_user_roles = ["Project_Manager"]
    # authenticated_user_id = "ed062af3-bdd6-459e-9881-18891742eea1"
    # print("authenticated_user_roles", authenticated_user_roles)
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles

    # filters
    kwargs["domain_id"] = domain_id
    kwargs["active"] = active
    kwargs["search"] = search

    # pagination attributes
    kwargs["page_size"] = page_size
    kwargs["page_number"] = page_number
    kwargs["sort_key"] = sort_key
    kwargs["sort_order"] = sort_order

    return kwargs
