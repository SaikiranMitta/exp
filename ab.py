import random
from ast import Sub

from numpy import add

from common import *
from microservices.assessments.services.assessmentsService import (
    Assessment,
)
from models.assessmentModels import Response
from models.checklistModels import *
from models.database.dbConnection import session

# a = (
#     session.query(Assessment)
#     .filter(Assessment.id == "fa1be7bb-a170-4aee-b374-ec6b8b5f12b3")
#     .first()
# )
# checklist_id = str(a.checklist_id)
# area = session.query(Area.id).filter(Area.checklist_id == checklist_id)
# subarea=session.query(Subarea.id).filter(Subarea.area_id.in_(area))
item = (
    session.query(Item)
    .filter(Item.id == "a7040e31-c1e1-44a5-a7ca-cd6aedb567e2")
    .first()
)
print(item.name)
print()
