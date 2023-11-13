import sqlalchemy as sa
from sqlalchemy import VARCHAR

from models.commonImports import *
from models.roleModels import *
from models.userModels import *

# from commonImports import *
# from roleModels import *
# from userModels import *


# class Ptype(enum.Enum):
#     p = "p"


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

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Action(enum.Enum):
    Read = "Read"
    Write = "Write"
    Create = "Create"
    Delete = "Delete"
    All = "All"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class RolePolicy(Base):
    __tablename__ = "CasbinRule"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    ptype = Column(VARCHAR(255), nullable=False)
    role_id = Column(UUID, ForeignKey("t_tenet_role.id"), nullable=False)
    resource = Column(Enum(Resource))
    action = Column(Enum(Action))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    sa.UniqueConstraint(role_id, resource, action)

    def as_dict(self):
        _json = {}
        for c in self.__table__.columns:
            _column_name = c.name
            _value = getattr(self, _column_name)
            if type(_value) in [type(None)] or isinstance(_value, bool):
                pass
            elif isinstance(_value, enum.Enum):
                _value = _value.value
            else:
                _value = str(_value)
            _json[_column_name] = _value
        return _json


Base.metadata.create_all(engine)
