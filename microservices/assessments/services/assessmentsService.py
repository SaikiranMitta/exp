import calendar
import enum
import json
import os
from datetime import datetime

import boto3
from sqlalchemy import desc, exc, func

from common.customExceptions import (  # PathParameterNotFound,; URLAttributeNotFound,
    AnyExceptionHandler,
    AttributeIdNotFound,
    AttributeNotPresent,
    IncorrectFormat,
    InvalidAttribute,
)
from common.decorator import decor

# from microservices.projects.services.projectsService import Project
from microservices.projects.services.projectsService import Project as ProjectClass
from microservices.users.services.usersService import User as UserClass
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import (
    AssessmentResponseDelta as AssessmentResponseDeltaModel,
)
from models.assessmentModels import AssessmentStatus
from models.assessmentModels import GradeCalculationTask as GradeCalculationTaskModel
from models.assessmentModels import Response as ResponseModel
from models.assessmentModels import ResponseType, ResponseValue
from models.checklistModels import Activity as ActivityModel
from models.checklistModels import Area as AreaModel
from models.checklistModels import Checklist as ChecklistModel
from models.checklistModels import Item as ItemModel
from models.checklistModels import Subarea as SubareaModel
from models.database.dbConnection import session
from models.projectModels import Frequency as AuditFrequency
from models.projectModels import Project as ProjectModel
from models.resultModels import AssessmentItemScore as AssessmentItemScoreModel
from models.resultModels import AssessmentSubareaScore as AssessmentSubareaScoreModel


