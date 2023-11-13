"""
-*- coding: utf-8 -*-
========================
AWS Lambda
========================
Contributor: Chirag Rathod (Srce Cde)
========================
"""

import logging
import os

import jwt
from jwt import PyJWKClient

try:
    region = os.getenv("COGNITO_REGION_NAME")
    userPoolId = os.getenv("USER_POOL_ID")
    url = f"https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json"
    app_client = os.getenv("CLIENT_ID")
    
    print("Region: ",region)
    print("UserPoolId: ",userPoolId)
    print("URL: ",url)
    print("AppClient: ", app_client)

    # fetching jwks
    jwks_client = PyJWKClient(url)
except Exception as e:
    logging.error(e)
    raise ("Unable to download JWKS")


def return_response(isAuthorized, other_params={}):
    return {"isAuthorized": isAuthorized, "context": other_params}


def handler(event, context):
    try:
        # fetching access token from event
        token = event["headers"]["authorization"]
        token = str.replace(str(token), "Bearer ", "")

        # token = 'eyJraWQiOiJtUG1iZE50aHljdlFTMFRDRmRPNTZZVGFUSUxITWNjZzdxQ2dxVzR6b25rPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJiMjRhZjIyYy0yNjAyLTRlZmYtOTA5Yy1lMzU2MDBlNDIxYWYiLCJjb2duaXRvOmdyb3VwcyI6WyJBZG1pbiJdLCJpc3MiOiJodHRwczpcL1wvY29nbml0by1pZHAudXMtZWFzdC0xLmFtYXpvbmF3cy5jb21cL3VzLWVhc3QtMV9zT1RhMzA5SUUiLCJjbGllbnRfaWQiOiI2a2tqZGU5cjNsMmZzbThsbGF0YW8zcnE1ZSIsIm9yaWdpbl9qdGkiOiIwY2UyMzk3MS02MmRlLTRiMmUtOTZmNS1hOGI3OWJkMTkyZWYiLCJldmVudF9pZCI6IjJhYjRhZDY5LWFlN2EtNGJiOS05YWI1LTJmZGNhMWU1MjBiNSIsInRva2VuX3VzZSI6ImFjY2VzcyIsInNjb3BlIjoiYXdzLmNvZ25pdG8uc2lnbmluLnVzZXIuYWRtaW4iLCJhdXRoX3RpbWUiOjE2NTg0NzA2NTMsImV4cCI6MTY1ODQ5Mzg5OSwiaWF0IjoxNjU4NDkwMjk5LCJqdGkiOiJhZGU1MTdiMy1jMjkwLTQ4YzgtYjhiOC1jNzA3MGM3MzdiMDYiLCJ1c2VybmFtZSI6InBhcmFzaGFyLnNhbmdsZSJ9.nU4qoLCBTjyEOaNPpygyVIOImOKmSONDsGvNdmXI8rayi9HeDgOeHGS6HnDLri3CFWHUzzLEPbampTbfRbLJsiKrJoGSTImzWpsLAgMu-XWftURjTiU9sQbxrL_3FPY_mzRPME0Xh6UyhhbesgX0n-tTTSQdnOEA18r-b_cU8VxaYChmvkVzHw0F7imPg2ut-Qpio62lm4B3OrIypfISnV_S8Nf1SgPegqGndJ_0OKNEeszUig838vU_JlgRraAqkl9klGtbvvy27WsCjQ4bLy4PitMIbjN1XxUjdYAISBOowlYsBgsAkXHVR6M9y2R8MQVa8ACHluuVZUugfMzBCQ'
        # print(f'token : {token}')

        # check token structure
        if len(token.split(".")) != 3:
            return return_response(isAuthorized=False, other_params={})
    except Exception as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})

    try:
        # get unverified headers
        headers = jwt.get_unverified_header(token)
        # get signing key
        signing_key = jwks_client.get_signing_key_from_jwt(token)
        # validating exp, iat, signature, iss
        data = jwt.decode(
            token,
            signing_key.key,
            algorithms=[headers.get("alg")],
            options={
                "verify_signature": True,
                "verify_exp": True,
                "verify_iat": True,
                "verify_iss": True,
                "verify_aud": False,
            },
        )

    except jwt.InvalidTokenError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except jwt.DecodeError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except jwt.InvalidSignatureError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except jwt.ExpiredSignatureError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except jwt.InvalidIssuerError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except jwt.InvalidIssuedAtError as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})
    except Exception as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})

    try:
        # verifying audience...use data['client_id'] if verifying an access token else data['aud']
        if app_client != data.get("client_id"):
            return return_response(isAuthorized=False, other_params={})
    except Exception as e:
        logging.error(e)
        return return_response(isAuthorized=False, other_params={})

    # try:
    #     # token_use check
    #     if data.get("token_use") != "access":
    #         return return_response(isAuthorized=False, other_params={})
    # except Exception as e:
    #         logging.error(e)
    #     return return_response(isAuthorized=False, other_params={})

    # try:
    #     # scope check
    #     if "openid" not in data.get("scope").split(" "):
    #         return return_response(isAuthorized=False, other_params={})
    # except Exception as e:
    #         logging.error(e)
    #     return return_response(isAuthorized=False, other_params={})
    response = return_response(isAuthorized=True, other_params=data)
    return response


