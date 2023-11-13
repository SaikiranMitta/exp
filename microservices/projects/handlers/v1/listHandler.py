import json
from datetime import datetime
from re import I

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.projects.services.projectsService import Project


def listHandler(event, context):

    try:
        project = Project()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response, pagination = project.getProjectList(**kwargs)
        response = response_builder.buildResponse(
            None, response, None, **{"pagination": pagination}
        )
        # print(response['body'])
        # quit()

    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    if not event.get("requestContext"):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get("requestContext", {}).get("authorizer"):
        raise AnyExceptionHandler("Authentication token not present")

    print("event", event)

    from_date = event.get("queryStringParameters", {}).get("fromDate", None)
    to_date = event.get("queryStringParameters", {}).get("toDate", None)

    # account_id, domain_id, active filter
    domain_id = event.get("queryStringParameters", {}).get("domainId", None)
    account_id = event.get("queryStringParameters", {}).get("accountId", None)
    active = event.get("queryStringParameters", {}).get("active", None)
    audit_frequency = event.get("queryStringParameters", {}).get("audit", None)

    # pagination inputs
    page_size = event.get("queryStringParameters", {}).get("pageSize", None)
    page_number = event.get("queryStringParameters", {}).get("pageNumber", None)
    sort_key = event.get("queryStringParameters", {}).get("sortKey", None)
    sort_order = event.get("queryStringParameters", {}).get("sortOrder", None)

    # search
    search = event.get("queryStringParameters", {}).get("search", None)

    if from_date:
        try:
            datetime.strptime(from_date, "%Y-%m-%d")
        except Exception as error:
            raise IncorrectFormat("fromDate")

        if not to_date:
            raise AttributeNotPresent("toDate")
        try:
            datetime.strptime(to_date, "%Y-%m-%d")
        except Exception as error:
            from_date = None
            raise IncorrectFormat("to_date")

    min_overall_score = event.get("queryStringParameters", {}).get("min_score", None)
    max_overall_score = event.get("queryStringParameters", {}).get("max_score", None)
    if min_overall_score:
        if not max_overall_score:
            raise AttributeNotPresent("max_score")

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

    # print("Authenticated user roles")
    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs = {}
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles
    kwargs["from_date"] = from_date
    kwargs["to_date"] = to_date
    kwargs["min_overall_score"] = min_overall_score
    kwargs["max_overall_score"] = max_overall_score

    # //project filters
    kwargs["domain_id"] = domain_id
    kwargs["account_id"] = account_id
    kwargs["active"] = active
    kwargs["audit_frequency"] = audit_frequency
    kwargs["search"] = search

    # pagination attributes
    kwargs["page_size"] = page_size
    kwargs["page_number"] = page_number
    kwargs["sort_key"] = sort_key
    kwargs["sort_order"] = sort_order

    return kwargs
