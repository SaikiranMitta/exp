from database.dbConnection import engine, session
from dbModels import FunctionDescription as FunctionDescriptionModel

function_description = {
    "getAccountList": ("Account", "Read"),
    "getAccountDetails": ("Account", "Read"),
    "getProjectList": ("Project", "Read"),
    "createProject": ("Project", "Create"),
    "deleteProject": ("Project", "Delete"),
    "getProjectDetails": ("Project", "Read"),
    "updateProject": ("Project", "Write"),
    "createUser": ("User", "Create"),
    "deleteUser": ("User", "Delete"),
    "getUserList": ("User", "Read"),
    "getUserDetails": ("User", "Read"),
    "updateUserDetails": ("User", "Write"),
    "addUserRole": ("UserRole", "Create"),
    "deleteUserRole": ("UserRole", "Delete"),
    "getUserRoleList": ("UserRole", "Read"),
    "getSubareaList": ("Subarea", "Read"),
    "createRole": ("Role", "Create"),
    "deleteRole": ("Role", "Delete"),
    "getRoleList": ("Role", "Read"),
    "getRoleDetails": ("Role", "Read"),
    "updateRole": ("Role", "Write"),
    "createRolePolicy": ("RolePolicy", "Create"),
    "deleteRolePolicy": ("RolePolicy", "Delete"),
    "getRolePolicyList": ("RolePolicy", "Read"),
    "getRolePolicyDetails": ("RolePolicy", "Read"),
    "updateRolePolicy": ("RolePolicy", "Write"),
    "getAssessmentResults": ("Result", "Read"),
    "updateAssessmentGrades": ("Item", "Write"),
    "addProjectUser": ("ProjectUser", "Create"),
    "deleteProjectUser": ("ProjectUser", "Delete"),
    "getProjectUserList": ("ProjectUser", "Read"),
    "getProjectUserDetails": ("ProjectUser", "Read"),
    "getItemList": ("Item", "Read"),
    "updateAssessmenItemGrades": ("Item", "Write"),
    "createAssessment": ("Assessment", "Create"),
    "getAssessmentList": ("Assessment", "Read"),
    "getAssessmentDetails": ("Assessment", "Read"),
    "updateAssessmentStatus": ("Assessment", "Write"),
    "updateAssessmentResponses": ("AssessmentResponse", "Write"),
    "getAreaList": ("Area", "Read"),
    "getActivityList": ("Activity", "Read"),
    "createAccount": ("Account", "Create"),
    "updateAccount": ("Account", "Write"),
    "createDomain": ("Domain", "Create"),
    "getDomainDetails": ("Domain", "Read"),
    "getDomainList": ("Domain", "Read"),
}


to_be_created_function_objects = []

for key, value in function_description.items():
    if not session.query(FunctionDescriptionModel).filter(
        FunctionDescriptionModel.resource == value[0],
        FunctionDescriptionModel.name == key,
        FunctionDescriptionModel.action == value[1],
    ):
        function_description_object = FunctionDescriptionModel()
        function_description_object.name = key
        function_description_object.resource = value[0]
        function_description_object.action = value[1]
        to_be_created_function_objects.extend([function_description_object])
        # print(to_be_created_function_objects)
    session.add_all(to_be_created_function_objects)
    session.commit()

    # except Exception as ex:
    #     print("Exception")
    #     session.rollback()
