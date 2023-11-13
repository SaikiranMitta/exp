import sqlalchemy as sa

from models.commonImports import *
from models.roleModels import *
from models.userModels import *


class UserRole(Base):
    __tablename__ = "t_tenet_user_role_mapper"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    role_id = Column(UUID, ForeignKey("t_tenet_role.id"), nullable=False)
    user_id = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime, default=datetime.datetime.utcnow)
    sa.UniqueConstraint(role_id, user_id)

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
