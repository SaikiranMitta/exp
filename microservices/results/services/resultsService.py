import imp
import json
from typing import Any

from common.customExceptions import (
    URLAttributeNotFound,
    AnyExceptionHandler,
    AttributeIdNotFound,
    InvalidAttribute,
    RequestBodyAttributeNotFound,
    RequestBodyNotFound,
)
from microservices.areas.services.areasService import Area as AreaClass
from microservices.assessments.services.assessmentsService import (
    Assessment as AssessmentClass,
)
from microservices.items.services.itemsService import Item as ItemClass
from microservices.projects.services.projectsService import Project as ProjectClass
from microservices.subareas.services.subareasService import Subarea as SubareaClass
# from models.assessmentModels import Assessment as AssessmentModel
from models.checklistModels import Area as AreaModel
# from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
# from models.projectModels import Project as ProjectModel
from models.resultModels import AssessmentItemScore as AssessmentItemScoreModel
from models.resultModels import AssessmentSubareaScore as AssessmentSubareaScoreModel
from models.resultModels import ItemGrade
# from sqlalchemy import exc

# from models.dbModels import AssessmentItemScore, ItemGrade


class Result:
    def getAssessmentResults(self, **kwargs):

        """
        Input: dict {"pathParameters" : { "project_id": 123, "assessment_id" : 234}}
        Output: dict containing information about area / failure message

        """

        if not kwargs.get("pathParameters"):
            raise URLAttributeNotFound("Path Parameters")

        if not kwargs.get("pathParameters").get("project_id"):
            raise URLAttributeNotFound("Project Id")
        project_id = kwargs.get("pathParameters").get("project_id")

        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")
        
        assessment_id = kwargs.get("pathParameters").get("assessment_id")
        assessment_object = AssessmentClass()
        assessment = assessment_object._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")

        if not assessment.status.name == "Reviewed":
            raise AnyExceptionHandler(
                "Cannot fetch results for Assessment whose status is not Reviewed!"
            )

        if not str(assessment.project_id) == project_id:
            raise AnyExceptionHandler(
                "en assessment does not belong to the given Project"
            )
        result = {}
        overall_score = assessment.overall_score
        checklist_id = assessment.checklist_id
        areas = session.query(AreaModel).filter(
            AreaModel.checklist_id == checklist_id
        )

        assessment_result_details = []

        for area in areas:
            response = {}
            area_id = str(area.id)
            response["area_id"] = area_id
            response["name"] = area.name
            response["subareas"] = []
            subareas = session.query(SubareaModel).filter(
                SubareaModel.area_id == area_id
            )

            for subarea in subareas:
                subarea_response = {}
                subarea_id = subarea.id

                subarea_id = str(subarea.id)
                subarea_response["subarea_id"] = subarea_id
                subarea_response["name"] = subarea.name

                assessment_subarea_score = session.query(AssessmentSubareaScoreModel).filter(
                    AssessmentSubareaScoreModel.subarea_id == subarea_id,
                    AssessmentSubareaScoreModel.assessment_id == assessment_id,
                ).first()
                if not assessment_subarea_score is None:
                    subarea_response[
                        "subarea_score"
                    ] = assessment_subarea_score.subarea_score
                    subarea_response[
                        "subarea_techdebt_count"
                    ] = assessment_subarea_score.subarea_techdebt_count
                    response["subareas"].append(subarea_response)
                # else:
                    # subarea_response["subarea_score"] = 13.45
                    # subarea_response["subarea_techdebt_count"] = 12
                    # response["subareas"].append(subarea_response)

            assessment_result_details.append(response)
        result["overall_score"] = overall_score
        result["assessment_result_details"] = assessment_result_details
        return {"body": (result)}

    def _fetchItemScore(self, grade, effective_weightage):

        item_score = {
            "A": effective_weightage,
            "B": effective_weightage / 2,
            "C": effective_weightage / 4,
            "D": 0,
            "NA": None,
        }

        return item_score.get(grade)

    def updateAssessmentGrades(self, **kwargs):

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()

        # if not kwargs.get("pathParameters").get("project_id"):
        #     raise URLAttributeNotFound("Project Id")
        project_id = kwargs.get("pathParameters").get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)

        if project is None:
            raise AttributeIdNotFound("Project")

        # if not kwargs.get("pathParameters").get("assessment_id"):
        #     raise URLAttributeNotFound("Assessment Id")
        assessment_id = kwargs.get("pathParameters").get("assessment_id")
        assessment_object = AssessmentClass()
        assessment = assessment_object._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")
        if not str(assessment.project_id) == str(project_id):
            raise AnyExceptionHandler(
                "Assessment does not belong to the given project!"
            )

        # if not kwargs.get("pathParameters").get("area_id"):
        #     raise URLAttributeNotFound("Area Id")
        area_id = kwargs.get("pathParameters").get("area_id")
        area_object = AreaClass()
        area = area_object._getAreaById(area_id)
        if area is None:
            raise AttributeIdNotFound("Area")

        if not str(assessment.checklist_id) == str(area.checklist_id):
            raise AnyExceptionHandler(
                "Area does not belong to the given assessment!"
            )
        # if not kwargs.get("pathParameters").get("subarea_id"):
        #     raise URLAttributeNotFound("Subarea Id")
        subarea_id = kwargs.get("pathParameters").get("subarea_id")
        subarea_object = SubareaClass()
        subarea = subarea_object._getSubareaById(subarea_id)
        if subarea is None:
            raise AttributeIdNotFound("Subarea Id")

        if not str(subarea.area_id) == area_id:
            raise AnyExceptionHandler("Subarea does not belong to the given area Id")
        # if not kwargs.get("pathParameters").get("item_id"):
        #     raise URLAttributeNotFound("Item Id")
        item_id = kwargs.get("pathParameters").get("item_id")
        item_object = ItemClass()
        item = item_object._getItemById(item_id)
        if item is None:
            raise AttributeIdNotFound("Item")
        if not str(item.subarea_id) == str(subarea_id):
            raise AnyExceptionHandler("Item does not bleong to the given subarea!")
        if not kwargs.get("body"):
            raise RequestBodyNotFound()
        body = kwargs.get("body")
        body = json.loads(body)
        # if not bool(body):
        #     raise RequestBodyNotFound()
        if not body.get("grade"):
            raise RequestBodyAttributeNotFound("Grade")

        grade = body.get("grade")
        if not ItemGrade.has_value(grade):
            raise InvalidAttribute("Item", "Grade value")
        assessment_item_score = (
            session.query(AssessmentItemScoreModel)
            .filter(
                AssessmentItemScoreModel.item_id == str(item_id),
                assessment_id == str(assessment_id),
            )
            .first()
        )

        if assessment_item_score is None:
            raise AnyExceptionHandler(
                "Assessment Item score not found for the given item and assessment Id!"
            )

        assessment_item_score.item_grade = grade
        assessment_item_score.item_score = self._fetchItemScore(
            grade, item.effective_weightage
        )
        # assessment_item_score.modified_by=" "
        # To be implemented!!
        try:
            session.add(assessment_item_score)
            session.commit()
            session.refresh()
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        return {
            "body": "Item grade modified successfully!",
        }
