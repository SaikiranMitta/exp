import base64
from datetime import datetime

from openpyxl import Workbook
from openpyxl.writer.excel import save_virtual_workbook

from common.customExceptions import *
from common.responseBuilder import ResponseBuilder
from microservices.users.services.usersService import User


def listHandler(event, context):

    try:
        user = User()
        response_builder = ResponseBuilder()
        kwargs = {}
        kwargs = _checkAndCreateFunctionParametersDictionary(event)
        response, pagination = user.getUserList(**kwargs)
        download = kwargs.get("download")
        if download:
            workbook = Workbook()
            worksheet = workbook.active
            worksheet.title = "User Details"
            worksheet.column_dimensions['D'].width = 20
            worksheet.append(("PS No", "Name", 
                              "Current Project",
                              "Current Project Start Date",
                              "Last Audited Project", 
                              "Last Audited Project Score", 
                              "Past Project",
                              "Past Project Start Date",
                              "Past Project End Date"))
            
            # set column width
            for column in worksheet.columns:
                column_name = column[0].column_letter
                worksheet.column_dimensions[column_name].width = 15
                
            for result in response:
                last_audited_project = result.get("last_audited_project", None)
                last_audited_project_name, overall_score = None, None
                if last_audited_project:
                    last_audited_project_name = last_audited_project.get(
                        "name", None
                    )
                    assessment = result.get("last_audited_project").get(
                        "assessment", None
                    )
                    if assessment:
                        overall_score = assessment.get('overall_score', None)
                
                current_project_name = None
                current_project_start_date = None
                if result.get('current_project'):
                    current_project_name = result.get('current_project').get('name')
                    current_project_start_date = result.get('current_project_user').get('created_on')
                    current_project_start_date = datetime.strptime(current_project_start_date, 
                                                                   '%Y-%m-%d %H:%M:%S.%f').date()

                start_date , end_date = None, None
                project_project_user_tuple_list = result.get('project_project_user_tuple_list')
                if project_project_user_tuple_list:
                    for project_and_user_tuple in project_project_user_tuple_list:
                        project = project_and_user_tuple[0].as_dict()
                        project_user = project_and_user_tuple[1].as_dict()
                        if project_user:
                            start_date = datetime.strptime(project_user.get('created_on'), 
                                                           '%Y-%m-%d %H:%M:%S.%f')
                            if project_user.get('end_date'):
                                end_date = datetime.strptime(project_user.get('end_date'), 
                                                             '%Y-%m-%d %H:%M:%S.%f')
                        worksheet.append((result.get('ps_no'),
                                        result.get('name'),
                                        current_project_name,
                                        current_project_start_date,
                                        last_audited_project_name,
                                        overall_score, 
                                        project.get('name') if project else '',
                                        start_date.date() if start_date else '',
                                        end_date.date() if end_date else ''))
                else:
                    worksheet.append((result.get('ps_no'),
                                      result.get('name'),
                                      current_project_name,
                                      current_project_start_date,
                                      last_audited_project_name,
                                      overall_score, 
                                      None,
                                      None,
                                      None))

            workbook = save_virtual_workbook(workbook)
            workbook = base64.b64encode(workbook)
            response = {
                "statusCode": 200,
                "body": workbook.decode("utf-8"),
                "headers": {
                    "Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "Content-Disposition": "attachment; filename=User_details.xlsx",
                },
                "isBase64Encoded": True,
            }
        else:
            response = response_builder.buildResponse(
                None, response, None, **{"pagination": pagination}
            )

    except Exception as error:
        response = response_builder.buildResponse(error)

    return response


def _checkAndCreateFunctionParametersDictionary(event):

    kwargs = {}
    if not event.get("requestContext"):
        raise AnyExceptionHandler("Authentication token not present")
    if not event.get("requestContext", {}).get("authorizer"):
        raise AnyExceptionHandler("Authentication token not present")

    # filters
    status = event.get("queryStringParameters", {}).get("status", None)
    role = event.get("queryStringParameters", {}).get("role", None)

    # search
    search = event.get("queryStringParameters", {}).get("search", None)

    # download user list
    download = event.get("queryStringParameters", {}).get("download", None)

    if download:
        if download not in ["true", "false"]:
            raise InvalidAttribute("Download", "boolean value")
        if download == "true":
            download = True
        else:
            download = False

    # pagination inputs
    page_size = event.get("queryStringParameters", {}).get("pageSize", None)
    page_number = event.get("queryStringParameters", {}).get("pageNumber", None)
    sort_key = event.get("queryStringParameters", {}).get("sortKey", None)
    sort_order = event.get("queryStringParameters", {}).get("sortOrder", None)

    authenticated_user_id = (
        event.get("requestContext", {})
        .get("authorizer", {})
        .get("lambda", {})
        .get("sub")
    )

    authenticated_user_roles = (
        event.get("requestContext", {})
        .get("authorizer", {})
        .get("lambda", {})
        .get("cognito:groups")
    )

    if not isinstance(authenticated_user_roles, list):
        authenticated_user_roles = [authenticated_user_roles]
    kwargs["authenticated_user_id"] = authenticated_user_id
    kwargs["authenticated_user_roles"] = authenticated_user_roles

    # filters
    kwargs["status"] = status
    kwargs["role"] = role
    kwargs["search"] = search

    # download excel sheet
    kwargs["download"] = download

    # pagination attributes
    kwargs["page_size"] = page_size
    kwargs["page_number"] = page_number
    kwargs["sort_key"] = sort_key
    kwargs["sort_order"] = sort_order

    return kwargs
