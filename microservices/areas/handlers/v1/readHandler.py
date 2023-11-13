import json

from microservices.accounts.services.accountsService import Account


def readhandler(event, context):

    account = Account()
    response = account.getAccountDetails(**event)
    return response
