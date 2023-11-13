"""
    Program to seed Tenet checklist
    
    TO RUN:

    1. cd tenet/backend
    2. make sure .env has PGSQL variables & admin user id
"""

import sys
from pathlib import Path

cwd = str(Path.cwd())
sys.path.append(cwd)


import pandas as pd
from common.customExceptions import AnyExceptionHandler
from dotenv import load_dotenv

from checklistModels import Activity, Area, Checklist, Item, Subarea
from models.commonImports import *
from models.database.dbConnection import session

dotenv_path = Path("./.env")
load_dotenv(dotenv_path=dotenv_path)

user_id = os.getenv("COGNITO_ADMIN_ID")
# user_id = "8aa1329e-b7c5-41a5-acba-5cbb9fe3c774"


def UploadExcel():
    df = pd.read_excel("Version1.xlsx", sheet_name="with Importance of priority")
    checklist = Checklist(
        name="checklistV1",
        is_active=True,
        status="Published",
        comments="Uploaded via excel",
    )
    checklist.created_by = user_id
    # To be changed
    try:
        session.add(checklist)
        session.commit()
        session.refresh(checklist)
    except Exception as ex:
        session.rollback()
        raise AnyExceptionHandler(ex)
    checklist_id = checklist.id
    columns = list(df.columns)
    g = df.groupby("Area")
    c = g.groups.keys()

    for i in c:
        df_columns = g.get_group(i)
        print(df_columns.head())
        row_indexes = len(df_columns.index)
        activity_list = []
        area_id = None
        subarea_id = None
        item_id = None
        for i in range(row_indexes):

            if i == 0:

                name = df_columns.at[df_columns.index[i], "Area"]
                if (
                    session.query(Area)
                    .filter(Area.name == name, Area.checklist_id == checklist_id)
                    .first()
                    is None
                ):

                    area_weightage = df_columns.at[
                        df_columns.index[i], "Area\nWeightage"
                    ]
                    area_weightage = float(area_weightage)
                    checklist_id = checklist.id
                    print(f"checklist_id: {checklist_id}")
                    area = Area(
                        name=name,
                        weightage=area_weightage,
                        checklist_id=checklist_id,
                    )
                    area.created_by = user_id
                    #  To be changed

                    session.add(area)
                    session.commit()
                    session.refresh(area)
                    area_id = area.id

            if area_id is not None:

                name = df_columns.at[df_columns.index[i], "Sub-areas"]
                if (
                    session.query(Subarea)
                    .filter(Subarea.name == name, Subarea.area_id == area_id)
                    .first()
                    is None
                ):

                    subarea_weightage = float(
                        df_columns.at[df_columns.index[i], "Subarea\nWeightage"]
                    )
                    area_id = area_id
                    subarea = Subarea(
                        area_id=area_id, name=name, weightage=subarea_weightage
                    )
                    subarea.created_by = user_id
                    #  To be changed
                    session.add(subarea)
                    session.commit()
                    session.refresh(subarea)
                    subarea_id = subarea.id
                name = df_columns.at[df_columns.index[i], "Item"]
                if (
                    session.query(Item)
                    .filter(Item.subarea_id == subarea_id, Item.name == name)
                    .first()
                    is None
                ):

                    # item_details = df_columns.at[df_columns.index[i], 'Item']
                    name = name
                    item_weightage = float(
                        df_columns.at[df_columns.index[i], "Item\nWeightage"]
                    )
                    effective_weightage = float(
                        df_columns.at[df_columns.index[i], "Effective \nWeightage"]
                    )
                    item = Item(
                        subarea_id=subarea_id,
                        name=name,
                        weightage=item_weightage,
                        effective_weightage=effective_weightage,
                    )
                    item.created_by = user_id
                    #  To be changed
                    session.add(item)
                    session.commit()
                    session.refresh(item)
                    item_id = item.id

                if item_id and subarea_id is not None:
                    name = df_columns.at[df_columns.index[i], "Activity "]
                    if (
                        session.query(Activity)
                        .filter(Activity.item_id == item_id, Activity.name == name)
                        .first()
                        is None
                    ):

                        name = df_columns.at[df_columns.index[i], "Activity "]
                        activity_importance = df_columns.at[
                            df_columns.index[i], "Importance of Priority"
                        ]
                        activity = Activity(
                            name=name,
                            item_id=item_id,
                            importance=activity_importance,
                        )
                        activity.created_by = user_id
                        #  To be changed
                        session.add(activity)
                        session.commit()

            #     activity_list.append(activity)
            # if not activity_list:

            #     session.add_all(activity_list)
            #     session.commit()
        area_id = None
        item_id = None
        subarea_id = None

        # session.add_all(area_list)
        # session.commit()


UploadExcel()
