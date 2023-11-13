import imp
import json

from sqlalchemy import exc, func

from common.customExceptions import *
from common.customExceptions import (  # PathParameterNotFound,; URLAttributeNotFound,
    AnyExceptionHandler,
    AttributeIdNotFound,
)
from common.decorator import decor
from microservices.assessments.services.assessmentsService import (
    Assessment as AssessmentClass,
)
from microservices.projects.services.projectsService import Project as ProjectClass
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import (
    AssessmentResponseDelta as AssessmentResponseDeltaModel,
)
from models.checklistModels import Activity as ActivityModel
from models.checklistModels import Area as AreaModel
from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
from models.projectModels import Project as ProjectModel


class Area:
    def _checkGetAreaListParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getAreaList(self, **kwargs):
        """
        Fetch List of Areas in the system.
        Input: None
        Output: List []

        """

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
                "Assessment does not belong to the given Project!"
            )

        checklist_id = assessment.checklist_id
        areas = session.query(AreaModel).filter(
            AreaModel.checklist_id == checklist_id
        )

        # 09-March-2023 | delta count for each item | start
        area_ids = [str(a.id) for a in areas]

        area_delta_counts_manager = (
            session.query(
                SubareaModel.area_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ReviewerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ItemModel.id == ActivityModel.item_id,
                SubareaModel.id == ItemModel.subarea_id,
                SubareaModel.area_id.in_(area_ids),
            )
            .group_by(SubareaModel.area_id)
        )

        area_delta_dict_manager = {
            str(dc[0]): dc[1] for dc in area_delta_counts_manager
        }

        # delta count for each item for reviewer| start
        area_delta_counts_reviewer = (
            session.query(
                SubareaModel.area_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ManagerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ItemModel.id == ActivityModel.item_id,
                SubareaModel.id == ItemModel.subarea_id,
                SubareaModel.area_id.in_(area_ids),
            )
            .group_by(SubareaModel.area_id)
        )

        area_delta_dict_reviewer = {
            str(dc[0]): dc[1] for dc in area_delta_counts_reviewer
        }

        print("area delta count", area_delta_dict_manager, area_delta_dict_reviewer)

        # delta count for each item for reviewer| end

        areasSerializedObject = []
        # areasSerializedObject = [area.as_dict() for area in areas]

        for area in areas:
            area_dict = area.as_dict()

            # response object update for delta count | start
            if (
                any(area_delta_dict_manager)
                and str(area.id) in area_delta_dict_manager
            ):
                reviewer_delta = area_delta_dict_manager[str(area.id)]

            else:
                reviewer_delta = 0

            if (
                any(area_delta_dict_reviewer)
                and str(area.id) in area_delta_dict_reviewer
            ):
                manager_delta = area_delta_dict_reviewer[str(area.id)]

            else:
                manager_delta = 0
            # response object update for delta count |

            deltaResponseDict = {
                "delta": {
                    "reviewer_delta": manager_delta,
                    "manager_delta": reviewer_delta,
                }
            }

            area_dict["summary"] = deltaResponseDict
            areasSerializedObject.append(area_dict)

        return areasSerializedObject

    def _getAreaById(self, id):
        try:
            area = session.query(AreaModel).filter(AreaModel.id == id).first()
        except Exception:
            return None
        return area

    @decor
    def getareaDetails(self, **kwargs):
        """
        Input: dict {"pathParameters" : { "area_id": 123}}
        Output: dict containing information about area / failure message

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("area_id"):
        #     raise URLAttributeNotFound("Area Id")
        area_id = kwargs.get("pathParameters").get("area_id")
        area = self._getAreaById(area_id)
        if area is None:
            raise AttributeIdNotFound("Area")
        return {"body": area.as_dict()}
