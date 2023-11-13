from common.customExceptions import *
from sqlalchemy.sql import text
from sqlalchemy import exc, desc, inspect



class Paginate:
    # default setting
    __default_page_size = 8
    __default_page_number = 1
    __default_offset_value = 0

    __page_size = None
    __page_number = None
    __offset_value = 0
    __sort_key = None
    __sort_order = None
    __sort_key_order = None
    __total_records = 0
    __total_pages = 0
    __prev_page = None
    __next_page = None


    def __init__(self, ModelClass, **kwargs):
        
        # page size and page number 
        # do not change assignment order
        self.__page_size = self.__getPageSize(kwargs.get('page_size'))
        self.__page_number = self.__getPageNumber(kwargs.get('page_number'))
        self.__total_records = kwargs.get('total_data_count')
        self.__offset_value = self.__getOffset()
        self.__total_pages = self.__getTotalPages()
        self.__prev_page = self.__getPrevPage()
        self.__next_page = self.__getNextPage()

        self.__sort_key = kwargs.get('sort_key') if kwargs.get('sort_key') else None
        self.__sort_order = kwargs.get('sort_order') if kwargs.get('sort_order') else None
        self.__sort_key_order = self.__getSortKeyOrder(ModelClass)

    def __getPageSize(self,input_page_size):
        # if no pageSize set then set default page size 8
        per_page = self.__default_page_size
        if input_page_size is not None and int(input_page_size) == 0:
            per_page = None
        elif input_page_size and int(input_page_size) > 0:
            per_page = int(input_page_size)
        else:
            pass

        return per_page
    

    def __getPageNumber(self,input_page_number):
        page_number = self.__default_page_number
        if input_page_number and int(input_page_number) > 0:
            page_number = int(input_page_number)
            
        return page_number 

    def __getOffset(self) -> int:
        offset_value = self.__default_offset_value
        if self.__page_number and self.__page_size:
            offset_value = (self.__page_number - 1) * self.__page_size
        
        return offset_value


    def __getSortKeyOrder(self,ModelClass):

        table_cols = inspect(ModelClass)
        table_cols_list = [c_attr.key for c_attr in table_cols.mapper.column_attrs]
        
        #check if input sort_key exists in table
        if self.__sort_key not in table_cols_list or self.__sort_order not in ('asc','desc'):
            sort_key_order = None
        else:
            sort_key_order = text(f"{self.__sort_key} {self.__sort_order}")
        
        return sort_key_order

    def __getTotalPages(self) ->int:

        try:
            total_pages = self.__total_records // self.__page_size

            if self.__total_records % self.__page_size:
                total_pages = total_pages + 1
        except Exception as ex:
            # print(f"__getTotalPages():{ex}")
            pass
        
        finally:
            total_pages = 0

        return total_pages


    # need to update
    def __getNextPage(self) -> int:
        total_pages = self.__total_pages
        next_page = self.__page_number + 1
        next_page = next_page if next_page <= total_pages else None
        return next_page

    def __getPrevPage(self) -> int:
        prev_page = None if self.__page_number <= 1 else self.__page_number - 1
        return prev_page


    def _getPaginationSetting(self) -> dict:
        current_pagination_setting = {}
        current_pagination_setting['page_size'] = self.__page_size
        current_pagination_setting['page_number'] = self.__page_number
        current_pagination_setting['total_records'] = self.__total_records
        current_pagination_setting['offset_value'] = self.__offset_value
        current_pagination_setting['total_pages'] = self.__total_pages
        current_pagination_setting['prev_page'] = self.__prev_page
        current_pagination_setting['next_page'] = self.__next_page
        current_pagination_setting['sort_key'] = self.__sort_key
        current_pagination_setting['sort_order'] = self.__sort_order
        current_pagination_setting['sort_key_order'] = self.__sort_key_order

        return current_pagination_setting

        
    
    def _getResponseAttribute(self) -> dict:
        response_attributes = {}
        response_attributes['pageSize'] = self.__page_size
        response_attributes['pageNumber'] = self.__page_number
        response_attributes['totalRecords'] = self.__total_records
        response_attributes['totalPages'] = self.__total_pages
        response_attributes['prevPage'] = self.__prev_page
        response_attributes['nextPage'] = self.__next_page

        return response_attributes
