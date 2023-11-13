import datetime
import enum
import logging
import os
import uuid

import sqlalchemy as sa
from database.dbConnection import engine, session
from marshmallow_sqlalchemy import column2field
from sqlalchemy import (
    VARCHAR,
    Boolean,
    Column,
    Date,
    DateTime,
    Enum,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.dialects.mysql import FLOAT
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from sqlalchemy.sql.expression import null

# from userModels import *

Base = declarative_base()


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
        return {
            c.name: str(getattr(self, c.name))
            if c.name in ["created_on", "modified_on", "id"]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


class Area(Base):
    __tablename__ = "t_tenet_area"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    weightage = Column(FLOAT(precision=10, scale=2))
    checklist_id = Column(UUID, ForeignKey("t_tenet_checklist.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # checklist =relationship("Checklist", backref="area")


class Subarea(Base):
    __tablename__ = "t_tenet_subarea"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    weightage = Column(FLOAT(precision=10, scale=2))
    area_id = Column(UUID, ForeignKey("t_tenet_area.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # checklist =relationship("Checklist", backref="area")


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


class Activity(Base):
    __tablename__ = "t_tenet_activity"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, nullable=False)
    importance = Column(Enum(ActivityImportance))
    item_id = Column(UUID, ForeignKey("t_tenet_item.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
                "importance" "start_date",
                "end_date",
                "item_id",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


class UserStatus(enum.Enum):
    Verified = "Verified"
    Unverified = "Unverified"
    Inactive = "Inactive"

    @classmethod
    def has_value(cls, value):
        return value in cls._value2member_map_


class User(Base):
    __tablename__ = "t_tenet_user"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=False, nullable=False)
    username = Column(Text, nullable=False)
    status = Column(Enum(UserStatus), default="UnVerified")
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name in ["created_on", "modified_on", "id", "status"]
            else getattr(self, c.name)
            for c in self.__table__.columns
            # if c.name not in ["name"]
        }


class Account(Base):
    __tablename__ = "t_tenet_account"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    is_active = Column(Boolean, default=False)
    domain_id = Column(UUID, ForeignKey("t_tenet_domain.id"))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name in ["created_on", "modified_on", "id"]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


class Frequency(enum.Enum):
    Monthly = "Monthly"
    Yearly = " Yearly"
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
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
                "start_date",
                "audit_frequency",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
            if not c == "is_active"
        }


class Role(Base):
    __tablename__ = "t_tenet_role"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # user_role = relationship("UserRole", backref="role")

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


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
    role_id = Column(
        UUID, ForeignKey("t_tenet_role.id", ondelete="CASCADE"), nullable=False
    )
    resource = Column(Enum(Resource))
    action = Column(Enum(Action))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    sa.UniqueConstraint(role_id, resource, action)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
                "action",
                "resource",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
            if c.name not in ["ptype", "role_id"]
        }
        # return {
        #     c.name: str(getattr(self, c.name))
        #     if c.name in ["created_on", "modified_on", "id", "status"]
        #     else getattr(self, c.name)
        #     for c in self.__table__.columns
        # }


class ProjectUser(Base):
    __tablename__ = "t_tenet_project_user_mapper"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    project_id = Column(UUID, ForeignKey("t_tenet_project.id"), nullable=False)
    user_id = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    sa.UniqueConstraint(project_id, user_id)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name in ["created_on", "modified_on", "user_id", "project_id"]
            else getattr(self, c.name)
            for c in self.__table__.columns
            if not c.name in ["id", "project_id"]
        }


class UserRole(Base):
    __tablename__ = "t_tenet_user_role_mapper"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    role_id = Column(UUID, ForeignKey("t_tenet_role.id"), nullable=False)
    user_id = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    sa.UniqueConstraint(role_id, user_id)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name in ["created_on", "modified_on", "user_id", "role_id"]
            else getattr(self, c.name)
            for c in self.__table__.columns
            if not c.name in ["id", "role_id", "user_id"]
        }


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
    name = Column(Text, unique=True, nullable=False)
    project_id = Column(UUID, ForeignKey("t_tenet_project.id"), nullable=False)
    overall_score = Column(FLOAT(precision=10, scale=2))
    checklist_id = Column(UUID, ForeignKey("t_tenet_checklist.id"), nullable=False)
    start_date = Column(Date, nullable=False)
    end_date = Column(Date, nullable=False)
    status = Column(Enum(AssessmentStatus))
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
                "start_date",
                "end_date",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


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
    activity_id = Column(UUID, ForeignKey("t_tenet_activity.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey("t_tenet_assessment.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)


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
    subarea_score = Column(FLOAT(precision=10, scale=4))
    subarea_techdebt_count = Column(Integer, nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)
    # user_role = relationship("UserRole", backref="role")

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


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

    def as_dict(self):
        return {
            c.name: str(getattr(self, c.name))
            if c.name
            in [
                "created_on",
                "modified_on",
                "id",
            ]
            else getattr(self, c.name)
            for c in self.__table__.columns
        }


class ResponseField(enum.Enum):
    value = "value"
    comments = "comments"


class AssessmentResponseDelta(Base):
    __tablename__ = "t_tenet_assessment_response_delta"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    activity_id = Column(UUID, ForeignKey("t_tenet_activity.id"), nullable=False)
    assessment_id = Column(UUID, ForeignKey("t_tenet_assessment.id"), nullable=False)
    previous_assessment_id = Column(
        UUID, ForeignKey("t_tenet_assessment.id"), nullable=False
    )
    field = Column(Enum(ResponseField), nullable=False)
    previous_value = Column(Text, unique=True, nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)


class FunctionDescription(Base):
    __tablename__ = "t_tenet_function_description"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=True, nullable=False)
    resource = Column(Enum(Resource))
    action = Column(Enum(Action))


class Domain(Base):
    __tablename__ = "t_tenet_domain"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(Text, unique=False, nullable=False)
    domain_head_id = Column(UUID, ForeignKey("t_tenet_user.id"), nullable=False)
    created_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    created_on = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    modified_by = Column(UUID, ForeignKey("t_tenet_user.id"))
    modified_on = Column(DateTime)


Base.metadata.create_all(engine)
from accessControlScript import *
