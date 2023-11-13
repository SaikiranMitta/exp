import json

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.notifications.services.notificationServices import NotificationSender


def createHandler(event, context):
    try:
        notification = NotificationSender()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response = notification.create_notification(**kwargs)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response


def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}
    record = event.get("Records")
    message = json.loads(record[0].get("Sns").get("Message"))
    topic_arn = (record[0].get("Sns").get("TopicArn"))
    kwargs["TopicArn"] = topic_arn
    kwargs["project_id"] = message.get("project_id")

    if "assessment-created" in topic_arn:
        kwargs["assessment_id"] = message.get("id")
    elif "project-user-created" in topic_arn:
        kwargs["user_id"] = message.get("user_id")
    elif "assessment-updated" in topic_arn:
        kwargs["assessment_id"] = message.get("id")
        kwargs['status'] = message.get("status")

    return kwargs
