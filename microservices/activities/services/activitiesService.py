import imp
import json
from re import sub
from typing import Any

from sqlalchemy import desc, exc

from common.customExceptions import *
from common.customExceptions import (  # PathParameterNotFound,; URLAttributeNotFound,
    AnyExceptionHandler,
    AttributeIdNotFound,
)
from common.decorator import decor
from microservices.areas.services.areasService import Area as AreaClass
from microservices.assessments.services.assessmentsService import (
    Assessment as AssessmentClass,
)
from microservices.items.services.itemsService import Item as ItemClass
from microservices.projects.services.projectsService import Project as ProjectClass
from microservices.subareas.services.subareasService import Subarea as SubareaClass
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import (
    AssessmentResponseDelta as AssessmentResponseDeltaModel,
)
from models.assessmentModels import Response as ResponseModel
from models.checklistModels import Activity as ActivityModel
from models.checklistModels import Area as AreaModel
from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
from models.projectModels import Project as ProjectModel


class Activity:
    def _checkGetActivityListParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "area_id": "Area Id",
            "subarea_id": "Subarea Id",
            "item_id": "Item Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    def _fetchActivityDelta(self, assessment_id, activity_id):
        assessment_responses_delta = (
            session.query(AssessmentResponseDeltaModel)
            .filter(
                AssessmentResponseDeltaModel.assessment_id == assessment_id,
                AssessmentResponseDeltaModel.activity_id == activity_id,
            )
            .all()
        )

        delta = [
            assessment_response_delta.as_dict()
            for assessment_response_delta in assessment_responses_delta
        ]
        return delta

    @decor
    def getActivityList(self, **kwargs):
        """
        Fetch List of Activities in the system.
        Input: None
        Output: List []

        """

        self._checkGetActivityListParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)

        if project is None:
            raise AttributeIdNotFound("Project")
        assessment_id = kwargs.get("assessment_id")
        assessment_object = AssessmentClass()
        assessment = assessment_object._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")

        if not str(assessment.project_id) == project_id:
            raise AnyExceptionHandler(
                "Assessment does not belong to the given project!"
            )
        area_id = kwargs.get("area_id")
        area_object = AreaClass()
        area = area_object._getAreaById(area_id)

        if area is None:
            raise AttributeIdNotFound("Area")
        if not str(area.checklist_id) == str(assessment.checklist_id):
            raise AnyExceptionHandler(
                "Area Id does not belong to the correct checklist!"
            )
        subarea_id = kwargs.get("subarea_id")
        item_id = kwargs.get("item_id")
        subarea_object = SubareaClass()
        subarea = subarea_object._getSubareaById(subarea_id)
        if subarea is None:
            raise AttributeIdNotFound("Subarea")
        if not str(subarea.area_id) == area_id:
            raise AnyExceptionHandler("Subarea does not belong to the given Area")
        item_object = ItemClass()
        item = item_object._getItemById(item_id)
        if item is None:
            raise AttributeIdNotFound("Item")
        if not str(item.subarea_id) == subarea_id:
            raise AnyExceptionHandler("Item does not belong to the given subarea")
        activities = session.query(ActivityModel).filter(
            ActivityModel.item_id == item_id
        )
        activity_list = []

        # 07-March-2023 | activiy delta logic | start
        assessment_responses_delta = session.query(
            AssessmentResponseDeltaModel.id,
            AssessmentResponseDeltaModel.activity_id,
            AssessmentResponseDeltaModel.assessment_id,
            AssessmentResponseDeltaModel.previous_assessment_id,
            AssessmentResponseDeltaModel.previous_value,
            AssessmentResponseDeltaModel.previous_comments,
            AssessmentResponseDeltaModel.type,
        ).filter(
            AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
        )

        print("length of assessment delta", assessment_responses_delta)

        manager_delta_dict = {}
        reviewer_delta_dict = {}
        if not assessment_responses_delta:
            pass
        else:
            # delta_dict = {str(ard.activity_id):dict(ard) for ard in assessment_responses_delta}
            for ard in assessment_responses_delta:
                if ard.type.value == "ReviewerDelta":
                    reviewer_delta_dict[str(ard.activity_id)] = {
                        "id": str(ard.id),
                        "activity_id": str(ard.activity_id),
                        "assessment_id": str(ard.assessment_id),
                        "previous_assessment_id": str(ard.previous_assessment_id),
                        "value": ard.previous_value,
                        "comments": ard.previous_comments,
                    }
                if ard.type.value == "ManagerDelta":
                    manager_delta_dict[str(ard.activity_id)] = {
                        "id": str(ard.id),
                        "activity_id": str(ard.activity_id),
                        "assessment_id": str(ard.assessment_id),
                        "previous_assessment_id": str(ard.previous_assessment_id),
                        "value": ard.previous_value,
                        "comments": ard.previous_comments,
                    }

        # 07-March-2023 | activiy delta logic | end

        for activity in activities:
            activity_details = activity.as_dict()

            print(str(assessment_id), str(activity.id))

            responses = session.query(ResponseModel).filter(
                ResponseModel.assessment_id == str(assessment_id),
                ResponseModel.activity_id == str(activity.id),
            )

            if responses is not None:
                user_response = responses.filter(
                    ResponseModel.type == "ManagerResponse"
                ).first()
                # user_response = session.query(ResponseModel).filter(
                #     ResponseModel.assessment_id == str(assessment_id),
                #     ResponseModel.activity_id == str(activity.id),
                #     ResponseModel.type == "ManagerResponse",
                # )

                if user_response is None:
                    user_response_details = {}
                else:
                    user_response_details = user_response.as_dict()

                    ## 07-March-2023 | delta response logic updated (old commented) | Start
                    # delta = self._fetchActivityDelta(
                    #     assessment_id=str(assessment_id),
                    #     activity_id=str(activity.id),
                    # )
                    # if delta is None:
                    #     user_response_details["delta"] = {}
                    # else:
                    #     user_response_details["delta"] = delta

                    if (
                        any(reviewer_delta_dict)
                        and str(activity.id) in reviewer_delta_dict
                    ):
                        user_response_details["delta"] = reviewer_delta_dict[
                            str(activity.id)
                        ]
                    else:
                        user_response_details["delta"] = {}

                    ## 07-March-2023 | delta response logic updated | End

                reviewer_response = responses.filter(
                    ResponseModel.type == "ReviewerResponse"
                ).first()

                if reviewer_response is None:
                    reviewer_response_details = {}
                else:
                    reviewer_response_details = reviewer_response.as_dict()

                    if (
                        any(manager_delta_dict)
                        and str(activity.id) in manager_delta_dict
                    ):
                        reviewer_response_details["delta"] = manager_delta_dict[
                            str(activity.id)
                        ]
                    else:
                        reviewer_response_details["delta"] = {}

                activity_details["user_response_details"] = user_response_details
                activity_details[
                    "reviewer_response_details"
                ] = reviewer_response_details

            activity_list.append(activity_details)
        print(
            "manager_delta_dict, reviewer_delta_dict",
            manager_delta_dict,
            reviewer_delta_dict,
        )
        test_delta_count = (
            session.query(AssessmentResponseDeltaModel)
            .order_by(desc(AssessmentResponseDeltaModel.created_on))
            .limit(5)
        )
        print("!!!!!!!!!!!!!!!!!!!!!!!! test_delta_count:", test_delta_count)
        return activity_list
