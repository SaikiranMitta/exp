import os

import boto3


class Boto3Client:
    def getBoto3Client(self):
        client = boto3.client(
            "cognito-idp",
            region_name=os.getenv("COGNITO_REGION_NAME"),
        )
        return client
