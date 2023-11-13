# TENET-BACKEND

Automating the Tenet process

## Setup


### Dev & Code Environment 

1. Install the following packages:
   1. [Git](https://git-scm.com/downloads)
   2. [Vscode](https://code.visualstudio.com/download)
   3. [Docker](https://www.docker.com/products/docker-desktop) - Optional
   4. [Docker-compose](https://docs.docker.com/compose/install/) - Optional
   5. [NodeJS v16.3.0](https://github.com/nvm-sh/nvm#install--update-script)
   6. [PG Admin](https://www.pgadmin.org/download/)

2. **Setup Virtual Environment:**
   - Create a virtual environment:
     ```
     python -m venv venv
     ```
   - Activate the virtual environment:
     On Windows:
     ```
     venv\Scripts\activate
     ```
     On Unix or MacOS:
     ```
     source venv/bin/activate
     ```

3. **Install Dependencies:**
   - Install required packages:
     ```
     pip install -r requirements.txt
     ```

4. Known Issues

   1. Serverless cmd is not working -try with newer version of node and npm OR else try changing env variable in advanced setting.
by setting npm path where it is installed.
       * https://stackoverflow.com/questions/72674740/how-do-you-fix-npm-warn-config-global-global-local-are-deprecated-use
       * https://stackoverflow.com/questions/67970669/npm-install-errors-npm-warn-deprecated
       * https://stackoverflow.com/questions/51887616/windows-is-not-recognizing-serverless-as-internal-or-external-command

## Database Connection via Jump Server

To connect to the database through a jump server, use an SSH tunnel:

1. **Open a terminal** and run the following command:
   ```
   ssh -L localPort:dbHost:dbPort jumpUser@jumpHost -N
   ```
   Replace `localPort`, `dbHost`, `dbPort`, `jumpUser`, and `jumpHost` with your specific details.

2. **Connect to your database** using `localPort` on your local machine as if it's directly connected to the database.

## Backend Deployment

1. **Environment Variables:**
   - If the `.env` file is not available on the backend, run:
     ```
     ENV=prod sh create-environment-variables.sh
     ```

2. **Deploy with Serverless:**
   - Deploy the application using the Serverless framework:
     ```
     serverless deploy --stage prod --region us-west-2
     ```

### Project Dependencies


1. [Aws-Cli](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html)
   1. Command for Login `aws configure` 
   2. Refer to this [link](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-configure.html) for extra details
2. [Serverless](https://www.serverless.com/framework/docs/getting-started/)
   1. Deploy Project `sls deploy`
   2. Undeploy Project `sls remove`
