FROM lambci/lambda:build-python3.8

# Update all
RUN yum -y update
# Install your dependencies
RUN yum -y install libpq-dev postgresql-devel python-psycopg2 postgresql-libs
