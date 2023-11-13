import logging
import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import backref, relation, sessionmaker
from sqlalchemy_utils import create_database, database_exists
from sqlalchemy_utils.functions.orm import naturally_equivalent

dotenv_path = Path("./.env")
load_dotenv(dotenv_path=dotenv_path)
from sqlalchemy.orm import relationship

log = logging.getLogger(__name__)
print("###" * 8)
print(os.getcwd())
print(os.getenv("PG_USER"))


class Database:
    def get_database(self):
        """
        Connects to database.
        Returns:
            engine
        """
        try:
            engine = self.get_engine_from_settings()
            log.info("Connected to PostgreSQL database!")
        except IOError:
            log.exception("Failed to get database connection!")
            return None, "fail"

        return engine

    def get_engine_from_settings(self):
        """
        Sets up database connection from en file
        Input:
            Dictionary containing pghost, pguser, pgpassword, pgdatabase and pgport.
        Returns:
            Call to get_database returning engine
        """
        from pathlib import Path

        from dotenv import load_dotenv

        dotenv_path = Path("./.env")
        load_dotenv(dotenv_path=dotenv_path)
        if (
            not os.getenv("PG_USER")
            and not os.getenv("PG_PASSWORD")
            and not os.getenv("PG_HOST")
            and not os.getenv("PG_DB")
            and not os.getenv("PG_PORT")
        ):

            raise Exception("Bad config file")

        return self.get_engine(
            os.getenv("PG_USER"),
            os.getenv("PG_PASSWORD"),
            os.getenv("PG_HOST"),
            os.getenv("PG_PORT"),
            os.getenv("PG_DB"),
        )

    def get_engine(self, user, passwd, host, port, db):
        """
        Get SQLalchemy engine using credentials.
        Input:
            db: database name
            user: Username
            host: Hostname of the database server
            port: Port number
            passwd: Password for the database
        Returns:
            Database engine
        """

        url = "postgresql://{user}:{passwd}@{host}:{port}/{db}".format(
            user=user, passwd=passwd, host=host, port=port, db=db
        )
        if not database_exists(url):
            create_database(url)
        engine = create_engine(url, pool_size=2, echo=False)
        return engine

    def get_session(self):
        """
        Return an SQLAlchemy session
        Input:
            engine: an SQLAlchemy engine
        """
        engine = self.get_database()
        # conn = engine.connect()
        # results = conn.execute('SELECT * from  comment;')
        # print(results.fetchall())   # [(100, 'Joe'), (100, 'Jane')]
        # conn.close()
        session = sessionmaker(bind=engine)()
        # session = Session()
        return session, engine


database = Database()
session, engine = database.get_session()
