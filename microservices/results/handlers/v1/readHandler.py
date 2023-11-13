from common.responseBuilder import ResponseBuilder
from microservices.results.services.resultsService import Result


def readHandler(event, context):

    try:

        result = Result()
        response_builder = ResponseBuilder()
        response = result.getAssessmentResults(**event)
        response = response_builder.buildResponse(None, response)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response
