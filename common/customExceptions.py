class URLAttributeNotFound(Exception):

    # Constructor or Initializer
    def __init__(self, attribute):
        self.message = f"{attribute} attribute not present in the URL"


class AttributeNotPresent(Exception):
    def __init__(self, attribute):
        self.message = f"{attribute} attribute not found"


class RequestBodyNotFound(Exception):

    # Constructor or Initializer
    def __init__(self):
        self.message = f"Request body not found"


class RequestBodyAndURLAttributeNotSame(Exception):
    def __init__(self, attribute):
        self.message = f"{attribute} present in URL and request body do not match"


class AttributeIdNotFound(Exception):

    # Constructor or Initializer
    def __init__(self, attribute):
        self.message = f"{attribute} with the specified ID not found"


class RequestBodyAttributeNotFound(Exception):

    # Constructor or Initializer
    def __init__(self, attribute):
        self.message = f"{attribute} attribute not present in request body"


# class PathParameterNotFound(Exception):

#     # Constructor or Initializer
#     def __init__(self):
#         self.message = f"Path parameter not present in URL!"


class AnyExceptionHandler(Exception):
    def __init__(self, e):
        self.message = str(e)


class IncorrectFormat(Exception):
    def __init__(self, attribute):
        self.message = f"Incorrect format of {attribute} attribute"


class InvalidAttribute(Exception):
    def __init__(self, entity, attribute):
        self.message = f"Invalid {entity} {attribute}"


class AlreadyExists(Exception):

    # Constructor or Initializer
    def __init__(self, entity, attribute):
        self.message = f"{entity} with the given {attribute} already exists"
