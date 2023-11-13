from models.commonImports import *
from models.userModels import *

# from commonImports import *
# from userModels import *


class Action(enum.Enum):
    Read = "Read"
    Write = "Write"
    Create = "Create"
    Delete = "Delete"
    All = "All"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Resource(enum.Enum):
    Assessment = "Assessment"
    Checklist = "Checklist"
    Role = "Role"
    RolePolicy = "RolePolicy"
    User = "User"
    UserRole = "UserRole"
    Project = "Project"
    ProjectUser = "ProjectUser"
    All = "All"
    Account = "Account"
    Result = "Result"
    Item = "Item"
    Subarea = "Subarea"
    Area = "Area"
    Activity = "Activity"
    AssessmentResponse = "AssessmentResponse"
    Domain = "Domain"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class FunctionDescription(Base):
    __tablename__ = "t_tenet_function_description"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    resource = Column(Enum(Resource))
    action = Column(Enum(Action))
