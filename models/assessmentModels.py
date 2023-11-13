import enum

from models.commonImports import *
from models.projectModels import *
from models.userModels import *

# from commonImports import *
# from userModels import *

# from commonImports import *


class AssessmentStatus(enum.Enum):
    ToDo = "ToDo"
    InProgress = "InProgress"
    Submitted = "Submitted"
    Expired = "Expired"
    UnderReview = "UnderReview"
    Declined = "Declined"
    Reviewed = "Reviewed"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Assessment(Base):
    __tablename__ = "t_tenet_assessment"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, nullable=False)
    project_id = Column(UUID, ForeignKey("t_tenet_project.id"), nullable=False)
    overall_score = Column(FLOAT(precision=10, scale=2))
    tech_debt = Column(Integer)
    checklist_id = Column(UUID, ForeignKey(
        "t_tenet_checklist.id"), nullable=False)
    start_date = Column(Date, nullable=False)
    end_date = Column(Date, nullable=False)
    status = Column(Enum(AssessmentStatus), default=AssessmentStatus.ToDo)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(
        DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

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


class ResponseValue(enum.Enum):
    Yes = "Yes"
    No = "No"
    NA = "NA"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class ResponseType(enum.Enum):
    ManagerResponse = "ManagerResponse"
    ReviewerResponse = "ReviewerResponse"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Response(Base):

    __tablename__ = "t_tenet_response"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)

    value = Column(Enum(ResponseValue))
    type = Column(Enum(ResponseType))
    comments = Column(Text)
    activity_id = Column(UUID, ForeignKey(
        "t_tenet_activity.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey(
        "t_tenet_assessment.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(
        DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

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


class ResponseField(enum.Enum):
    value = "value"
    comments = "comments"

class DeltaType(enum.Enum):
    ManagerDelta = "ManagerDelta"
    ReviewerDelta = "ReviewerDelta"


class AssessmentResponseDelta(Base):
    __tablename__ = "t_tenet_assessment_response_delta"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    activity_id = Column(UUID, ForeignKey(
        "t_tenet_activity.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey(
        "t_tenet_assessment.id"), nullable=False)
    previous_assessment_id = Column(
        UUID, ForeignKey("t_tenet_assessment.id"), nullable=False
    )
    # field = Column(Enum(ResponseField))
    # previous_value = Column(Text, unique=True, nullable=False)
    # previous_value should not be unique
    type = Column(Enum(DeltaType))
    previous_value = Column(Text)
    previous_comments = Column(Text)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(
        DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

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


class GradeCalculationTask(Base):
    __tablename__ = "t_tenet_assessment_grade_calculation_status"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    assessment_id = Column(UUID, ForeignKey(
        "t_tenet_assessment.id"), nullable=False)
    status = Column(Boolean, default=False)
    active = Column(Boolean, default=True)
    created_on = Column(
        DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_on = Column(DateTime)

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
