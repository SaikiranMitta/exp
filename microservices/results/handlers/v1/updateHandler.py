import json

from microservices.results.services.resultsService import Result


def updateHandler(event, context):

    result = Result()
    response = result.updateAssessmentGrades(**event)
    return response
