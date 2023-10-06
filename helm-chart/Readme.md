## SOC Backend API

This helm chart is for spinning up soc-backend-api cluster. It spins up 5 instances of soc-backend-api by default.

soc-backend-api pods are spread accross the spot and reserved instances, with preference to spot instances.

Image name - registry.iac.com:5000/soc-backend-api
****
To set tag edit values.yaml and update the property image.tag

Environment Variable | Value
---------------------|------
S3_CAMEL_DELAY|                        25
S3_CAMEL_MAX_MESSAGES_PER_POLL|        2
S3_CAMEL_CONCURRENT_CONSUMERS|         50
TRUSTAR_USERNAME|                      d129d4e0-39ef-40c9-8b64-28c8369361df
TRUSTAR_PASSWORD|                      B9bEApQM9p9h7qyHyQAifnx4
BOOTSTARP_SERVERS|                     soc-kafka-0.soc-kafka.default.svc.cluster.local.:9092,soc-kafka-1.soc-kafka.default.svc.cluster.local.:9092,soc-kafka-2.soc-kafka.default.svc.cluster.local.:9092,soc-kafka-3.soc-kafka.default.svc.cluster.local.:9092,soc-kafka-4.soc-kafka.default.svc.cluster.local.:9092,soc-kafka-5.soc-kafka.default.svc.cluster.local.:9092
RAW_LOG_TOPIC|                         raw-logs-4
LOG_TOPIC|                             logs
SMTP_HOST|                             smtp.office365.com
SMTP_USERNAME|                         socadminalerts@iac.com
SMTP_FROM_EMAIL|                       socadminalerts@iac.com
SMTP_PASSWORD|                         <set to the key 'smtp_password' in secret 'soc-backend-api'> 
KUDU_MASTERS|                          kudu-master-0.kudu-master-headless.default.svc.cluster.local:7051,kudu-master-1.kudu-master-headless.default.svc.cluster.local:7051,kudu-master-2.kudu-master-headless.default.svc.cluster.local:7051
AKKA_CLUSTER_HOST|                     0.0.0.0
AKKA_CLUSTER_PORT|                     2551
PORT|                                  8084
KEYCLOAK_HOST|                         https://soc.iac.com/auth
MYSQL_HOST|                            mysql-backend
MYSQL_DATABASE|                        iac
MYSQL_USER|                            root
MYSQL_PASSWORD|                        <set to the key 'mysql_password' in secret 'soc-backend-api'>           
AWS_ACCESS_KEY|                        <set to the key 'AWS_ACCESS_KEY' in secret 'soc-backend-api'>           
AWS_SECRET_KEY|                        <set to the key 'AWS_SECRET_KEY' in secret 'soc-backend-api'>           
KEYCLOAK_ADMIN_PASSWORD|               <set to the key 'KEYCLOAK_ADMIN_PASSWORD' in secret 'soc-backend-api'>  
PRESTO_ANALYST_HOST|                   haproxy-presto-analyst
PRESTO_ANALYST_PORT|                   8080
PRESTO_RULES_HOST|                     haproxy-presto
PRESTO_RULES_PORT|                     8080
PRESTO_CATLOG|                         hive
PRESTO_USER|                           analyst
PRESTO_SCHEMA|                         default
PRESTO_SOURCE|                         analyst
PRESTO_RULES_USER|                     analyst
PRESTO_RULES_SOURCE|                   rules
PRESTO_ANALYST_USER|                   analyst
PRESTO_ANALYST_SOURCE|                 analyst
ENVIRONMENT|                           production
LUMBERJACK_KEYSTORE_PASSWORD|          diChiKuchop7YiFr@ruw
LUMBERJACK_EXCLUDE_STRING|             true
STAGE_1_PARALLELISM|                   10
K8S_SELECTOR|                          app.kubernetes.io/name=soc-backend-api
K8S_MANAGEMENT_PORT|                   management
AKKA_MANAGEMENT_HOSTNAME|              status.podIP
AKKA_CLUSTER_HOST|                     status.podIP
CLIENT_URL|                            https://soc.iac.com
MAX_CONSUMER_INSTANCES_PER_NODE|       3
MAX_PRODUCER_INSTANCES_PER_NODE|       10
MAX_LOG_PRODUCER_INSTANCES_PER_NODE|   10
MAX_STORE_MANAGER_INSTANCES_PER_NODE|  50
MAX_ENRICHMENT_INSTANCES_PER_NODE|     10
MAX_NORMALIZATION_INSTANCES_PER_NODE|  5
NORMALIZATION_INSTANCES|               5
DISABLE_S3_INGESTION|                  false
RAW_LOG_CONSUMER_GROUP|                soc-backend-consumer-18
CAMEL_KAFKA_NUMBER_OF_CONSUMERS|       10
CAMEL_KAFKA_MAX_POLL_RECORDS|          5
CAMEL_KAFKA_AGGREGATION_GROUP_SIZE|    5
CAMEL_KAFKA_HEARTBEAT_INTERVAL_MS|     10000
CAMEL_KAFKA_SESSION_TIMEOUT_MS|        30000
MINIMIZE_S3_LOAD|                      true
API_DISPATCHER_MIN_PARALLELISM|        8
API_DISPATCHER_MAX_PARALLELISM|        24
API_DISPATCHER_PARALLELISM_FACTOR|     2.0
API_DISPATCHER_THROUGHPUT|             1
API_DISPATCHER_THREAD_POOL_SIZE|       40
AST_FIELDS|                            id,message,type,timestamp
DAILYBEAST_AWS_ACCESS_KEY|             <set to the key 'DAILYBEAST_AWS_ACCESS_KEY' in secret 'soc-backend-api'>  
DAILYBEAST_AWS_SECRET_KEY|             <set to the key 'DAILYBEAST_AWS_SECRET_KEY' in secret 'soc-backend-api'>  

