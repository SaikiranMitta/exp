import random

from common import *
from microservices.assessments.services.assessmentsService import (
    Assessment,
)

# from models.assessmentModels import Response as ResponseModel
from models import *
from models.assessmentModels import Response
from models.database.dbConnection import session

user_responses = [
    ("Yes", ("Not filled Properly", "Angular used")),
    ("No",),
    (
        "NA",
        (
            "Not needed as per client specifications",
            "Incomplete explanation",
        ),
    ),
]

responses = session.query(Response).filter(
    Response.assessment_id == "fa1be7bb-a170-4aee-b374-ec6b8b5f12b3",
    Response.type == "ReviewerResponse",
)
add_responses = []
for response in responses:
    print(response)
    choice = random.choice(user_responses)
    if choice[0] in ["Yes", "NA"]:
        comment = random.choice(choice[1])
    else:
        comment = None
    response.value = choice[0]
    response.comments = comment
    session.commit()

    # print(choice[0])
    # print("555" * 7)
    # print(type(choice[0]))
    # setattr(response, "value", choice[0])
    # setattr(response, "comment", comment)
session.commit()