# x = handler({'headers': {'authorization': 'eyJraWQiOiJtUG1iZE50aHljdlFTMFRDRmRPNTZZVGFUSUxITWNjZzdxQ2dxVzR6b25rPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiJiMjRhZjIyYy0yNjAyLTRlZmYtOTA5Yy1lMzU2MDBlNDIxYWYiLCJjb2duaXRvOmdyb3VwcyI6WyJBZG1pbiJdLCJpc3MiOiJodHRwczpcL1wvY29nbml0by1pZHAudXMtZWFzdC0xLmFtYXpvbmF3cy5jb21cL3VzLWVhc3QtMV9zT1RhMzA5SUUiLCJjbGllbnRfaWQiOiI2a2tqZGU5cjNsMmZzbThsbGF0YW8zcnE1ZSIsIm9yaWdpbl9qdGkiOiIwY2UyMzk3MS02MmRlLTRiMmUtOTZmNS1hOGI3OWJkMTkyZWYiLCJldmVudF9pZCI6IjJhYjRhZDY5LWFlN2EtNGJiOS05YWI1LTJmZGNhMWU1MjBiNSIsInRva2VuX3VzZSI6ImFjY2VzcyIsInNjb3BlIjoiYXdzLmNvZ25pdG8uc2lnbmluLnVzZXIuYWRtaW4iLCJhdXRoX3RpbWUiOjE2NTg0NzA2NTMsImV4cCI6MTY1ODQ5Mzg5OSwiaWF0IjoxNjU4NDkwMjk5LCJqdGkiOiJhZGU1MTdiMy1jMjkwLTQ4YzgtYjhiOC1jNzA3MGM3MzdiMDYiLCJ1c2VybmFtZSI6InBhcmFzaGFyLnNhbmdsZSJ9.nU4qoLCBTjyEOaNPpygyVIOImOKmSONDsGvNdmXI8rayi9HeDgOeHGS6HnDLri3CFWHUzzLEPbampTbfRbLJsiKrJoGSTImzWpsLAgMu-XWftURjTiU9sQbxrL_3FPY_mzRPME0Xh6UyhhbesgX0n-tTTSQdnOEA18r-b_cU8VxaYChmvkVzHw0F7imPg2ut-Qpio62lm4B3OrIypfISnV_S8Nf1SgPegqGndJ_0OKNEeszUig838vU_JlgRraAqkl9klGtbvvy27WsCjQ4bLy4PitMIbjN1XxUjdYAISBOowlYsBgsAkXHVR6M9y2R8MQVa8ACHluuVZUugfMzBCQ'}}, {})
# print(x)
