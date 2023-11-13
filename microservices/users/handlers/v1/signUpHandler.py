from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.users.services.usersService import User


def preSignUpHandler(event, context):

    try:
        user = User()
        response_builder = ResponseBuilder()
        # response = user.getUserList(**kwargs)
        print(event)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return event


def postSignUpHandler(event, context):

    try:
        user = User()
        response_builder = ResponseBuilder()

        if (
            event.get("request").get("userAttributes").get("cognito:user_status")
            == "FORCE_CHANGE_PASSWORD"
        ):
            user_id = event.get("userName")
            user_update = user.verifiedUser(user_id)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return event
