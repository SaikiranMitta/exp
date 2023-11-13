from bdb import effective

from models.commonImports import *
from models.userModels import *

# from commonImports import *
# from userModels import *


class ChecklistStatus(enum.Enum):
    UnPublished = "UnPublished"
    Published = "Published"
    UnderReview = "UnderReview"
    Declined = "Declined"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class ActivityImportance(enum.Enum):
    MH = "MH"
    MIMH = "MIMH"
    GH = "GH"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Checklist(Base):
    __tablename__ = "t_tenet_checklist"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    is_active = Column(Boolean, default=False)
    status = Column(Enum(ChecklistStatus))
    comments = Column(Text, nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
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


class Area(Base):

    __tablename__ = "t_tenet_area"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    weightage = Column(FLOAT(precision=10, scale=2))
    checklist_id = Column(Integer, ForeignKey("t_tenet_checklist.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # checklist =relationship("Checklist", backref="area")

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


class Subarea(Base):

    __tablename__ = "t_tenet_subarea"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    weightage = Column(FLOAT(precision=10, scale=2))
    area_id = Column(Integer, ForeignKey("t_tenet_area.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # checklist =relationship("Checklist", backref="area")

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


class Item(Base):

    __tablename__ = "t_tenet_item"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    weightage = Column(FLOAT(precision=10, scale=2))
    effective_weightage = Column(FLOAT(precision=10, scale=2))
    subarea_id = Column(UUID, ForeignKey("t_tenet_subarea.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
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


class Activity(Base):

    __tablename__ = "t_tenet_activity"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, nullable=False)
    importance = Column(Enum(ActivityImportance))
    item_id = Column(Integer, ForeignKey("t_tenet_item.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
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
