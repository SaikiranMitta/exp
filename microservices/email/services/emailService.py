import os
import boto3
import json
from botocore.exceptions import ClientError
from dotenv import load_dotenv
from models.userModels import User as UserModel
from models.database.dbConnection import session
from common.customExceptions import AttributeNotPresent
from common.responseBuilder import ResponseBuilder

load_dotenv()  # read .env file, if it exists


def sendEmail(from_add, to_add, email_subject, email_body):
    sender = from_add  # must be verified in AWS SES Email
    recepient = to_add  # must be verified in AWS SES Email
    aws_region = os.getenv("REGION_NAME")
    # The subject line for the email.
    subject = email_subject
    # The HTML body of the email.
    html_body = email_body
    # The character encoding for the email.
    CHARSET = "UTF-8"
    # Create a new SES resource and specify a region.
    client = boto3.client('ses', region_name=aws_region)

    # Try to send the email.
    try:
        # Provide the contents of the email.
        response = client.send_email(
            Destination={
                'ToAddresses': [
                    recepient,
                ],
            },
            Message={
                'Body': {
                    'Html': {
                        'Data': html_body
                    },
                },
                'Subject': {
                    'Data': subject
                },
            },
            Source=sender
        )
    # Display an error if something goes wrong.
    except ClientError as e:
        print('exception is', e.response['Error']['Message'])
    else:
        print("Email sent! Message ID: ", response['MessageId'])
        return response['MessageId']


def checkCreateAssessmentParameters(**kwargs):
    required_parameters = {
        "user_id": "user_id",
        "message": "message",
        "description": "description",
    }
    for key, value in required_parameters.items():
        if key not in kwargs:
            raise AttributeNotPresent(value)


def sendEmailToUser(**kwargs):
    """
        Send email to users in the system.
        Input: Event data
        Output: MessageId of mail
    """
    try:
        response_builder = ResponseBuilder()
        checkCreateAssessmentParameters(**kwargs)
        event_data = kwargs.get("event_data")
        for record in event_data:
            body_dict = json.loads(record['body'])
            user_info = (session.query(UserModel).filter(
                UserModel.id == body_dict['user_id']).first())
            
            print("sendEmailToUser user_info:: ", user_info.username)
            email_subject = body_dict['message']
            email_body = body_dict['description']
            response = sendEmail(os.getenv("NOTIFICATION_MAIL_SENDER"),
                                 user_info.username,
                                 email_subject, email_body)
        response = response_builder.buildResponse(None, response, 201)
    except Exception as error:
        response = response_builder.buildResponse(error)
    return response
