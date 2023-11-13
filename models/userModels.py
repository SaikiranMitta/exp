import enum

from models.commonImports import *

# from commonImports import *


class UserStatus(enum.Enum):
    Verified = "Verified"
    Unverified = "Unverified"
    Inactive = "Inactive"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class User(Base):
    __tablename__ = "t_tenet_user"

    id = Column(UUID(as_uuid=True), primary_key=True)
    name = Column(Text, unique=False, nullable=False)
    username = Column(Text, nullable=False)
    status = Column(Enum(UserStatus), default="UnVerified")
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    ps_no = Column(Integer, unique=True, nullable=False)

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
