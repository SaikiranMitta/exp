from contextlib import contextmanager
from dataclasses import field

import casbin
from casbin import persist
from sqlalchemy import Column, Integer, String, create_engine, or_
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from database.dbConnection import engine, session

# from rolePolicyModels import Database
from rolePolicyModels import RolePolicy as CasbinRule


class Adapter(persist.Adapter):
    """the interface for Casbin adapters."""

    def __init__(self, engine):
        self._engine = engine
        self._session = session

    def load_policy(self, model):
        """loads all policy rules from the storage."""
        lines = self._session.query(CasbinRule).all()
        # print(lines)
        # for line in lines:

        #     (persist.load_policy_line(str(line), model))

    def _save_policy_line(self, ptype, rule):
        line = CasbinRule(ptype=ptype)
        line.role_id = rule[0]
        line.v1 = rule[1]
        line.v2 = rule[2]

        # for i, v in enumerate(rule):
        #     setattr(line, 'v{}'.format(i), v)
        self._session.add(line)

    def _commit(self):
        self._session.commit()

    def save_policy(self, model):
        """saves all policy rules to the storage."""
        query = self._session.query(CasbinRule)
        query.delete()
        for sec in ["p", "g"]:
            if sec not in model.model.keys():
                continue
            for ptype, ast in model.model[sec].items():

                for rule in ast.policy:
                    self._save_policy_line(ptype, rule)
        self._commit()
        return True

    def add_policy(self, sec, ptype, rule):
        """adds a policy rule to the storage."""
        self._save_policy_line(ptype, rule)
        self._commit()

    def remove_policy(self, sec, ptype, rule, **kwargs):
        """removes a policy rule from the storage."""
        query = self._session.query(CasbinRule)
        query = query.filter(CasbinRule.ptype == ptype)
        # query=query.filter(CasbinRule.v3 == kwargs.get("permission_id"))
        for i, v in enumerate(rule):
            query = query.filter(
                getattr(CasbinRule, "v{}".format(i)) == v
            )
        r = query.delete()
        self._commit()

        return True if r > 0 else False

    def remove_filtered_policy(self, sec, ptype, *field_values):
        """removes policy rules that match the filter from the storage.
        This is part of the Auto-Save feature.
        """
        # print("#"*9)
        # print(sec)
        # print(ptype)
        # # print(field_index)
        # print(field_values)
        # role_id= field_values[0][0]
        # permission_id = field_values[0][1]
        # print("#"*9)
        query = self._session.query(CasbinRule)
        # query=query.filter(CasbinRule.v3 == kwargs.get("permission_id"))
        query = query.filter(CasbinRule.ptype == ptype)
        query = query.filter(
            CasbinRule.v0 == role_id, CasbinRule.v3 == permission_id
        )

        r = query.delete()
        self._commit()

        # return True if r > 0 else False

    def __del__(self):
        self._session.close()


adapter = Adapter(engine)
rbac = casbin.Enforcer("./rbac_model.conf", adapter)
