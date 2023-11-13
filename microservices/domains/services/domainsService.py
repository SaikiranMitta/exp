import datetime
import json
import os
from pathlib import Path
from sys import audit
from typing import Any

import boto3
from dotenv import load_dotenv
from sqlalchemy import exc

from common.customExceptions import *
from common.decorator import decor
from common.paginate import Paginate as Pagination
from models.accountModels import Account
from models.database.dbConnection import session
from models.domainModels import Domain as DomainModel
from models.projectModels import Project
from models.projectUserModels import ProjectUser


class Domain:
    def _checkGetDomainDetailsParameters(self, **kwargs):
        required_parameters = {
            "domain_id": "Domain Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def _checkgetDomainDetailsParameters(self, **kwargs):
        required_parameters = {
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    def _getDomainsByUser(self, user_id):
        """
        Fetch List of Domains of which projects are mapped to user
        Input: authenticated_user_id
        Output: List []

        """
        domains = (
            session.query(DomainModel)
            .join(Account)
            .join(Project)
            .join(ProjectUser)
            .filter(ProjectUser.user_id == user_id)
        )
        return domains

    def _getAllDomains(self):
        """
        Fetch List of Domains in the system.
        Input: None
        Output: List []

        """
        domains = session.query(DomainModel)
        return domains

    @decor
    def getDomainList(self, **kwargs):
        """
        Fetch List of Domain in the system.
        Input: None
        Output: List []

        """
        self._checkgetDomainDetailsParameters(**kwargs)
        domains = None
        if (
            "Project_Manager" in kwargs["authenticated_user_roles"]
            or "Engineer" in kwargs["authenticated_user_roles"]
        ):
            domains = self._getDomainsByUser(kwargs["authenticated_user_id"])
        else:
            domains = self._getAllDomains()

        # pagination and filters | Start

        if kwargs["domain_head_id"]:
            domain_head_id = kwargs["domain_head_id"]
            domains = domains.filter(DomainModel.domain_head_id == domain_head_id)

        total_data_count = domains.count()

        paginate = Pagination(
            DomainModel,
            **{
                "page_size": kwargs["page_size"],
                "page_number": kwargs["page_number"],
                "sort_key": kwargs["sort_key"],
                "sort_order": kwargs["sort_order"],
                "total_data_count": total_data_count,
            }
        )

        pagination_setting = paginate._getPaginationSetting()
        pagination_response_attributes = paginate._getResponseAttribute()

        domains = (
            domains.order_by(pagination_setting["sort_key_order"])
            .limit(pagination_setting["page_size"])
            .offset(pagination_setting["offset_value"])
            .all()
        )
        # pagination and filters | End

        domainsSerializedObject = [domain.as_dict() for domain in domains]
        return domainsSerializedObject, pagination_response_attributes

    def _getDomainById(self, id):
        """
        Checks if the input domain id belongs to an domain in the system.
        Input: Id -> Id of the domain
        Output-> domain object if the account exists for the given domain Id else None

        """
        try:
            domain = session.query(DomainModel).filter(DomainModel.id == id).first()
        except Exception:
            return None
        finally:
            session.close()
        return domain

    @decor
    def getDomainDetails(self, **kwargs):
        """
        Fetch details of the requested Role
        Input:
        Output: {} containing details of the role

        """

        # if not kwargs.get("pathParameters"):
        #     raise PathParameterNotFound()
        # if not kwargs.get("pathParameters").get("role_id"):
        #     raise URLAttributeNotFound("Role Id")

        self._checkGetDomainDetailsParameters(**kwargs)
        domain_id = kwargs.get("domain_id")
        domain = self._getDomainById(domain_id)
        if domain is None:
            raise AttributeIdNotFound("Domain")
        return domain.as_dict()

    def _checkCreateDomainParameters(self, **kwargs):
        required_parameters = {
            "name": "Name",
            "domain_head_id": "Domain Head Id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs or not kwargs.get(key):
                raise AttributeNotPresent(value)

    @decor
    def createDomain(self, **kwargs):
        """

        Add Domain in the system.
        Input: dict {"name"}

        Output: dict { "name","created_by", "created_on" ,  "modified_by" , " modified_on"}

        """
        self._checkCreateDomainParameters(**kwargs)
        name = kwargs.get("name")
        domain_head_id = kwargs.get("domain_head_id")
        created_by = kwargs.get("created_by")
        print("name", name)
        domain = DomainModel()
        domain.name = name
        domain.domain_head_id = domain_head_id
        domain.created_by = created_by
        try:
            session.add(domain)
            session.commit()
            session.refresh(domain)

        except exc.IntegrityError as ex:
            print("Exception", ex, "domain", domain)
            session.rollback()
            raise AlreadyExists("Domain", "name")
        except Exception as ex:
            print("Error creating Domain", ex)
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return domain.as_dict()

    def _checkUpdateDomainParameters(self, **kwargs):
        required_parameters = {
            "id": "Id",
            "name": "Name",
            "domain_head_id": "Domain Head Id",
            "created_by": "Created By",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def updateDomain(self, **kwargs):
        """
        update an existing domain object,

        """

        self._checkUpdateDomainParameters(**kwargs)

        domain_id = kwargs.get("id")
        name = kwargs.get("name")
        domain_head_id = kwargs.get("domain_head_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        domain = self._getDomainById(domain_id)
        if domain is None:
            raise AttributeIdNotFound("Domain")
        domain.name = name
        domain.domain_head_id = domain_head_id
        domain.modified_by = authenticated_user_id
        domain.modified_on = datetime.datetime.utcnow()

        try:
            session.add(domain)
            session.commit()
            session.refresh(domain)
        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Domain", "name")
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()

        return domain.as_dict()
