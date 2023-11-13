# sub = "alice"  # the user that wants to access a resource.
# obj = "data1"  # the resource that is going to be accessed.
# act = "write"  # the operation that the user performs on the resource.

import uuid
from datetime import datetime

from numpy import rec

# from casbinModel import e
#     # permit alice to read data1
from adapter import rbac
from database.dbConnection import session
from rolePolicyModels import RolePolicy as RolePolicyModel

a = datetime.now()
a = str(a)
# rbac.add_policy("2", "assessment", "write", "34", "45", "2333", a )
# rbac.add_policy("1", "assessment", "read", "35")
field_values = ("1", "34")
# print(rbac.remove_filtered_policy(field_values))
def add(num1, num2):
    record = (
        session.query(RolePolicyModel)
        .filter(
            RolePolicyModel.id
            == "f3c436c7-c482-4676-a815-c603651b1835"
        )
        .first()
    )

    c = uuid.UUID("c80847c7-3c46-4e08-8955-c11b97a63db8").hex
    isok = rbac.enforce(
        (record.id),
        (record.resource),
        (record.action),
    )
    if isok:
        print("valid")
    # if e.enforce(sub, obj, act):
    #     return num1+num2
    else:
        print("Invalid Access")


(add(23, 34))

# rbac.load_policy()