class Assessment:
    def _validateDate(self, date_text):
        try:
            datetime.strptime(date_text, "%Y-%m-%d")
            return True
        except ValueError:
            return None
            # return {
            #     "statusCode": 400,
            #     "body": "Incorrect date format! Date format should be YYYY-MM-DD",
            # }

    def _getChecklistById(self, id):
        try:
            checklist = (
                session.query(ChecklistModel)
                .filter(ChecklistModel.id == str(id))
                .first()
            )

        except exc.DataError as de:
            return None
        return checklist

    def _getAssessmentById(self, id):
        try:
            assessment = (
                session.query(AssessmentModel)
                .filter(AssessmentModel.id == str(id))
                .first()
            )

        except Exception as ex:
            print("Error getting assessment", ex)
            return None
        # finally:
        #     session.close()
        return assessment

    def _checkGetAssessmentListParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def _groupBySummaryDict(self, data, summary_json={}):
        if data:
            _total = 0
            for k, v in data:
                _key = k
                if k:
                    _key = k.value
                if _key == "NA":
                    _key = "n/a"
                else:
                    _key = str(_key).lower()
                summary_json[_key] = v
                _total += v
            summary_json["total"] = _total
        return summary_json

    @decor
    def getAssessmentList(self, **kwargs):
        """
        Fetch List of Assessments in the system.
        Input: None
        Output: List []


        """
        self._checkGetAssessmentListParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project Id")
        assessment_status = []
        assessments = session.query(AssessmentModel).filter(
            AssessmentModel.project_id == project_id
        )

        if kwargs["status"]:
            status = kwargs["status"]
            assessments = assessments.filter(AssessmentModel.status == status)

        # 16-March-2023 | delta count for each area | Start
        assessment_ids = [str(a.id) for a in assessments]
        all_assessment_delta_counts = (
            session.query(
                AssessmentResponseDeltaModel.assessment_id,
                func.count(AssessmentResponseDeltaModel.id),
            )
            .filter(
                AssessmentResponseDeltaModel.type == "ReviewerDelta",
                AssessmentResponseDeltaModel.assessment_id.in_(assessment_ids),
            )
            .group_by(AssessmentResponseDeltaModel.assessment_id)
        )

        assessment_delta_dict = {dc[0]: dc[1] for dc in all_assessment_delta_counts}
        # 16-March-2023 | delta count for each area | End

        assessments = assessments.all()
        result = {}
        _assessments = []
        # [assessment.as_dict() for assessment in assessments]

        for assessment in assessments:
            print("Iterating assessments", assessment.as_dict())
            _assessment_dict = assessment.as_dict()
            _assessment_dict["calculation_task_id"] = None
            _assessment_dict["calculation_task_status"] = False
            task = self._getActiveTask(assessment.id)
            if task:
                _assessment_dict["calculation_task_id"] = str(task.id)
                _assessment_dict["calculation_task_status"] = task.status
            _manager_response = (
                session.query(ResponseModel.value, func.count("*"))
                .filter(
                    ResponseModel.assessment_id == str(assessment.id),
                    ResponseModel.type == "ManagerResponse",
                )
                .group_by(ResponseModel.value)
                .all()
            )

            _reviewer_response = (
                session.query(ResponseModel.value, func.count("*"))
                .filter(
                    ResponseModel.assessment_id == str(assessment.id),
                    ResponseModel.type == "ReviewerResponse",
                )
                .group_by(ResponseModel.value)
                .all()
            )

            activity_dict = {"activities": {}}

            manager_dict = {"yes": 0, "no": 0, "n/a": 0, "total": 0}
            reviewer_dict = {"yes": 0, "no": 0, "n/a": 0, "total": 0}
            if _manager_response:
                manager_dict = self._groupBySummaryDict(
                    _manager_response, manager_dict
                )
            if _reviewer_response:
                reviewer_dict = self._groupBySummaryDict(
                    _reviewer_response, reviewer_dict
                )

            activity_dict["activities"]["manager"] = manager_dict
            activity_dict["activities"]["reviewer"] = reviewer_dict

            # 16-March-2023 | delta count for each area | Start
            if (
                any(assessment_delta_dict)
                and str(assessment.id) in assessment_delta_dict
            ):
                deltaResponseDict = {
                    "delta": {
                        "reviewer_delta": assessment_delta_dict[str(assessment.id)]
                    }
                }
            else:
                deltaResponseDict = {"delta": {"reviewer_delta": 0}}
            activity_dict.update(deltaResponseDict)
            # 16-March-2023 | delta count for each area | End

            _assessment_dict["summary"] = activity_dict

            _assessments.append(_assessment_dict)

        result["data"] = _assessments

        try:
            summary = (
                session.query(AssessmentModel.status, func.count("*"))
                .group_by(AssessmentModel.status)
                .all()
            )

            result["summary"] = self._groupBySummaryDict(summary)

        except Exception as e:
            print(e)

        return result

    def _checkGetAssessmentDetailsParameters(self, **kwargs):
        required_parameters = {
            "project_id": "Project Id",
            "assessment_id": "Assessment Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def getAssessmentDetails(self, **kwargs):
        """
        Fetch details of Assessments in the system.
        Input: { "assessment_id": 123, "project_id":4434}
        Output: {} dict containing details of assessment/failure message

        """
        self._checkGetAssessmentDetailsParameters(**kwargs)
        project_id = kwargs.get("project_id")
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")

        result = {}
        _assessments = []

        assessment_id = kwargs.get("assessment_id")
        assessment = self._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")
        if not str(assessment.project_id) == project_id:
            raise AnyExceptionHandler(
                "Assessment does not belong to the given project!"
            )

        _assessment_dict = assessment.as_dict()
        _assessment_dict["calculation_task_id"] = None
        _assessment_dict["calculation_task_status"] = False
        task = self._getActiveTask(assessment.id)
        if task:
            _assessment_dict["calculation_task_id"] = str(task.id)
            _assessment_dict["calculation_task_status"] = task.status
        _manager_response = (
            session.query(ResponseModel.value, func.count("*"))
            .filter(
                ResponseModel.assessment_id == str(assessment.id),
                ResponseModel.type == "ManagerResponse",
            )
            .group_by(ResponseModel.value)
            .all()
        )

        _reviewer_response = (
            session.query(ResponseModel.value, func.count("*"))
            .filter(
                ResponseModel.assessment_id == str(assessment.id),
                ResponseModel.type == "ReviewerResponse",
            )
            .group_by(ResponseModel.value)
            .all()
        )

        activity_dict = {"activities": {}}

        manager_dict = {"yes": 0, "no": 0, "n/a": 0, "total": 0}
        reviewer_dict = {"yes": 0, "no": 0, "n/a": 0, "total": 0}
        if _manager_response:
            manager_dict = self._groupBySummaryDict(_manager_response, manager_dict)
        if _reviewer_response:
            reviewer_dict = self._groupBySummaryDict(
                _reviewer_response, reviewer_dict
            )

        activity_dict["activities"]["manager"] = manager_dict
        activity_dict["activities"]["reviewer"] = reviewer_dict
        _assessment_dict["summary"] = activity_dict

        # 11 March 2023 | reviewerDelta
        assessment_reviewer_delta = self._getAssessmentReviewerDelta(assessment)
        assessment_manager_delta = self._getAssessmentManagerDelta(assessment)

        _assessment_dict["summary"]["delta"] = {
            "reviewer_delta": assessment_reviewer_delta,
            "manager_delta": assessment_manager_delta,
        }

        _assessments.append(_assessment_dict)

        result["data"] = _assessments

        return result

    def _createAssessmentItemScoreRecords(self, items, assessment, **kwargs):
        authenticated_user_id = kwargs.get("authenticated_user_id")

        assessment_item_scores = []
        for item in items:
            assessment_item_score = AssessmentItemScoreModel()
            assessment_item_score.item_id = str(item.id)
            assessment_item_score.assessment_id = str(assessment.id)
            assessment_item_score.created_by = authenticated_user_id
            assessment_item_scores.append(assessment_item_score)
        try:
            session.add_all(assessment_item_scores)
            session.commit()
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)

    def _generateStartAndEndDate(self, audit_frequency, **kwargs):
        # tz_NY = pytz.timezone("Asia/Kolkata")
        current_date = datetime.now()
        if audit_frequency == "Monthly":
            if current_date.date().day > 1:
                try:
                    next_month_date = current_date.replace(
                        month=current_date.month + 1, day=1
                    ).date()
                except ValueError:
                    if current_date.month == 12:
                        next_month_date = current_date.replace(
                            year=current_date.year + 1, month=1, day=1
                        ).date()
            else:
                next_month_date = current_date.date()
            start_date = next_month_date
            end_date = current_date.replace(
                day=calendar.monthrange(next_month_date.year, next_month_date.month)[
                    1
                ]
            ).date()
        elif audit_frequency == "Annually":
            if current_date.date().day() > 1:
                next_year_date = current_date.replace(
                    year=current_date.year + 1, day=1, month=1
                ).date()
                start_date = next_year_date
                end_date = start_date.replace(month=12, day=31)
            elif current_date.date().day() == 1 and current_date.date().month == 1:
                start_date = current_date.date()
                end_date = start_date.replace(month=12, day=31)
            elif current_date.date().day() == 1 and current_date.date().month > 1:
                next_year_date = current_date.replace(
                    year=current_date.year + 1, day=1, month=1
                ).date()
                start_date = next_year_date
                end_date = start_date.replace(month=12, day=31)
        elif audit_frequency == "HalfYearly":
            if current_date.date().month > 1 and current_date.date().month < 7:
                start_date = current_date.replace(
                    year=current_date.year, month=7, day=1
                ).date()

                end_date = current_date.replace(
                    year=current_date.year, month=12, day=31
                ).date()
            elif current_date.date().month == 1 and current_date.date().day == 1:
                start_date = current_date.date()
                end_date = current_date.replace(month=6, day=30).date()

            elif current_date.date().month == 7 and current_date.date().day == 1:
                start_date = current_date.date()
                end_date = current_date.replace(month=12, day=31).date()
            elif current_date.date().month > 7:
                start_date = current_date.replace(
                    year=current_date.year + 1, month=1, day=1
                ).date()
                end_date = current_date.replace(month=6, day=30).date()
        elif audit_frequency == "Quarterly":
            dict = {[1, 2, 3]: 4, [4, 5, 6]: 7, [7, 8, 9]: 10}
            if current_date.date().day > 1 and current_date.date().month < 10:
                current_month = current_date.date().month
                for key, value in dict.items():
                    if current_month in key:
                        start_date_month = value
                        break
                start_date = current_date.replace(
                    month=start_date_month,
                    day=1,
                    year=current_date.year,
                ).date()
                end_date = current_date.replace(
                    day=calendar.monthrange(start_date.year, start_date.month)[1],
                    year=start_date.year,
                    month=start_date.month + 2,
                ).date()

            if current_date.date().month >= 10 and current_date.date().day > 1:
                start_date = current_date.replace(
                    year=current_date.year + 1, month=1, day=1
                ).date()
                end_date = start_date.replace(day=31, month=3, year=start_date.year)
            if current_date.date().month == 10 and current_date.date().date == 1:
                start_date = current_date.date()
                end_date = start_date.replace(
                    year=start_date.year, month=start_date + 2, day=31
                )
            if current_date.date().month == 1 and current_date.date().date == 1:
                start_date = current_date.date()
                end_date = start_date.replace(
                    year=start_date.year,
                    month=start_date.month + 2,
                    day=31,
                )
            elif current_date.date().month == 4 and current_date.date().day == 1:
                start_date = current_date.date()
                end_date = start_date.replace(
                    year=start_date.year,
                    month=start_date.month + 2,
                    day=30,
                )
            elif current_date.date().month == 7 and current_date.date().day == 1:
                start_date = current_date.date()
                end_date = start_date.replace(
                    year=start_date.year,
                    month=start_date.month + 2,
                    day=30,
                )
            return start_date, end_date

    # @decor
    def createAssessmentParameters(self, **kwargs):
        assessment_creation_parameters = {}
        checklist = (
            session.query(ChecklistModel)
            .filter(ChecklistModel.status == "Published")
            .order_by(ChecklistModel.created_on.desc())
            .first()
        )
        if checklist is None:
            raise AnyExceptionHandler("No active checklist found!")
        checklist_id = str(checklist.id)
        projects = session.query(ProjectModel.id).all()
        projects_assessments = session.query(Assessment.project_id).filter(
            Assessment.project_id.in_(projects)
        )

        for project_assessment in projects_assessments:
            project_id = project_assessment.project_id
            audit_frequency = project_assessment.audit_frequency.name
            start_date, end_date = self._generateStartAndEndDate(audit_frequency)
            assessment_creation_parameters["start_date"] = start_date
            assessment_creation_parameters["end_date"] = end_date
            assessment_creation_parameters["checklist_id"] = checklist_id
            assessment_creation_parameters["project_id"] = project_id
            self.createAssessment(assessment_creation_parameters)

    def _storeReviewerDelta(self, assessment):
        # getting previously reviewed assessment if any
        previous_assessment = (
            session.query(AssessmentModel)
            .filter(
                AssessmentModel.project_id == assessment.project_id,
                AssessmentModel.status == "Reviewed",
            )
            .order_by(desc(AssessmentModel.start_date))
            .limit(1)[0:1]
        )

        if not previous_assessment:
            print("No prev assessment found")
            pass
        else:
            previous_assessment = previous_assessment[0]
            # getting manager responses for respective assessment's activities
            previous_assessment_manager_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id == str(previous_assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

            # getting reviever responses for respective assessment's activities
            previous_assessment_reviewer_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id == str(previous_assessment.id),
                ResponseModel.type == "ReviewerResponse",
            )

            manager_responses_dict = {
                x.activity_id: [x.value, x.comments]
                for x in previous_assessment_manager_responses
            }
            reviewer_responses_dict = {
                x.activity_id: [x.value, x.comments]
                for x in previous_assessment_reviewer_responses
            }

            final_delta_list = []

            for mr_key, mr_val in manager_responses_dict.items():
                if mr_key in reviewer_responses_dict:
                    if str(mr_val[0]) != str(reviewer_responses_dict[mr_key][0]):
                        # creating delta model object for reviever value record
                        delta_model = AssessmentResponseDeltaModel()
                        delta_model.previous_assessment_id = str(
                            previous_assessment.id
                        )

                        if isinstance(reviewer_responses_dict[mr_key][0], enum.Enum):
                            reviewer_responses_dict[mr_key][
                                0
                            ] = reviewer_responses_dict[mr_key][0].value
                        delta_model.previous_value = str(
                            reviewer_responses_dict[mr_key][0]
                        )

                        if type(reviewer_responses_dict[mr_key][1]) in [type(None)]:
                            reviewer_responses_dict[mr_key][1] = ""
                        delta_model.previous_comments = str(
                            reviewer_responses_dict[mr_key][1]
                        )

                        delta_model.type = "ReviewerDelta"
                        delta_model.activity_id = str(mr_key)
                        delta_model.assessment_id = str(assessment.id)
                        delta_model.created_by = str(assessment.created_by)

                        final_delta_list.append(delta_model)

            if final_delta_list:
                try:
                    session.add_all(final_delta_list)
                    session.commit()
                    print(f"_getAssessmentDelta Delta added")
                except Exception as ex:
                    session.rollback()
                    print(f"_getAssessmentDelta exception block")
                    raise AnyExceptionHandler(ex)

    def _storeManagerDelta(self, assessment):
        # getting previously submitted assessment
        print("GOT THE ASSESSMENT assessment:", assessment)
        previous_assessment = (
            session.query(AssessmentModel)
            .filter(
                AssessmentModel.project_id == assessment.project_id,
                AssessmentModel.status.in_(["Submitted", "UnderReview", "Reviewed"]),
            )
            .order_by(desc(AssessmentModel.start_date))
            .limit(1)[0:1]
        )
        print("GOT THE ASSESSMENT previous_assessment:", previous_assessment)

        if not previous_assessment:
            print("No prev assessment found")
            pass
        else:
            previous_assessment = previous_assessment[0]
            # getting manager responses for previous assessment's activities
            previous_assessment_manager_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id == str(previous_assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

            print(
                "previous_assessment_manager_responses",
                previous_assessment_manager_responses,
            )
            # getting managers responses for current assessment's activities
            current_assessment_manager_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id == str(assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

            print(
                "current_assessment_manager_responses",
                current_assessment_manager_responses,
            )
            previous_assessment_manager_responses_dict = {
                x.activity_id: [x.value, x.comments]
                for x in previous_assessment_manager_responses
            }
            current_assessment_manager_responses_dict = {
                x.activity_id: [x.value, x.comments]
                for x in current_assessment_manager_responses
            }

            final_delta_list = []

            for mr_key, mr_val in previous_assessment_manager_responses_dict.items():
                if mr_key in current_assessment_manager_responses_dict:
                    if (str(mr_val[0]) != str(current_assessment_manager_responses_dict[mr_key][0])) or (mr_val[1] != current_assessment_manager_responses_dict[mr_key][1]):
                        # creating delta model object for managers value record
                        delta_model = AssessmentResponseDeltaModel()
                        delta_model.previous_assessment_id = str(
                            previous_assessment.id
                        )

                        if isinstance(
                            previous_assessment_manager_responses_dict[mr_key][0],
                            enum.Enum,
                        ):
                            previous_assessment_manager_responses_dict[mr_key][
                                0
                            ] = previous_assessment_manager_responses_dict[mr_key][
                                0
                            ].value
                        delta_model.previous_value = str(
                            previous_assessment_manager_responses_dict[mr_key][0]
                        )

                        if type(
                            previous_assessment_manager_responses_dict[mr_key][1]
                        ) in [type(None)]:
                            previous_assessment_manager_responses_dict[mr_key][
                                1
                            ] = ""
                        delta_model.previous_comments = str(
                            previous_assessment_manager_responses_dict[mr_key][1]
                        )

                        delta_model.type = "ManagerDelta"
                        delta_model.activity_id = str(mr_key)
                        delta_model.assessment_id = str(assessment.id)
                        delta_model.created_by = str(assessment.created_by)

                        final_delta_list.append(delta_model)

            if final_delta_list:
                try:
                    print("final_delta_list", final_delta_list)
                    session.add_all(final_delta_list)
                    session.commit()
                    print(f"_getAssessmentDelta Delta added")
                except Exception as ex:
                    session.rollback()
                    print(f"_getAssessmentDelta exception block")
                    raise AnyExceptionHandler(ex)

    def _checkCreateAssessmentParameters(self, **kwargs):
        required_parameters = {
            "start_date": "Start date",
            "end_date": "End date",
            "project_id": "Project Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def createAssessment(self, **kwargs):
        """
        Create Assessment
        Input: {"project_id": 23-442,  "start_date", "end_date",}
        Output: {} containing created assessment details/ failure dictionary

        """
        self._checkCreateAssessmentParameters(**kwargs)
        project_id = kwargs.get("project_id")
        start_date = kwargs.get("start_date")
        end_date = kwargs.get("end_date")

        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")

        if not project.is_active:
            raise AnyExceptionHandler(
                "Project is Inactive. Cannot create assessment for an inactive project!"
            )
        # fetch active checklist
        checklist = session.query(ChecklistModel).filter(
            ChecklistModel.is_active == True
        )
        if checklist is None:
            raise AnyExceptionHandler("No active checklist in the system")
        checklist = checklist.first()
        checklist_id = str(checklist.id)

        if not checklist.status.name == "Published":
            raise AnyExceptionHandler(
                "Cannot create assessment with a checklist which is not published!"
            )

        date = self._validateDate(start_date)
        if date is None:
            raise IncorrectFormat("Start date")
        date = self._validateDate(end_date)
        if date is None:
            raise IncorrectFormat("End date")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        status = "ToDo"

        og_name = self._createAssessmentName(project, start_date)
        name = og_name
        existing_assessment = self._getAssessmentByNameProject(name, project_id)
        counter = 1
        while existing_assessment:
            name = f"{og_name}_{str(counter)}"
            existing_assessment = self._getAssessmentByNameProject(name, project_id)
            counter += 1

        assessment = AssessmentModel()
        assessment.name = name
        assessment.project_id = project_id
        assessment.checklist_id = checklist_id
        assessment.status = status
        assessment.start_date = start_date
        assessment.end_date = end_date
        assessment.created_by = authenticated_user_id

        # getting prev assessment if any
        previous_assessment = (
            session.query(AssessmentModel)
            .filter(
                AssessmentModel.project_id == str(assessment.project_id),
            )
            .order_by(desc(AssessmentModel.start_date))
            .limit(1)[0:1]
        )

        previous_assessment_manager_responses = False
        previous_assessment_reviewer_responses = False

        if not previous_assessment:
            pass
        else:
            previous_assessment = previous_assessment[0]

            # getting prev assessment Reviewer responses if any
            previous_assessment_reviewer_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
                ResponseModel.assessment_id,
                ResponseModel.type,
            ).filter(
                ResponseModel.assessment_id == str(previous_assessment.id),
                ResponseModel.type == "ReviewerResponse",
            )

            # getting prev assessment manager responses for all activities
            previous_assessment_manager_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
                ResponseModel.assessment_id,
                ResponseModel.type,
            ).filter(
                ResponseModel.assessment_id == str(previous_assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

        try:
            session.add(assessment)
            session.commit()
            session.refresh(assessment)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        # Delta creation logic is flawed
        # responsedetla DB fields is to be rechecked
        self._storeReviewerDelta(assessment)
        print("Delta added")
        # Response creation

        areas = (
            session.query(AreaModel.id)
            .filter(AreaModel.checklist_id == checklist_id)
            .distinct()
        )
        subareas = (
            session.query(SubareaModel.id)
            .filter(SubareaModel.area_id.in_(areas))
            .distinct()
        )
        items = (
            session.query(ItemModel.id)
            .filter(ItemModel.subarea_id.in_(subareas))
            .distinct()
        )
        activities = session.query(ActivityModel.id).filter(
            ActivityModel.item_id.in_(items)
        )

        self._createAssessmentItemScoreRecords(items, assessment, **kwargs)

        responses = []
        for activity in activities:
            reviewer_response = ResponseModel()
            reviewer_response.assessment_id = str(assessment.id)
            reviewer_response.activity_id = str(activity.id)
            reviewer_response.created_by = str(authenticated_user_id)
            reviewer_response.type = "ReviewerResponse"

            # filtering previous assessment responses as per activity id
            if not previous_assessment_reviewer_responses:
                pass
            else:
                previous_activity_reviewer_response = (
                    previous_assessment_reviewer_responses.filter(
                        ResponseModel.activity_id == str(activity.id)
                    ).first()
                )

                # dumping previous assesments' activities manager's response to new object
                if previous_activity_reviewer_response is not None:
                    reviewer_response.comments = (
                        previous_activity_reviewer_response.comments
                    )
                    reviewer_response.value = (
                        previous_activity_reviewer_response.value
                    )

            manager_response = ResponseModel()
            manager_response.assessment_id = str(assessment.id)
            manager_response.activity_id = str(activity.id)
            manager_response.created_by = str(authenticated_user_id)
            manager_response.type = "ManagerResponse"

            # filtering previous assessment responses as per activity id
            if not previous_assessment_manager_responses:
                pass
            else:
                previous_activity_manager_response = (
                    previous_assessment_manager_responses.filter(
                        ResponseModel.activity_id == str(activity.id)
                    ).first()
                )

                # dumping previous assesments' activities manager's response to new object
                if previous_activity_manager_response is not None:
                    manager_response.comments = (
                        previous_activity_manager_response.comments
                    )
                    manager_response.value = previous_activity_manager_response.value

            responses.extend(
                [
                    manager_response,
                    reviewer_response,
                ]
            )
        try:
            session.add_all(responses)
            session.commit()
            session.refresh(assessment)
            kwargs["assessment_id"] = str(assessment.id)
            # createAssessment event published to assessmentCreatedTopic
            client = boto3.client("sns")
            published_message = client.publish(
                TargetArn=os.getenv("SNS_ASSESSMENT_CREATED_ARN"),
                Message=json.dumps(assessment.as_dict()),
            )
            print("createAssessment published_message:: ", published_message)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return assessment.as_dict()

    def _fetchItemScore(self, grade, effective_weightage):
        # item_object = ItemClass()
        # item = item_object._getItemById(item_id)
        # if item is None:
        #     raise AttributeIdNotFound("Item")
        # effective_weightage = item.effective_weightage
        item_score = {
            "A": effective_weightage,
            "B": effective_weightage / 2,
            "C": effective_weightage / 4,
            "D": 0,
            "NA": None,
        }

        return item_score.get(grade)

    def _addSubareaScore(self, subarea_item_score):
        assessment_denominator = float(0)
        assessment_overall_score = float(0)
        print("Sub area item score", subarea_item_score)
        for (
            key,
            values,
        ) in subarea_item_score.items():
            numerator = float(0)
            denominator = float(0)
            for value in values:
                print("value -- value" * 30)
                print(value)

                if not (value[0] == "NA" or value[0] == None):
                    numerator += float(value[0])
                    denominator += float(value[1])

                print(numerator)
                print(denominator)
            if denominator == float(0):
                subarea_item_score[key] = None
            else:
                subarea_item_score[key] = (numerator / denominator) * 100

            if (
                not subarea_item_score[key] == "NA"
                and not subarea_item_score[key] == None
            ):
                assessment_overall_score += subarea_item_score[key]
                assessment_denominator += 1
        if assessment_denominator == 0:
            assessment_overall_score = 100
        else:
            assessment_overall_score = (
                assessment_overall_score / assessment_denominator
            )
            assessment_overall_score = round(assessment_overall_score, 2)

        return {
            "overall_score": assessment_overall_score,
            "subarea_item_score": subarea_item_score,
        }

    def gradeCalculator(self, **kwargs):
        assessment_id = kwargs.get("assessment_id")
        assessment = self._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")
        checklist_id = str(assessment.checklist_id)
        responseType = None
        if kwargs["status"] == "Submitted":
            responseType = "ManagerResponse"
            (
                responses,
                responses_comments_none,
            ) = self._checkIfAssessmentComplete(assessment_id, responseType)
            # set null respone by manager to NO
            print("Assessment completion checked")
            if responses:
                for _response in responses:
                    _response.value = ResponseValue.No
                    try:
                        session.add(_response)
                        session.commit()
                        session.refresh(_response)
                    except Exception as ex:
                        session.rollback()
                        raise AnyExceptionHandler(ex)

        elif kwargs["status"] == "Reviewed":
            responseType = "ReviewerResponse"
            (
                responses,
                responses_comments_none,
            ) = self._checkIfAssessmentComplete(assessment_id, responseType)

            print("Assessment completion checked")
            for response in responses:
                self._defaultReviewerActivityRespone(response)
            session.commit()
            print("setting default reviewer activity reponse to managers response")
            # contains all the responses for reviewers

        areas = session.query(AreaModel.id).filter(
            AreaModel.checklist_id == checklist_id
        )
        subareas = session.query(SubareaModel.id).filter(
            SubareaModel.area_id.in_(areas)
        )
        # for subarea in subareas:
        #     items = session.query(ItemModel).filter(SubareaModel.id == subarea.id)
        items = session.query(ItemModel).filter(ItemModel.subarea_id.in_(subareas))
        subarea_techdebt_count = {}
        subarea_item_score = {}
        assessment_item_scores = []
        print("Meta data Pulled")
        for item in items:
            print("Iterating Item")
            activities = session.query(ActivityModel).filter(
                ActivityModel.item_id == str(item.id)
            )

            is_mimh = False
            is_mh = False
            is_gh = False
            na_count = 0
            assessment_id = str(assessment_id)
            # subarea_techdebt_count[str(item.subarea_id)] = 0
            subarea_item_score[str(item.subarea_id)] = []
            no_count = 0
            for activity in activities:
                print("Iterating Activity in Item")
                activity_id = str(activity.id)

                response = (
                    session.query(ResponseModel)
                    .filter(
                        ResponseModel.activity_id == activity_id,
                        ResponseModel.type == responseType,
                        ResponseModel.assessment_id == assessment_id,
                    )
                    .first()
                )

                # print(response.type)
                # print(response.type.name)
                if response.value.name == "No":
                    # subarea_techdebt_count[str(item.subarea_id)] = (
                    #     subarea_techdebt_count[str(item.subarea_id)]
                    #     + 1
                    # )
                    # print("activity_importance--" * 8)
                    # print(activity.importance)
                    # print(activity_id)
                    # print("Noooooo")
                    no_count += 1
                    activity_id = response.activity_id
                    activity = (
                        session.query(ActivityModel)
                        .filter(
                            ActivityModel.id == str(activity_id),
                        )
                        .first()
                    )
                    # Most Important Must Have
                    if activity.importance.name == "MIMH":
                        is_mimh = True
                    # Must Have
                    elif activity.importance.name == "MH":
                        is_mh = True
                    # Good to Have
                    elif activity.importance.name == "GH":
                        is_gh = True
                if response.value.name == "NA":
                    na_count += 1

            if na_count == activities.count():
                grade = "NA"
            elif no_count == activities.count():
                grade = "D"
            elif no_count + na_count == activities.count():
                grade = "D"
            elif is_mimh:
                grade = "D"
            elif is_mh:
                grade = "C"
            elif is_gh:
                grade = "B"
            else:
                grade = "A"
            assessment_item_score = (
                session.query(AssessmentItemScoreModel)
                .filter(
                    AssessmentItemScoreModel.assessment_id == str(assessment.id),
                    AssessmentItemScoreModel.item_id == str(item.id),
                )
                .first()
            )

            # if not exist create
            if not assessment_item_score:
                assessment_item_score = AssessmentItemScoreModel()
            assessment_item_score.assessment_id = str(assessment.id)
            assessment_item_score.item_id = str(item.id)
            assessment_item_score.item_grade = grade
            assessment_item_score.item_score = self._fetchItemScore(
                grade,
                item.effective_weightage,
            )

            assessment_item_scores.append(assessment_item_score)
        task = self._getGradeCalculationTaskById(kwargs["grade_calculation_task_id"])
        print("Grade Calculation Task", task.as_dict())
        try:
            session.add_all(assessment_item_scores)
            session.commit()
            if kwargs["status"] == "Reviewed":
                self._addAndCheckAssessmentItemGrades(assessment)
                assessment.tech_debt = self._calculateTechDebt(assessment_id)
            print("Overall Score", assessment.overall_score)
            task.status = True
            session.commit()
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()

        # subarea_item_score[str(item.subarea_id)].append(
        #     (
        #         assessment_item_score.item_score,
        #         item.effective_weightage,
        #     )
        # )
        # print("Inside Assessment subarea score-- *" * 8)
        # result_dict = self._addSubareaScore(subarea_item_score)
        # print("Outside Assessment subarea score-- *" * 8)
        # print(result_dict)
        # assessment_subareas = []
        # for key, value in result_dict.get(
        #     "subarea_item_score"
        # ).items():

        #     assessment_subarea_score = AssessmentSubareaScoreModel()
        #     assessment_subarea_score.subarea_id = str(key)
        #     # assessment_subarea_score.subarea_score = value
        #     assessment_subarea_score.assessment_id = str(
        #         assessment.id
        #     )
        #     assessment_subarea_score.subarea_techdebt_count = (
        #         subarea_techdebt_count[key]
        #     )
        #     assessment_subareas.append(assessment_subarea_score)
        # try:
        #     session.add_all(assessment_subareas)

        #     session.commit()
        # except Exception as ex:
        #     session.rollback()
        #     raise AnyExceptionHandler(ex)
        # finally:
        #     session.close()

    def _checkIfAssessmentComplete(self, assessment_id, type):
        responses = session.query(ResponseModel).filter(
            ResponseModel.assessment_id == str(assessment_id),
            ResponseModel.value == None,
            ResponseModel.type == type,
        )

        responses_comments_none = session.query(ResponseModel).filter(
            ResponseModel.assessment_id == str(assessment_id),
            ResponseModel.value in ["Yes", "NA"],
            ResponseModel.comments == None,
            ResponseModel.type == type,
        )
        return responses, responses_comments_none

    def _addItemScore(
        self,
        assessment_item_score,
        grade,
        effective_weightage,
        **kwargs,
    ):
        assessment_item_score.item_score = self._fetchItemScore(
            grade, effective_weightage
        )

        try:
            session.commit()
            session.refresh(assessment_item_score)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)

    def _getTechdebtCount(self, subarea_id, assessment_id):
        subareas = session.query(SubareaModel.id).filter(
            SubareaModel.id == str(subarea_id)
        )
        items = session.query(ItemModel.id).filter(
            ItemModel.subarea_id.in_(subareas)
        )

        activities = session.query(ActivityModel.id).filter(
            ActivityModel.item_id.in_(items)
        )

        manager_responses = session.query(ResponseModel.id).filter(
            ResponseModel.activity_id.in_(activities),
            ResponseModel.assessment_id == str(assessment_id),
            ResponseModel.type == "ReviewerResponse",
            ResponseModel.value == "No",
        )[:]

        techdebtCount = len(manager_responses)
        return techdebtCount

    def _defaultReviewerActivityRespone(self, reviewer_response):
        # get manager response value for the same activity id

        manager_response = (
            session.query(ResponseModel)
            .filter(
                ResponseModel.activity_id == str(reviewer_response.activity_id),
                ResponseModel.assessment_id == str(reviewer_response.assessment_id),
                ResponseModel.type == "ManagerResponse",
            )
            .first()
        )
        _manager_response_dict = manager_response.as_dict()
        _manager_response_value = _manager_response_dict["value"]

        # update the null respone value of reviewer_response to the manager_response value
        reviewer_response.value = _manager_response_value
        try:
            session.add(reviewer_response)
            # session.refresh(reviewer_response)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)

    def _addAndCheckAssessmentItemGrades(self, assessment, **kwargs):
        assessment_id = assessment.id
        checklist_id = assessment.checklist_id
        areas = session.query(AreaModel.id).filter(
            AreaModel.checklist_id == str(checklist_id)
        )
        subareas = session.query(SubareaModel.id).filter(
            SubareaModel.area_id.in_(areas)
        )
        items = session.query(ItemModel).filter(ItemModel.subarea_id.in_(subareas))
        assessment_item_scores = []
        subarea_item_score = {}
        print("Before items loop")
        for item in items:
            if not subarea_item_score.get(str(item.subarea_id)):
                subarea_item_score[str(item.subarea_id)] = []
            assessment_item_score = (
                session.query(AssessmentItemScoreModel)
                .filter(
                    AssessmentItemScoreModel.assessment_id == str(assessment_id),
                    AssessmentItemScoreModel.item_id == str(item.id),
                )
                .first()
            )

            item_id = str(item.id)
            if assessment_item_score is None:
                raise AnyExceptionHandler(
                    f"Grade not filled for item with Id {item_id}"
                )

            if assessment_item_score.item_grade is None:
                raise AnyExceptionHandler(
                    f"Grade not filled for item with Id 123 {item_id}"
                )

            # self._addItemScore(
            #     assessment_item_score,
            #     str(assessment_item_score.item_grade.name),
            #     item.effective_weightage,
            # )

            subarea_item_score[str(item.subarea_id)].append(
                (
                    assessment_item_score.item_score,
                    item.effective_weightage,
                )
            )
        print("After items loop", subarea_item_score)

        result_dict = self._addSubareaScore(subarea_item_score)
        print("Overall result", result_dict)
        assessment.overall_score = result_dict.get("overall_score")
        assessment_subareas = []
        print("Before subarea item score loop")
        for key, value in result_dict.get("subarea_item_score").items():
            assessment_subarea_score = (
                session.query(AssessmentSubareaScoreModel)
                .filter(
                    AssessmentSubareaScoreModel.assessment_id == str(assessment.id),
                    AssessmentSubareaScoreModel.subarea_id == str(key),
                )
                .first()
            )

            # if not exist create
            if not assessment_subarea_score:
                assessment_subarea_score = AssessmentSubareaScoreModel()
            assessment_subarea_score.subarea_id = str(key)
            assessment_subarea_score.subarea_score = value
            assessment_subarea_score.assessment_id = str(assessment.id)
            assessment_subarea_score.subarea_techdebt_count = self._getTechdebtCount(
                str(key), assessment_id
            )
            assessment_subareas.append(assessment_subarea_score)
        print("After subarea item score loop")
        try:
            session.add_all(assessment_subareas)
            session.commit()
        except Exception as ex:
            print("Error adding subareas", ex)
            session.rollback()
            raise AnyExceptionHandler(ex)
        # finally:
        #     session.close()
        # return (assessment_item_scores, items)

    # def _checkGetAssessmentListParameters(self, **kwargs):
    #     required_parameters = {
    #         "project_id": "Project Id",
    #         "status": "Assessment Status",
    #         "authenticated_user_id": "Authenticated User Id",
    #         "authenticated_user_roles": "Authenticated User Role",
    #     }
    #     for key, value in required_parameters.items():
    #         if key not in kwargs or not kwargs.get(key):
    #             raise AttributeNotPresent(value)

    @decor
    def updateAssessmentStatus(self, **kwargs):
        """
        Update Assessment Status
        Assessment status update
        - On creation of assessment [Todo]
        - On submission of first draft [In-Progress]
        - On submission for review [Submitted]
        - When the reviewer declines the information due to some reason like incomplete data [Declined]
        - When the reviewer is reviewing and has done a submission of the first draft [Review-In-Progress]
        - When the reviewer submits [Reviewed]

        Input : dict 'project_id': 123, 'assessment_id': 234}
        Output: dict containing updated object/ failure message


        """

        project_id = kwargs.get("project_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        user_object = UserClass()
        authenticated_user = user_object._getUserById(authenticated_user_id)
        if authenticated_user is None:
            raise AttributeIdNotFound("Authenticated User Id")
        if authenticated_user.status.name != "Verified":
            raise AnyExceptionHandler(
                "An unverified/inactive user cannot make changes to the assessment status"
            )
        project_object = ProjectClass()
        project = project_object._getProjectById(project_id)
        if project is None:
            raise AttributeIdNotFound("Project")

        if not project.is_active:
            raise AnyExceptionHandler(
                "Project is Inactive. Cannot update assessment status of an inactive project!"
            )

        assessment_id = kwargs.get("assessment_id")
        assessment = self._getAssessmentById(assessment_id)
        if assessment is None:
            raise AttributeIdNotFound("Assessment")

        if not str(assessment.project_id) == project_id:
            raise AnyExceptionHandler(
                "Assessment does not belong to the given project!"
            )
        assessment_status = {
            "ToDo": ["InProgress"],
            "InProgress": ["InProgress", "Submitted"],
            "Submitted": ["UnderReview", "InProgress"],
            "UnderReview": ["Reviewed", "Declined"],
            "Declined": ["ToDo", "InProgress"],
        }

        status = kwargs.get("status")
        if not AssessmentStatus.has_value(status):
            raise InvalidAttribute("Assessment", "Status value")
        if status == "Expired":
            raise AnyExceptionHandler("Cannot mark assessment status as expired")
        if status == "ToDo":
            raise AnyExceptionHandler("Cannot mark assessment status as ToDo")

        _db_status = assessment.status.name
        _frontend_status = status
        _check_status = assessment_status.get(_db_status)

        calculation_task_id = None
        calculation_task_status = None
        activeTask = self._getActiveTask(assessment_id)
        if activeTask:
            calculation_task_id = str(activeTask.id)
            calculation_task_status = activeTask.status

        if _frontend_status not in _check_status:
            raise AnyExceptionHandler(
                f"""Assessment status cannot be updated as the previous status of the
                 assessment is {_db_status} and the current status {_frontend_status} is not coherent."""
            )
        if status == "InProgress":
            if datetime.now().date() < assessment.start_date:
                raise AnyExceptionHandler(
                    "Cannot initiate assessment before the start date"
                )
        elif status in ("Submitted", "Reviewed"):
            if activeTask:
                activeTask.active = False
            calculationTask = GradeCalculationTaskModel()
            calculationTask.assessment_id = assessment_id
            session.add(calculationTask)
            session.flush()
            calculation_task_id = str(calculationTask.id)
            calculation_task_status = calculationTask.status
            print("Calculation task created", calculationTask.as_dict())
            self._queueGradeCalculation(
                assessment_id, calculation_task_id, status=status
            )

            if status == "Submitted":
                print(
                    "IF CONDITION MATCHED",
                )
                self._storeManagerDelta(assessment)
                print("Manager Delta added!")

        assessment.status = status
        assessment.modified_by = authenticated_user_id
        assessment.modified_on = datetime.now()
        print("Adding Assessment record")
        try:
            session.add(assessment)
            session.commit()
            session.refresh(assessment)
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        assessmentDict = assessment.as_dict()
        assessmentDict["calculation_task_id"] = calculation_task_id
        assessmentDict["calculation_task_status"] = calculation_task_status

        # updateAssessmentStatus event published to assessmentUpdatedTopic
        client = boto3.client("sns")
        if status in ("Submitted", "Reviewed"):
            published_message = client.publish(
                TargetArn=os.getenv("SNS_ASSESSMENT_UPDATED_ARN"),
                Message=json.dumps(assessment.as_dict()),
            )
            print("updateAssessmentStatus published_message:: ", published_message)

        return assessmentDict

    def _queueGradeCalculation(self, assessment_id, task_id, status="Submitted"):
        print("queuing Calculation to sqs")
        messageBody = json.dumps(
            {
                "assessment_id": str(assessment_id),
                "grade_calculation_task_id": str(task_id),
                "status": status,
            }
        )
        client = boto3.client("sqs")
        response = client.send_message(
            QueueUrl=os.getenv("SQS_GRADE_CALCULATION_QUEUE_URL"),
            MessageBody=messageBody,
        )
        print("queued!", messageBody)

    def _getGradeCalculationTaskById(self, task_id):
        try:
            task = (
                session.query(GradeCalculationTaskModel)
                .filter(GradeCalculationTaskModel.id == str(task_id))
                .first()
            )

        except Exception as ex:
            print("error retreiving GradeCalculationTask record", ex)
            return None
        return task

    def _getActiveTask(self, assessment_id):
        try:
            task = (
                session.query(GradeCalculationTaskModel)
                .filter(
                    GradeCalculationTaskModel.assessment_id == str(assessment_id),
                    GradeCalculationTaskModel.active == True,
                )
                .first()
            )

        except Exception as ex:
            print("error retreiving GradeCalculationTask record", ex)
            return None
        return task

    def _createAssessmentName(self, project, start_date_str):
        name = None
        # today = datetime.today()
        start_date = datetime.strptime(start_date_str, "%Y-%m-%d")
        if project.audit_frequency == AuditFrequency.Monthly:
            name = f"Monthly-{start_date.strftime('%h')}-{start_date.strftime('%Y')}"
        elif project.audit_frequency == AuditFrequency.Quarterly:
            name = f"Quarterly-Q{((start_date.month-1)//3)+1}-{start_date.strftime('%Y')}"
        elif project.audit_frequency == AuditFrequency.HalfYearly:
            name = f"Half-Yearly-H{((start_date.month-1)//6)+1}-{start_date.strftime('%Y')}"
        elif project.audit_frequency == AuditFrequency.Yearly:
            name = f"Yearly-Y-{start_date.strftime('%Y')}"
        return name

    def _getAssessmentByNameProject(self, name, project_id):
        try:
            assessment = (
                session.query(AssessmentModel)
                .filter(
                    AssessmentModel.name == name,
                    AssessmentModel.project_id == str(project_id),
                )
                .first()
            )

        except Exception as ex:
            print("Error getting assessment", ex)
            return None
        return assessment

    def _calculateTechDebt(self, assessment_id):
        try:
            result = (
                session.query(ResponseModel)
                .filter(
                    ResponseModel.value == ResponseValue.No,
                    ResponseModel.assessment_id == assessment_id,
                    ResponseModel.type == ResponseType.ReviewerResponse,
                )
                .count()
            )
        except Exception as ex:
            print("Error getting tech debt count", ex)
            return None
        return result

    def _getAssessmentReviewerDelta(self, assessment):
        assessment_id = assessment.id
        checklist_id = assessment.checklist_id

        areas = session.query(AreaModel).filter(
            AreaModel.checklist_id == checklist_id
        )

        # 11-March-2023 | delta count for each area | start
        area_ids = [str(a.id) for a in areas]

        area_delta_counts = (
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

        area_delta_list = [dc[1] for dc in area_delta_counts]
        assessment_delta = sum(area_delta_list)

        return assessment_delta

        # 11-March-2023 | delta count for each area | end

    def _getAssessmentManagerDelta(self, assessment):
        assessment_id = assessment.id
        checklist_id = assessment.checklist_id

        areas = session.query(AreaModel).filter(
            AreaModel.checklist_id == checklist_id
        )

        # 11-March-2023 | delta count for each area | start
        area_ids = [str(a.id) for a in areas]

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

        area_delta_list = [dc[1] for dc in area_delta_counts_reviewer]
        assessment_delta = sum(area_delta_list)

        return assessment_delta

        # delta count for each area | end reviewer
