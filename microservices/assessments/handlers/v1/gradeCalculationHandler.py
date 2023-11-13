import json

from common.customExceptions import RequestBodyNotFound, RequestBodyAttributeNotFound
from common.responseBuilder import ResponseBuilder

from microservices.assessments.services.assessmentsService import Assessment

def gradeCalculationHandler(event, context):
    try:
        assessment = Assessment()
        response_builder = ResponseBuilder()
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        print("triggered via sqs",kwargs)
        assessment.gradeCalculator(**kwargs)
        response = response_builder.buildResponse(None, True)
    except Exception as error:
        print(error)
        response = response_builder.buildResponse(error)
    return response

def readHandler(event, context):
    # if not event.get("pathParameters").get("grade_calculation_task_id"):
    #     raise URLAttributeNotFound("Assessment Id")
    
    assessment = Assessment()
    response_builder = ResponseBuilder()
    calculation_task = assessment._getGradeCalculationTaskById(event.get("pathParameters").get("grade_calculation_task_id"))
    if not calculation_task:
        return response_builder.buildResponse(None)
    return response_builder.buildResponse(None, calculation_task.as_dict())

def _checkAndCreateFunctionParametersDictionary(event):
    kwargs = {}

    for record in event["Records"]:
        if not record.get("body"):
            raise RequestBodyNotFound()
    body = json.loads(event["Records"][0].get("body"))

    if not body.get("assessment_id"):
        raise RequestBodyAttributeNotFound("assessment_id")
    kwargs["assessment_id"] = body.get("assessment_id")
    
    if not body.get("grade_calculation_task_id"):
        raise RequestBodyAttributeNotFound("grade_calculation_task_id")
    kwargs["grade_calculation_task_id"] = body.get("grade_calculation_task_id")

    if not body.get("status"):
        raise RequestBodyAttributeNotFound("status")
    kwargs["status"] = body.get("status")

    return kwargs
