import enum
from models.commonImports import *


class NotificationStatus(enum.Enum):
    NEW = "New"
    READ = "Read"
    ARCHIVED = "Archived"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class NotificationSeverity(enum.Enum):
    LOW = "Low"
    MEDIUM = "Medium"
    HIGH = "High"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Notification(Base):
    __tablename__ = 't_tenet_notifications'
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id = Column(String)
    message = Column(String)
    description = Column(String)
    status = Column(Enum(NotificationStatus), default=NotificationStatus.NEW)
    severity = Column(Enum(NotificationSeverity),
                      default=NotificationSeverity.HIGH)

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
