import json
from datetime import date, datetime
from typing import Any

from common.customExceptions import *
from common.decorator import decor
from microservices.assessments.services.assessmentsService import (
    Assessment as AssessmentClass,
)
from microservices.projects.services.projectsService import Project as ProjectClass
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import AssessmentStatus
from models.assessmentModels import Response as ResponseModel
from models.assessmentModels import ResponseValue
from models.checklistModels import Activity as ActivityModel
from models.checklistModels import Area as AreaModel
from models.checklistModels import Checklist as ChecklistModel
from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
from models.projectModels import Project as ProjectModel
from sqlalchemy import exc


class AssessmentResponse:
    def _validateDate(self, date_text):
        try:
            datetime.strptime(date_text, "%Y-%m-%d")
            return True
        except ValueError:
            return None

    def _checkIfAssessmentSubmitted(self, assessment_id):

        responses = session.query(ResponseModel).filter(
            ResponseModel.assessment_id,
            ResponseModel.value == None,
            ResponseModel.type.name == "ManagerResponse",
        )

        responses_comments_none = session.query(ResponseModel).filter(
            ResponseModel.assessment_id,
            ResponseModel.value.name in ["Yes", "NA"],
            ResponseModel.comments == None,
            ResponseModel.type.name == "ManagerResponse",
        )
        return responses, responses_comments_none

    def _getResponseById(self, id):
        try:
            response = (
                session.query(ResponseModel)
                .filter(ResponseModel.id == str(id))
                .first()
            )

        except exc.DataError as de:
            return None
        return response

    def _validateResponse(self, responseObject):
        if not responseObject.get("id"):
            return {
                "success": False,
                "body": "Id attribute not present in request body!",
            }
        if not responseObject.get("value"):
            return {
                "success": False,
                "body": "Value attribute not present in request body!",
            }
        if not ResponseValue.has_value(responseObject.get("value")):
            return {
                "success": "False",
                "body": "Incorrect value of Response value!",
            }

    def _checkUpdateAssessmentResponsesParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "responses": "Responses",
            "role": "Role",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)
        if not isinstance(kwargs.get("responses"), list):
            raise AnyExceptionHandler("Type of responses field should be list")

    def _checkResponseFormat(self, response, role):
        required_parameters = {
            "id": "Id",
            "value": "Value",
        }
        if not role == "Reviewer":
            for key, value in required_parameters.items():
                if key not in response:
                    raise AttributeNotPresent(value)
                # comment will be optional for now - as per feedback
                # if response.get("value") in ["Yes", "NA"]:
                #     if not response.get("comments"):
                #         id = response.get("id")
                #         raise AnyExceptionHandler(
                #             f"Comment attribute not present for object with given Id {id}"
                #         )
        else:
            for key, value in required_parameters.items():
                if key not in response:
                    raise AttributeNotPresent(value)

    def _checkIfResponseIdAndRoleMatch(self, response, role, response_return):
        rolesAndResponseType = {
            "ManagerResponse": ["Manager"],
            "ReviewerResponse": ["Reviewer"],
        }
        if role not in rolesAndResponseType.get(response.type.name):
            id = str(response.id)
            return True, response_return.get("body").append(
                {
                    "success": False,
                    "body": f"Response with the given Id {id} cannot be filled by user with {role} role.",
                }
            )
        return False, response_return

    @decor
    def updateAssessmentResponses(self, **kwargs):

        """
        Fetch details of Assessments in the system.
        Input: None
        Output: {} dict containing details of assessment/failure message

        """
        self._checkUpdateAssessmentResponsesParameters(**kwargs)
        role = kwargs.get("role")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)

        # check if the project exists & is active
        if project is None:
            raise AttributeIdNotFound("Project")
        if not project.is_active:
            raise AnyExceptionHandler(
                "Cannot update responses for an inactive project"
            )

        assessment_id = kwargs.get("assessment_id")
        assessment_object = AssessmentClass()
        assessment = assessment_object._getAssessmentById(assessment_id)

        # verify if the assessment exists & belongs to the same project
        if assessment is None:
            raise AttributeIdNotFound("Assessment")
        if not str(assessment.project_id) == project_id:
            raise AnyExceptionHandler(
                "Assessment does not belong to the given Project"
            )
        responses = kwargs.get("responses")
        if len(responses) == 0:
            raise AnyExceptionHandler("Response list is empty!")
        response_return = {"body": []}
        for responseObject in responses:
            self._checkResponseFormat(responseObject, role)
            validated_response = self._validateResponse(responseObject)
            if validated_response is None:
                response = self._getResponseById(responseObject.get("id"))
                if response is None:
                    response_return.get("body").append(
                        {
                            "success": False,
                            "message": "Not found",
                            "body": {
                                "id": responseObject.get("id"),
                                "message": "Response with the specified Id not found!",
                            },
                        }
                    )
                else:
                    if not str(response.assessment_id) == assessment_id:
                        id = str(response.id)
                        response_return.get("body").append(
                            {
                                "success": False,
                                "body": f"Response with the given Id {id} does not belong to the given assessment",
                            }
                        )
                        pass
                    if not self._checkIfResponseIdAndRoleMatch(
                        response, role, response_return
                    )[0]:
                        response.value = responseObject.get("value")
                        response.comments = responseObject.get("comments")
                        response.modified_by = authenticated_user_id
                        response.modified_on = datetime.now()
                        try:
                            session.add(response)
                            session.commit()
                            session.refresh(response)
                        except Exception as ex:
                            session.rollback()
                            raise AnyExceptionHandler(ex)
                        if response is not None:
                            response_return.get("body").append(
                                {
                                    "success": True,
                                    "body": response.as_dict(),
                                }
                            )
            else:
                response_return.get("body").append(
                    {"success": False, "message": validated_response}
                )
        body = response_return["body"]
        return body
