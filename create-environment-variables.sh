#!bin/bash

touch .env

echo CLIENT_ID=$(aws ssm get-parameter --name /tenet/$ENV/client_id --query "Parameter.Value" --output text) >> .env
echo COGNITO_ADMIN_ID=$(aws ssm get-parameter --name /tenet/$ENV/cognito_admin_id --query "Parameter.Value" --output text) >> .env
echo NOTIFICATION_MAIL_SENDER=$(aws ssm get-parameter --name /tenet/$ENV/notification_mail_sender --query "Parameter.Value" --output text) >> .env
echo PG_DB=$(aws ssm get-parameter --name /tenet/$ENV/pg_db --query "Parameter.Value" --output text) >> .env
echo PG_HOST=$(aws ssm get-parameter --name /tenet/$ENV/pg_host --query "Parameter.Value" --output text) >> .env
echo PG_PASSWORD=$(aws ssm get-parameter --name /tenet/$ENV/pg_password --query "Parameter.Value" --output text) >> .env
echo PG_PORT=$(aws ssm get-parameter --name /tenet/$ENV/pg_port --query "Parameter.Value" --output text) >> .env
echo PG_USER=$(aws ssm get-parameter --name /tenet/$ENV/pg_user --query "Parameter.Value" --output text) >> .env
echo REGION_NAME=$(aws ssm get-parameter --name /tenet/$ENV/region_name --query "Parameter.Value" --output text) >> .env
echo USER_POOL_ID=$(aws ssm get-parameter --name /tenet/$ENV/user_pool_id --query "Parameter.Value" --output text) >> .env
echo EMAIL_QUEUE_URL=$(aws ssm get-parameter --name /tenet/$ENV/email_queue_url --query "Parameter.Value" --output text) >> .env
echo SNS_PROJECT_CREATED_ARN=$(aws ssm get-parameter --name /tenet/$ENV/sns_project_created_arn --query "Parameter.Value" --output text) >> .env
echo SNS_ASSESSMENT_CREATED_ARN=$(aws ssm get-parameter --name /tenet/$ENV/sns_assessment_created_arn --query "Parameter.Value" --output text) >> .env
echo SQS_GRADE_CALCULATION_QUEUE_URL=$(aws ssm get-parameter --name /tenet/$ENV/sqs_grade_calculation_queue_url --query "Parameter.Value" --output text) >> .env
echo AWS_COGNITO_REGION=$(aws ssm get-parameter --name /tenet/$ENV/cognito_pool_region --query "Parameter.Value" --output text) >> .env
echo COGNITO_REGION_NAME=$(aws ssm get-parameter --name /tenet/$ENV/cognito_pool_region --query "Parameter.Value" --output text) >> .env