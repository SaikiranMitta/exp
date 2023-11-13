import imp
import json
from tabnanny import check

from sqlalchemy import exc, func

from common.customExceptions import (  # PathParameterNotFound,; URLAttributeNotFound,
    AnyExceptionHandler,
    AttributeIdNotFound,
    AttributeNotPresent,
)
from common.decorator import decor
from microservices.areas.services.areasService import Area as AreaClass
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
from models.checklistModels import Checklist as ChecklistModel
from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
from models.projectModels import Project as ProjectModel


class Subarea:
    def _getSubareaById(self, id):
        try:
            subarea = (
                session.query(SubareaModel)
                .filter(SubareaModel.id == str(id))
                .first()
            )

        except Exception as ex:
            return None
        finally:
            session.close()
        return subarea

    def _checkGetSubareaListParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "area_id": "Area Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getSubareaList(self, **kwargs):
        """
        Fetch List of Areas in the system.
        Input: None
        Output: List []

        """
        self._checkGetSubareaListParameters(**kwargs)
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
        checklist_id = assessment.checklist_id
        area_id = kwargs.get("area_id")
        assessment_object = AssessmentClass()
        area_object = AreaClass()
        area = area_object._getAreaById(area_id)
        if area is None:
            raise AttributeIdNotFound("Area")
        if not str(area.checklist_id) == checklist_id:
            raise AnyExceptionHandler(
                "Given Area doesn't belong to the assessment checklist!"
            )
        subareas = session.query(SubareaModel).filter(
            SubareaModel.area_id == area_id
        )

        # 09-March-2023 | delta count for each item | start
        subarea_ids = [str(sa.id) for sa in subareas]

        subarea_delta_counts_manager = (
            session.query(
                ItemModel.subarea_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ReviewerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ItemModel.id == ActivityModel.item_id,
                ItemModel.subarea_id.in_(subarea_ids),
            )
            .group_by(ItemModel.subarea_id)
        )

        subarea_delta_dict_manager = {
            str(idc[0]): idc[1] for idc in subarea_delta_counts_manager
        }
        # 09-March-2023 | delta count for each item | end

        # 09-March-2023 | delta count for each item of reviewer| start
        subarea_delta_counts_reviewer = (
            session.query(
                ItemModel.subarea_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ManagerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ItemModel.id == ActivityModel.item_id,
                ItemModel.subarea_id.in_(subarea_ids),
            )
            .group_by(ItemModel.subarea_id)
        )

        subarea_delta_dict_reviewer = {
            str(idc[0]): idc[1] for idc in subarea_delta_counts_reviewer
        }

        # 09-March-2023 | delta count for each item of reviewer| end

        # subareasSerializedObject = [
        #     subarea.as_dict() for subarea in subareas
        # ]
        subareasSerializedObject = []
        for subarea in subareas:
            subarea_dict = subarea.as_dict()
            # if (
            #     any(subarea_delta_dict_manager)
            #     and str(subarea.id) in subarea_delta_dict_manager
            # ):
            #     deltaResponseDict = {
            #         "delta": {
            #             "reviewer_delta": subarea_delta_dict_manager[str(subarea.id)]
            #         }
            #     }
            # else:
            #     deltaResponseDict = {"delta": {"reviewer_delta": 0}}

            # response object update for delta count | start
            if (
                any(subarea_delta_dict_manager)
                and str(subarea.id) in subarea_delta_dict_manager
            ):
                reviewer_delta = subarea_delta_dict_manager[str(subarea.id)]

            else:
                reviewer_delta = 0

            if (
                any(subarea_delta_dict_reviewer)
                and str(subarea.id) in subarea_delta_dict_reviewer
            ):
                manager_delta = subarea_delta_dict_reviewer[str(subarea.id)]
            else:
                manager_delta = 0
            # response object update for delta count | end

            deltaResponseDict = {
                "delta": {
                    "reviewer_delta": manager_delta,
                    "manager_delta": reviewer_delta,
                }
            }

            subarea_dict["summary"] = deltaResponseDict
            subareasSerializedObject.append(subarea_dict)

        return subareasSerializedObject
