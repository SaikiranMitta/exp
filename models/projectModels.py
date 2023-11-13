# from accountModels import *
# from commonImports import *
# from userModels import *

from models.accountModels import *
from models.commonImports import *
from models.userModels import *


class Frequency(enum.Enum):
    Monthly = "Monthly"
    Yearly = "Yearly"
    HalfYearly = "HalfYearly"
    Quarterly = "Quarterly"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class Project(Base):
    __tablename__ = "t_tenet_project"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    details = Column(Text, nullable=False)
    audit_frequency = Column(Enum(Frequency))
    trello_link = Column(Text, nullable=False)
    start_date = Column(Date, default=datetime.datetime.utcnow, nullable=False)
    account_id = Column(UUID, ForeignKey("t_tenet_account.id"), nullable=False)
    is_active = Column(Boolean, default=True)
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


Base.metadata.create_all(engine)
