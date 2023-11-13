from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.domains.services.domainsService import Domain


def listHandler(event, context):
    try:
        domain = Domain()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response, pagination = domain.getDomainList(**kwargs)
        response = response_builder.buildResponse(None, response, None, **{'pagination': pagination})

    except Exception as error:
        response = response_builder.buildResponse(error)

    return response


def _checkAndCreateFunctionParametersDictionary(event):
    if not event.get("requestContext"):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get("requestContext", {}).get("authorizer"):
        raise AnyExceptionHandler("Authentication token not present")
    
    #filters
    domain_head_id = event.get("queryStringParameters", {}).get("domainHeadId", None)

    # pagination inputs
    page_size = event.get("queryStringParameters", {}).get("pageSize", None)
    page_number = event.get("queryStringParameters", {}).get("pageNumber", None)
    sort_key = event.get("queryStringParameters", {}).get("sortKey", None)
    sort_order = event.get("queryStringParameters", {}).get("sortOrder", None)

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

    print("AUthenticated user roles")
    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs = {}
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles

    # Filters
    kwargs["domain_head_id"] = domain_head_id

    # pagination attributes
    kwargs["page_size"] = page_size
    kwargs["page_number"] = page_number
    kwargs["sort_key"] = sort_key
    kwargs["sort_order"] = sort_order

    return kwargs
