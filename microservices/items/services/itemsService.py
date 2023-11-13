import imp
import json
from re import sub

from sqlalchemy import exc, func

from common.customExceptions import *
from common.decorator import decor
from microservices.areas.services.areasService import Area as AreaClass
from microservices.assessments.services.assessmentsService import (
    Assessment as AssessmentClass,
)
from microservices.projects.services.projectsService import Project as ProjectClass
from microservices.subareas.services.subareasService import Subarea as SubareaClass
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
from models.resultModels import AssessmentItemScore as AssessmentItemScoreModel
from models.resultModels import ItemGrade


class Item:
    def _getItemById(self, id):
        try:
            item = session.query(ItemModel).filter(ItemModel.id == str(id)).first()

        except Exception as ex:
            return None
        finally:
            session.close()
        return item

    def _checkGetItemListParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "area_id": "Area Id",
            "subarea_id": "Subarea Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getItemList(self, **kwargs):
        """
        Fetch List of Items in the system
        Input: { "project_id": 123, "assessment_id" : 234, "area_id" :34522, "subarea_id": 34523}
        Output: List []

        """
        self._checkGetItemListParameters(**kwargs)
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
                "Assessment does not belong to the given project"
            )

        area_id = kwargs.get("area_id")
        area_object = AreaClass()
        area = area_object._getAreaById(area_id)
        if area is None:
            raise AttributeIdNotFound("Area")
        if not str(assessment.checklist_id) == str(area.checklist_id):
            raise AnyExceptionHandler("Area does not belong to the given assessment")
        subarea_id = kwargs.get("subarea_id")
        subarea_object = SubareaClass()
        subarea = subarea_object._getSubareaById(subarea_id)
        if subarea is None:
            raise AttributeIdNotFound("Subarea")
        if not str(subarea.area_id) == str(area_id):
            raise AnyExceptionHandler("Subarea does not belong to the given area Id")

        items = session.query(ItemModel).filter(
            ItemModel.subarea_id == str(subarea_id)
        )

        # 08-March-2023 | delta count for each item | start
        item_ids = [item.id for item in items]

        item_delta_counts_manager = (
            session.query(
                ActivityModel.item_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ReviewerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ActivityModel.item_id.in_(item_ids),
            )
            .group_by(ActivityModel.item_id)
        )

        item_delta_dict_manager = {
            str(idc[0]): idc[1] for idc in item_delta_counts_manager
        }
        # 08-March-2023 | delta count for each item | end

        # delta count for reviewer on items | start
        item_delta_counts_reviewer = (
            session.query(
                ActivityModel.item_id, func.count(AssessmentResponseDeltaModel.id)
            )
            .filter(
                AssessmentResponseDeltaModel.assessment_id == str(assessment_id),
                AssessmentResponseDeltaModel.type == "ManagerDelta",
                ActivityModel.id == AssessmentResponseDeltaModel.activity_id,
                ActivityModel.item_id.in_(item_ids),
            )
            .group_by(ActivityModel.item_id)
        )

        item_delta_dict_reviewer = {
            str(idc[0]): idc[1] for idc in item_delta_counts_reviewer
        }
        # delta count for reviewer on items | start

        itemsSerializedObject = []
        for item in items:
            # item_result_object = {}
            assessment_item_score = (
                session.query(AssessmentItemScoreModel)
                .filter(
                    AssessmentItemScoreModel.item_id == str(item.id),
                    AssessmentItemScoreModel.assessment_id == str(assessment_id),
                )
                .first()
            )
            assessment_item_score_dict = assessment_item_score.as_dict()

            item_dict = item.as_dict()

            item_grade = assessment_item_score_dict.get("item_grade", None)
            item_score = assessment_item_score_dict.get("item_score", None)
            # item_result_object["id"] = str(item.id)
            # item_result_object["name"] = item.name
            # item_result_object["weightage"] = item.weightage
            # item_result_object["effective_weightage"] = item.effective_weightage
            # # item_result_object["subarea_id"] = str(item.subarea_id)
            item_dict["grade"] = item_grade
            item_dict["score"] = item_score

            # if (
            #     any(item_delta_dict_manager)
            #     and str(item.id) in item_delta_dict_manager
            # ):
            #     # item_dict["summary"] = {'delta': item_delta_dict_manager[str(item.id)]}
            #     deltaResponseDict = {
            #         "delta": {
            #             "reviewer_delta": item_delta_dict_manager[str(item.id)],
            #             "manager_delta": 0,
            #         }
            #     }
            # else:
            #     deltaResponseDict = {
            #         "delta": {"reviewer_delta": 0, "manager_delta": 0}
            #     }

            # response object update for delta count | start
            if (
                any(item_delta_dict_manager)
                and str(item.id) in item_delta_dict_manager
            ):
                reviewer_delta = item_delta_dict_manager[str(item.id)]
            else:
                reviewer_delta = 0

            if (
                any(item_delta_dict_reviewer)
                and str(item.id) in item_delta_dict_reviewer
            ):
                manager_delta = item_delta_dict_reviewer[str(item.id)]
            else:
                manager_delta = 0

            deltaResponseDict = {
                "delta": {
                    "reviewer_delta": manager_delta,
                    "manager_delta": reviewer_delta,
                }
            }
            # response object update for delta count | start

            item_dict["summary"] = deltaResponseDict
            itemsSerializedObject.append(item_dict)

        return itemsSerializedObject

    def _checkUpdateAssessmentItemGradesParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "area_id": "Area Id",
            "subarea_id": "Subarea Id",
            "item_id": "Item Id",
            "grade": "Grade ",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def updateAssessmenItemGrades(self, **kwargs):
        self._checkUpdateAssessmentItemGradesParameters(**kwargs)
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
                "Assessment does not belong to the given project"
            )
        area_id = kwargs.get("area_id")
        area_object = AreaClass()
        area = area_object._getAreaById(area_id)
        if area is None:
            raise AttributeIdNotFound("Area")

        if not str(assessment.checklist_id) == str(area.checklist_id):
            raise AnyExceptionHandler("Area does not belong to the given assessment")
        subarea_id = kwargs.get("subarea_id")
        subarea_object = SubareaClass()
        subarea = subarea_object._getSubareaById(subarea_id)
        if subarea is None:
            raise AttributeIdNotFound("Subarea Id")

        if not str(subarea.area_id) == area_id:
            raise AnyExceptionHandler("Subarea does not belong to the given area Id")
        item_id = kwargs.get("item_id")
        item = self._getItemById(item_id)
        if item is None:
            raise AttributeIdNotFound("Item")
        if not str(item.subarea_id) == str(subarea_id):
            raise AnyExceptionHandler("Item does not belong to the given subarea")
        grade = kwargs.get("grade")
        if not ItemGrade.has_value(grade):
            raise InvalidAttribute("Item", "Grade value")
        assessment_item_score = (
            session.query(AssessmentItemScoreModel)
            .filter(
                AssessmentItemScoreModel.item_id == str(item_id),
                AssessmentItemScoreModel.assessment_id == str(assessment_id),
            )
            .first()
        )

        if assessment_item_score is None:
            raise AnyExceptionHandler(
                "Assessment Item score not found for the given item and assessment Id"
            )

        assessment_item_score.item_grade = grade
        # assessment_item_score.modified_by=" "
        # To be implemented!!
        try:
            session.add(assessment_item_score)
            session.commit()
            session.refresh(assessment_item_score)

        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return assessment_item_score.as_dict()
