import json
from typing import List


class ResponseBuilder:
    def _getResponseCodes(self, error):
        response_codes = {
            "URLAttributeNotFound": 400,
            "AttributeIdNotFound": 404,
            "RequestBodyAttributeNotFound": 400,
            "AlreadyExists": 409,
            "InvalidAttribute": 409,
            "AnyExpectionHandler": 409,
            "IncorrectFormat": 400,
            "RequestBodyAndURLAttributeNotSame": 409,
        }
        if not response_codes.get(error.__class__.__name__):
            return 404
        return response_codes.get(error.__class__.__name__)

    def buildResponse(self, error, response=None, statusCode=None, **kwargs):
        print(error)
        if error == None and not response == None:
            if statusCode:
                statusCode = statusCode
            else:
                statusCode = 200

            if kwargs.get('pagination') and isinstance(response, List) and statusCode != 207:
                pagination = kwargs.get('pagination', {})
                response = {
                    "statusCode": statusCode,
                    "body": json.dumps({"data": {**pagination, "results": response}}),
                }
            elif isinstance(response, List) and statusCode != 207:
                response = {
                    "statusCode": statusCode,
                    "body": json.dumps({"data": {"results": response}}),
                }
            elif isinstance(response, List) and statusCode == 207:
                response = {
                    "statusCode": statusCode,
                    "body": json.dumps({"data": response}),
                }
            else:
                response = {
                    "statusCode": statusCode,
                    "body": json.dumps(
                        {
                            "data": response,
                        }
                    ),
                }
        else:
            error_msg = str(error)
            if hasattr(error, "message"):
                error_msg = error.message
            response = {
                "statusCode": self._getResponseCodes(error),
                "body": json.dumps(
                    {
                        "data": error_msg,
                    }
                ),
            }
        response["headers"] = {"Content-Type": "application/json"}
        return response
