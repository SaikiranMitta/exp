from common.customExceptions import URLAttributeNotFound
from common.responseBuilder import ResponseBuilder
from microservices.userRoles.services.userRolesService import UserRole


def deleteHandler(event, context):

    try:
        user_role = UserRole()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = user_role.deleteUserRole(**kwargs)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    if not event.get("pathParameters").get("role_id"):
        raise URLAttributeNotFound("Role Id")
    if not event.get("pathParameters").get("user_id"):
        raise URLAttributeNotFound("User Id")
    role_id = event.get("pathParameters").get("role_id")
    user_id = event.get("pathParameters").get("user_id")
    kwargs["role_id"] = role_id
    kwargs["user_id"] = user_id
    return kwargs