---
**NOTE**

Set your current working directory as ***infra/helm-charts*** inside the repo.

---


Command to install soc-backend-api in the cluster (**by default 5 replicas are installed**).
Just make sure that mysql-backend is already running before installing soc-backend-api
 
- >**`helm secrets install --name=soc-backend-api -f soc-backend-api/helm_vars/secrets.yaml soc-backend-api/`**

Command to install soc-backend-api with specific replicas in the cluster (**replace n with the number of replicas needed**)

- >**`helm secrets install --name=soc-backend-api --set replicaCount=n -f soc-backend-api/helm_vars/secrets.yaml soc-backend-api/`**

Command to delete soc-backend-api from the cluster

- >**`helm del soc-backend-api --purge`**


Commands to check if soc-backend-api is running  (**use one of the below commands**)

- >**`kubectl get po | grep soc-backend-api`**

- >**`kubectl get po -l app.kubernetes.io/instance=soc-backend-api`**


Commands to check CPU and RAM utilisation (**use one of the below commands**)

- >**`kubectl top po | grep soc-backend-api`**

- >**`kubectl top po -l app.kubernetes.io/instance=soc-backend-api`**


Command to view secrets

- >**`helm secrets view soc-backend-api/helm_vars/secrets.yaml`**


Command to update secrets

- >**`helm secrets edit soc-backend-api/helm_vars/secrets.yaml`**


Commands to see logs (**use either ktail or kubectl**)

 - ktail command will always start from the latest logs (**replace n by the replica number**) 
    - >**`ktail soc-backend-api-n`**

- ktail command to view consolidated logs for all the replicas

    - >**`ktail -l app.kubernetes.io/instance=soc-backend-api`**

 - kubectl command will start from earliest and will follow latest logs by passing -f flag (**replace n by the replica number**) 

    - >**`kubectl logs -f soc-backend-api-n`**

 - kubectl command to view consolidated logs for all the replicas

    - >**`kubectl logs -f -l app.kubernetes.io/instance=soc-backend-api`**

Command to do rolling upgrade of the soc-backend-api pods

- >**`helm upgrade soc-backend-api soc-backend-api/`**

Command to describe the soc-backend-api pods (**replace n with replica number**)

- >**`kubectl describe po soc-backend-api-n`**

Command to list kubernetes service related to soc-backend-api 

- >**`kubectl get svc -l app.kubernetes.io/instance=soc-backend-api`**

Command to exec into any of the soc-backend-api pods (**replace n with replica number**)

- >**`kubectl exec -it soc-backend-api-n bash`**



Related Guides:-

- [Mysql Backend](../mysql-backend)
