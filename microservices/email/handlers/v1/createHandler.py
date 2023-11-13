import json
from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.email.services.emailService import sendEmailToUser


def createHandler(event, context):
    try:
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = sendEmailToUser(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    record = event.get("Records")
    body = json.loads(record[0].get("body"))
    if not body.get("user_id"):
        raise RequestBodyAttributeNotFound("user_id")
    if not body.get("message"):
        raise RequestBodyAttributeNotFound("message")
    if not body.get("description"):
        raise RequestBodyAttributeNotFound("description")

    kwargs["user_id"] = body.get("user_id")
    kwargs["message"] = body.get("message")
    kwargs["description"] = body.get("description")
    kwargs["event_data"] = record
    return kwargs
