# from commonImports import *
# from userModels import *
import sqlalchemy as sa

from models.assessmentModels import *
from models.checklistModels import *
from models.commonImports import *
from models.userModels import *

# class SubareaScore(enum.Enum):
#     A = "A"
#     B = "B"
#     C = "C"
#     D = "D"
#     NA = "NA"

#     @classmethod
#     def has_value(cls, value):
#         return value in cls._value2member_map_


class AssessmentSubareaScore(Base):
    __tablename__ = "t_tenet_assessment_subarea_score"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    subarea_id = Column(UUID, ForeignKey("t_tenet_subarea.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey("t_tenet_assessment.id"), nullable=False)
    subarea_techdebt_count = Column(Integer, nullable=False)
    subarea_score = Column(FLOAT(precision=10, scale=4))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # user_role = relationship("UserRole", backref="role")

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


class ItemGrade(enum.Enum):
    A = "A"
    B = "B"
    C = "C"
    D = "D"
    NA = "NA"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class AssessmentItemScore(Base):
    __tablename__ = "t_tenet_assessment_item_score"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    item_id = Column(UUID, ForeignKey("t_tenet_item.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey("t_tenet_assessment.id"), nullable=False)
    item_grade = Column(Enum(ItemGrade))
    # item_score = Column(String(8))
    item_score = Column(FLOAT(precision=10, scale=2))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # user_role = relationship("UserRole", backref="role")
    sa.UniqueConstraint(item_id, assessment_id)

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
