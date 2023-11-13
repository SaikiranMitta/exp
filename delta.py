import random

from numpy import add
from sqlalchemy import desc

from common import *
from common.customExceptions import AnyExceptionHandler
from microservices.assessments.services.assessmentsService import (
    Assessment,
)
from models import *
from models.assessmentModels import Assessment as AssessmentModel
from models.assessmentModels import (
    AssessmentResponseDelta as AssessmentResponseDeltaModel,
)
from models.assessmentModels import Response
from models.assessmentModels import Response as ResponseModel
from models.checklistModels import Activity as ActivityModel
from models.database.dbConnection import session
from models.dbModels import AssessmentResponseDelta


def _getDelta(assessment):
    previous_assessment = (
        session.query(AssessmentModel)
        .filter(
            AssessmentModel.project_id == str(assessment.project_id),
            AssessmentModel.status == "Reviewed",
        )
        .order_by(desc(AssessmentModel.start_date))
        .limit(1)[0:1]
    )
    if not previous_assessment:
        delta = []
        print("none")
    else:
        previous_assessment = previous_assessment[0]
        if (
            not previous_assessment.checklist_id
            == assessment.checklist_id
        ):
            delta = []
        else:
            previous_assessment_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id
                == str(previous_assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

            current_assessment_responses = session.query(
                ResponseModel.activity_id,
                ResponseModel.value,
                ResponseModel.comments,
            ).filter(
                ResponseModel.assessment_id == str(assessment.id),
                ResponseModel.type == "ManagerResponse",
            )

            current_assessment_response_dict = {}
            for (
                current_assessment_response
            ) in current_assessment_responses:

                current_assessment_response_dict[
                    current_assessment_response.activity_id
                ] = (
                    current_assessment_response.value,
                    current_assessment_response.comments,
                )
            previous_assessment_response_dict = {}
            for (
                previous_assessment_response
            ) in previous_assessment_responses:

                previous_assessment_response_dict[
                    previous_assessment_response.activity_id
                ] = (
                    previous_assessment_response.value,
                    previous_assessment_response.comments,
                )
            delta = []
            assessment_responses_delta = []
            for (
                key,
                value,
            ) in previous_assessment_response_dict.items():

                current_value = current_assessment_response_dict[key]
                # print(current_value)
                isequal_value = value[0] == current_value[0]
                if not isequal_value:
                    assessment_response_delta = (
                        AssessmentResponseDeltaModel()
                    )
                    assessment_response_delta.field = "value"
                    assessment_response_delta.previous_value = value[
                        0
                    ]
                    assessment_response_delta.assessment_id = str(
                        assessment.id
                    )
                    assessment_responses_delta.append(
                        assessment_responses_delta
                    )

                isequal_comments = value[1] == current_value[1]
                if not isequal_value:
                    assessment_response_delta = (
                        AssessmentResponseDeltaModel()
                    )
                    assessment_response_delta.field = "value"
                    assessment_response_delta.previous_value = value[
                        0
                    ]
                    assessment_response_delta.previous_value = value[
                        0
                    ]
                    assessment_response_delta.assessment_id = str(
                        assessment.id
                    )

                    assessment_responses_delta.append(
                        assessment_responses_delta
                    )

            if assessment_responses_delta:
                session.add_all(assessment_responses_delta)
                try:
                    session.commit()

                except Exception as ex:
                    session.rollback()
                    raise AnyExceptionHandler(ex)
                finally:
                    session.close()

            #         field= "value"
            #         previous_value= value[0]
            #         assessment_id=

            #     isequal = value == current_value
            #     if not isequal:
            #         # print(value)
            #         # print("Value")
            #         # print(current_value)
            #         # print("Current Value")
            #         delta.append(key)
            # print(delta)
            # print(previous_assessment.id)
            # reviewer_responses = session.query(ResponseModel).filter(
            #     ResponseModel.activity_id.not_in(delta),
            #     ResponseModel.assessment_id == str(previous_assessment.id),
            # )

            # reviewer_responses_dict = {}
            # for reviewer_response in reviewer_responses:
            #     reviewer_responses_dict[str(reviewer_response.activity_id)] = (
            #         reviewer_response.value.name,
            #         reviewer_response.comments,
            #     )

            # current_assessment_reviewer_responses = session.query(
            #     ResponseModel
            # ).filter(
            #     ResponseModel.assessment_id == str(assessment.id),
            #     ResponseModel.type == "ReviewerResponse",
            # )
            # updated_reviewer_responses = []
            # for (
            #     current_assessment_reviewer_response
            # ) in current_assessment_reviewer_responses:
            #     if reviewer_responses_dict.get(
            #         str(current_assessment_reviewer_response.activity_id)
            #     ):
            #         # current_assessment_reviewer_response.value = (
            #         #     reviewer_responses_dict.get(
            #         #         str(current_assessment_reviewer_response.activity_id)
            #         #     )[0]
            #         # # )
            #         # print(current_assessment_response.value)
            #         # # current_assessment_reviewer_response.comments = (
            #         #     reviewer_responses_dict.get(
            #         #         str(current_assessment_reviewer_response.activity_id)
            #         #     )[1]
            #         # )
            #         # print(current_assessment_response.comments)
            #         updated_reviewer_responses.append(
            #             {
            #                 "id": (current_assessment_reviewer_response.id),
            #                 "value": reviewer_responses_dict.get(
            #                     str(current_assessment_reviewer_response.activity_id)
            #                 )[0],
            #                 "comments": reviewer_responses_dict.get(
            #                     str(current_assessment_reviewer_response.activity_id)
            #                 )[1],
            #             }
            #         )

            # session.bulk_update_mappings(ResponseModel, updated_reviewer_responses)
            # session.flush()

            # session.commit()


assessment = (
    session.query(AssessmentModel)
    .filter(
        AssessmentModel.id == "971d2855-b68e-4525-aaa4-171ed171fd42"
    )
    .first()
)
_getDelta(assessment)
