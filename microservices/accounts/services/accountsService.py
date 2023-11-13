import datetime

from sqlalchemy import desc, exc

from common.customExceptions import *
from common.decorator import decor
from common.paginate import Paginate as Pagination
from microservices.domains.services.domainsService import Domain as DomainClass
from microservices.users.services.usersService import User as UserClass
from models.accountModels import Account as AccountModel
from models.database.dbConnection import session
from models.projectModels import Project
from models.projectUserModels import ProjectUser


class Account:
    def _checkgetAccountListParameters(self, **kwargs):
        required_parameters = {
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getAccountList(self, **kwargs):
        """
        Fetch List of Accounts in the system.
        Input: None
        Output: List []

        """
        self._checkgetAccountListParameters(**kwargs)
        accounts_filters = []
        accounts = session.query(AccountModel)

        if (
            "Project_Manager" in kwargs["authenticated_user_roles"]
            or "Engineer" in kwargs["authenticated_user_roles"]
        ):
            user_id = kwargs["authenticated_user_id"]
            accounts = accounts.join(Project).join(ProjectUser)
            accounts_filters.append(ProjectUser.user_id == user_id)

        if kwargs["domain_id"]:
            domain_id = kwargs["domain_id"]
            domain_object = DomainClass()
            domain = domain_object._getDomainById(domain_id)
            if domain is None:
                raise AttributeIdNotFound("Domain")

            accounts_filters.append(AccountModel.domain_id == domain_id)

        if kwargs["active"]:
            active = kwargs["active"]
            if active not in ["true", "false"]:
                raise InvalidAttribute("Account", "status value")
            if active == "true":
                active = True
            else:
                active = False
            accounts_filters.append(AccountModel.is_active == active)

        if kwargs["search"]:
            search = kwargs["search"]
            accounts = accounts.filter(AccountModel.name.ilike(f"%{search}%"))

        # paginate and filter data | start
        if accounts_filters:
            accounts = accounts.filter(*accounts_filters)

        total_data_count = accounts.count()

        paginate = Pagination(
            AccountModel,
            **{
                "page_size": kwargs["page_size"],
                "page_number": kwargs["page_number"],
                "sort_key": kwargs["sort_key"],
                "sort_order": kwargs["sort_order"],
                "total_data_count": total_data_count,
            },
        )

        pagination_setting = paginate._getPaginationSetting()
        pagination_response_attributes = paginate._getResponseAttribute()

        accounts = (
            accounts.order_by(pagination_setting["sort_key_order"])
            .limit(pagination_setting["page_size"])
            .offset(pagination_setting["offset_value"])
            .all()
        )

        # paginate and filter data | end

        accountsSerializedObject = [account.as_dict() for account in accounts]
        return accountsSerializedObject, pagination_response_attributes

    def _getAccountById(self, id):
        """
        Checks if the input account id belongs to an account in the system.
        Input: Id -> Id of the account
        Output-> account object if the account exists for the given account Id else None

        """
        try:
            account = (
                session.query(AccountModel).filter(AccountModel.id == id).first()
            )
        except Exception:
            return None
        finally:
            session.close()
        return account

    def _checkGetAccountDetailsParameters(self, **kwargs):
        required_parameters = {
            "account_id": "Account id",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }
        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def getAccountDetails(self, **kwargs):
        """
        Input: dict  {"account_id": 123}
        Output: dict containing information about account / failure message

        """
        self._checkGetAccountDetailsParameters(**kwargs)
        account_id = kwargs.get("account_id")
        account = self._getAccountById(account_id)
        if account is None:
            raise AttributeIdNotFound("Account")
        return account.as_dict()

    def _getAllAccounts(self):
        """
        Fetch List of Accounts in the system.
        Input: None
        Output: List []

        """
        accounts = session.query(AccountModel).all()
        return accounts

    def _getAccountsByUser(self, user_id):
        """
        Fetch List of Accounts of which projects are mapped to user
        Input: authenticated_user_id
        Output: List []

        """
        accounts = (
            session.query(AccountModel)
            .join(Project)
            .join(ProjectUser)
            .filter(ProjectUser.user_id == user_id)
            .all()
        )
        return accounts

    def _checkCreateAccountParameters(self, **kwargs):
        required_parameters = {
            "name": "Name",
            "domain_id": "Domain Id",
            "is_active": "Is Active",
            "created_by": "Created By",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def createAccount(self, **kwargs):
        """

        Add Account in the system.
        Input: dict {"name"}

        Output: dict { "name","created_by", "created_on" ,  "modified_by" , " modified_on"}

        """
        self._checkCreateAccountParameters(**kwargs)
        name = kwargs.get("name")
        is_active = True
        domain_id = kwargs.get("domain_id")
        created_by = kwargs.get("created_by")
        account = AccountModel()
        account.name = name
        account.domain_id = domain_id
        account.is_active = is_active
        account.created_by = created_by
        try:
            session.add(account)
            session.commit()
            session.refresh(account)

        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Account", "name")
        except Exception as ex:
            print("Error creating Account", ex)
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()
        return account.as_dict()

    def _checkUpdateAccountParameters(self, **kwargs):
        required_parameters = {
            "id": "Id",
            "name": "Name",
            "domain_id": "Domain Id",
            "is_active": "Is Active",
            "created_by": "Created By",
            "authenticated_user_id": "Authenticated User Id",
            "authenticated_user_roles": "Authenticated User Role",
        }

        for key, value in required_parameters.items():
            if key not in kwargs:
                raise AttributeNotPresent(value)

    @decor
    def updateAccount(self, **kwargs):
        """
        update an existing account object,

        """

        self._checkUpdateAccountParameters(**kwargs)

        account_id = kwargs.get("id")
        name = kwargs.get("name")
        domain_id = kwargs.get("domain_id")
        authenticated_user_id = kwargs.get("authenticated_user_id")
        account = self._getAccountById(account_id)
        if account is None:
            raise AttributeIdNotFound("Account")
        account.name = name
        account.domain_id = domain_id
        account.modified_by = authenticated_user_id
        account.modified_on = datetime.datetime.utcnow()

        try:
            session.add(account)
            session.commit()
            session.refresh(account)
        except exc.IntegrityError as ex:
            session.rollback()
            raise AlreadyExists("Account", "name")
        except Exception as ex:
            session.rollback()
            raise AnyExceptionHandler(ex)
        finally:
            session.close()

        return account.as_dict()
