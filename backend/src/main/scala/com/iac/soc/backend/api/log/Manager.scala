package com.iac.soc.backend.api.log

//import java.util.Calendar

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.iac.soc.backend.api.common.mapping.LogFormat._
import com.iac.soc.backend.api.common.mapping.LogMapping._
import com.iac.soc.backend.api.common.mapping.QueryResponse
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.{JsonSupport, OrganizationService}
import com.iac.soc.backend.utility.ASTUtility
import com.ibm.icu.util.Calendar
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import spray.json._

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Manager {

  final case class getLogs(auth_details: Claims, params: LogsPostRequest);

  final case class getLogsByQueryId(auth_details: Claims, id: String, page: Int)

  final case class getLogsQueryStats(auth_details: Claims, params: LogsPostStatsRequest)

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging with JsonSupport {

  import Manager._

  implicit val system: ActorSystem = context.system

  implicit val materializer = ActorMaterializer()

//  implicit val executionContext = system.dispatcher
  implicit val executionContext = system.dispatchers.lookup("api-dispatcher")

  val config = ConfigFactory.load()

  val limit = config.getString("ast.default-query-rows-limit")

  val logService: Repository = new Repository()

  def formatLogResults(rawResults: QueryResponse): ListBuffer[Map[String, Any]] = {

    val rawResultSize = rawResults.data.get.size

    val perBatchLogs = config.getInt("logs.parallel-batch-logs")

    val batchSize = Math.round(rawResults.data.get.size / perBatchLogs)

    if (rawResults.data.get.size > perBatchLogs) {

      val resultBatch1 = Future {

        prepareKeyValues(rawResults, 0, batchSize)

      }

      val resultBatch2 = Future {

        prepareKeyValues(rawResults, batchSize, batchSize * 2)

      }

      val resultBatch3 = Future {

        prepareKeyValues(rawResults, batchSize * 2, batchSize * 3)

      }

      val resultBatch4 = Future {

        prepareKeyValues(rawResults, batchSize * 3, batchSize * 4)

      }

      val resultBatch5 = Future {

        prepareKeyValues(rawResults, batchSize * 4, rawResultSize)

      }

      val futureResponse = for {

        resultBatch1Result <- resultBatch1
        resultBatch2Result <- resultBatch2
        resultBatch3Result <- resultBatch3
        resultBatch4Result <- resultBatch4
        resultBatch5Result <- resultBatch5

      } yield (resultBatch1Result, resultBatch2Result, resultBatch3Result, resultBatch4Result, resultBatch5Result)

      val futureResult = Await.ready(futureResponse, Duration.Inf).value.get

      val result = futureResult.get._1 ++ futureResult.get._2 ++ futureResult.get._3 ++ futureResult.get._4 ++ futureResult.get._5

      result

    }
    else {

      prepareKeyValues(rawResults, 0, rawResults.data.get.size)

    }

  }

  def prepareKeyValues(rawResults: QueryResponse, startIndex: Int, endIndex: Int): ListBuffer[Map[String, Any]] = {

    var results: ListBuffer[Map[String, Any]] = new ListBuffer[Map[String, Any]]

    val resultData = rawResults.data.get.slice(startIndex, endIndex)

    resultData.foreach { valData =>

      var indexRes = 0;

      var result: Map[String, Any] = collection.immutable.Map();

      try {

        valData.foreach {

          case Some(value) => {
            result += (rawResults.columns.get(indexRes).name -> value)

            indexRes = indexRes + 1

          }
          case None => {

            result += (rawResults.columns.get(indexRes).name -> "null")

            indexRes = indexRes + 1

          }

        }

      } catch {
        case e: Exception => {

          logger.info(s"Failed to fetch Logs :: ${e}")

        }
      }

      if (result.size > 0)
        results += result

    }
    results

  }

  def buildQueryID(nextUri: Option[String]): String = {

    var queryId = ""

    if (nextUri != None && nextUri.get != "") {

      val nextUriParams = nextUri.get.split("/")

      if (nextUriParams.length >= 5) {

        queryId = nextUriParams(5) + "-" + nextUriParams(6) //+ "-" + nextUriParams(7)

      }

    }

    queryId

  }

  def receive: Receive = {

    case getLogs(auth_details, params) => {

      logger.info(s"Recevied request to execute query :: ${params.query}")

      var results: ListBuffer[Map[String, Any]] = new ListBuffer[Map[String, Any]]

      val context = ASTUtility.initializeContext()

//      sender() ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

      try {

        var query = params.query

        var org_flag = false

        if (params.query != null && params.query != "") {

          val me = sender()

          /**
            * Submit query to presto using POST API
            */

          if ((auth_details.organizations != null && auth_details.organizations.size > 0)) {

            logger.info("Fetch Organization if user is a non soc user")

            val organizations = OrganizationService.getOrganizations(auth_details)

            val orgs_name = organizations.map(_.name)

            logger.info(s"User ${auth_details.user_id} organizations are :: ${orgs_name}")

            logger.info("Get organizations within query using AST")

            val js_organizations = ASTUtility.getOrganizations(query, context)

            logger.info(s"Organization from AST ${js_organizations}")

            val js_org_list = js_organizations.toString.split(',')

            if (js_organizations != null && js_organizations != "null" && js_organizations.toString != "" && js_org_list.size > 0) {

              js_org_list.foreach { org =>

                if (orgs_name.contains(org) == false) {

                  logger.info(s"Unauthorized to access ${org} organization")

                  org_flag = true

                }

              }

              if (org_flag == true) {

                logger.info("Sending unauthorized response")

                me ! LogsResponse(403, "Unauthorized", "", -1, false, List.empty)

              }
              else {

                logger.info("Organization found in query, adding user's organization to query")

                query = ASTUtility.addOrganizations(orgs_name.mkString(","), query, context)
              }

            } else {

              logger.info("No organization found in query")

              query = ASTUtility.addOrganizations(orgs_name.mkString(","), query, context)

            }

          }

          logger.info(s"Add log date to query :: ${query}")

          query = ASTUtility.addLogDate(query, context)

          logger.info(s"Check and Add order by to query :: ${query}")

          query = ASTUtility.checkAndAddOrderClause(query, context)

          logger.info(s"Check and Add limit by to query :: ${query}")

          query = ASTUtility.checkAndAddLimitClause(query, limit)

          logger.info(s"Final query :: ${query}")

          if (org_flag == false) {

            logService.postStatement(query)

              .onComplete {

                case Success(response) => Unmarshal(response.entity).to[String].map { statementResponse =>

                  if (response.status.isSuccess()) {

                    val statementResponseObj = statementResponse.parseJson.convertTo[StatementResponse]

                    if (statementResponseObj.nextUri != null && statementResponseObj.nextUri != "") {

                      /**
                        * Fetch Presto Query Stats using GET API
                        */

                      logService.getQueryStats(statementResponseObj)

                        .onComplete {

                          case Success(getResponse) => Unmarshal(getResponse.entity).to[String].map { getStatementResponse =>

                            if (getResponse.status.isSuccess()) {

                              val getResponseObj = getStatementResponse.parseJson.convertTo[QueryResponse]

                              if (getResponseObj.stats.get.state != "FAILED") {

                                logger.info(s"Query State :: ${getResponseObj.stats.get.state}")

                                if (getResponseObj.nextUri != None && getResponseObj.nextUri.get != null && getResponseObj.nextUri.get != "") {

                                  /**
                                    * Get results of first page using Presto GET API
                                    */

                                  val queryId = buildQueryID(getResponseObj.nextUri); //getResponseObj.id //

                                  logger.info(s"queryId ${queryId}")

                                  logService.getQueryResults(queryId, 2)

                                    .onComplete {

                                      case Success(queryResponse) => Unmarshal(queryResponse.entity).to[String].map { getQueryResponse =>

                                        if (queryResponse.status.isSuccess()) {

                                          val rawResults = getQueryResponse.parseJson.convertTo[QueryResponse]

                                          var hasNextPage = true

                                          if (rawResults.nextUri == None && rawResults.stats.get.state == "FINISHED") {

                                            hasNextPage = false

                                          }

                                          if (rawResults.data != None) {

                                            results = formatLogResults(rawResults);

                                          }

                                          if (results.size > 0) {

                                            me ! LogsResponse(200, "Success", queryId, 1, hasNextPage, results.toList)

                                          } else {

                                            if (hasNextPage) {

                                              me ! LogsResponse(200, "Not Found", queryId, 1, hasNextPage, results.toList)

                                            } else {

                                              me ! LogsResponse(200, "Not Found", queryId, -1, hasNextPage, results.toList)

                                            }

                                          }

                                        } else {

                                          logger.error(s"Error in Presto Get query result API :: ${queryResponse.status.reason()}")

                                          me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                                        }

                                      }

                                      case Failure(_) => {

                                        me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                                      }

                                    }

                                }

                              } else {

                                logger.error(s"Error in Presto Query ${query}")

                                me ! LogsResponse(400, "Invalid Query", "", -1, false, results.toList)

                              }

                            }

                            else {

                              logger.error(s"Error in Presto Get stats API :: ${getResponse.status.reason()}")

                              me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                            }

                          }

                          case Failure(_) => {

                            me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)
                          }

                        }

                    } else {

                      logger.error(s"Error in Presto POST Query API :: ${response.status.reason()}")

                      me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                    }

                  }

                  else {

                    logger.error(s"Error in Presto POST Query API :: ${response.status.reason()}")

                    me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                  }

                }

                case Failure(_) => {

                  me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

                }

              }

          } else {

            me ! LogsResponse(403, "Unauthorized", "", -1, false, List.empty)

          }

        }

        else {

          sender() ! LogsResponse(400, "Bad Request", "", -1, false, results.toList)

        }

      }

      catch {

        case e: Exception => {

          logger.error(s"Error in Submitting query to presto:: ${e.printStackTrace()}")

          sender() ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

        }

      }

    }

    case getLogsByQueryId(auth_details, id, page) => {

      var results: ListBuffer[Map[String, Any]] = new ListBuffer[Map[String, Any]]

      val me = sender()

      try {

        logService.getQueryResults(id, page + 1)

          .onComplete {

            case Success(queryResponse) => Unmarshal(queryResponse.entity).to[String].map { getQueryResponse =>

              if (queryResponse.status.isSuccess()) {

                val rawResults = getQueryResponse.parseJson.convertTo[QueryResponse]

                var hasNextPage = true

                if (rawResults.nextUri == None && rawResults.stats.get.state == "FINISHED") {

                  hasNextPage = false

                }

                if (rawResults.data != None) {

                  results = formatLogResults(rawResults);

                }

                if (results.size > 0) {

                  val queryId = buildQueryID(rawResults.nextUri);

                  //me ! LogsResponse(200, "Success", rawResults.id, page, hasNextPage, results.toList)

                  me ! LogsResponse(200, "Success", queryId, page, hasNextPage, results.toList)

                } else {

                  if (hasNextPage) {

                    val queryId = buildQueryID(rawResults.nextUri);

                    //me ! LogsResponse(200, "Not Found", rawResults.id, page, hasNextPage, results.toList)

                    me ! LogsResponse(200, "Not Found", queryId, page, hasNextPage, results.toList)

                  } else {

                    val queryId = buildQueryID(rawResults.nextUri);

                    //me ! LogsResponse(200, "Not Found", rawResults.id, -1, hasNextPage, results.toList)

                    me ! LogsResponse(200, "Not Found", queryId, -1, hasNextPage, results.toList)

                  }

                }

              } else {

                logger.error(s"Error in Presto Get query result API :: ${queryResponse.status.reason()}")

                me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

              }

            }

            case Failure(_) => {

              me ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

            }

          }

      } catch {

        case e: Exception => {

          logger.error(s"Error in Submitting query to presto:: ${e.printStackTrace()}")

          sender() ! LogsResponse(500, "Something went wrong, please try again", "", -1, false, results.toList)

        }

      }

    }

    case getLogsQueryStats(auth_details, params) => {

      logger.info(s"Dashboard Log Sumary API Started :: ${Calendar.getInstance().getTime()}")

      try {

        var countQuery = ""

        var histogramQuery = ""

        var categoryQuery = ""

        val context = ASTUtility.initializeContext();

        logger.info(s"Request received for logs query stats for query ${params.query}")

        logger.info(s"Add log date to query :: ${params.query}")

        var query = ASTUtility.addLogDate(params.query, context)

        logger.info(s"Check and Add order by to query :: ${query}")

        query = ASTUtility.checkAndAddOrderClause(query, context)

        logger.info("Initialize AST Context")

        val bindings = context.getBindings("js")

        var org_flag = false

        bindings.putMember("query", query)

        val js_query_returns_timestamp = context.eval("js", "module.queryReturnsTimestamp(module.getAST(query));").asBoolean

        val js_query_returns_category = context.eval("js", "module.queryReturnsCategory(module.getAST(query));").asBoolean

        if ((auth_details.organizations != null && auth_details.organizations.size > 0)) {

          logger.info("Fetch Organization if user is a non soc user")

          val organizations = OrganizationService.getOrganizations(auth_details)

          val orgs_name = organizations.map(_.name)

          bindings.putMember("organizations", orgs_name.mkString(","))

          logger.info(s"User ${auth_details.user_id} organizations are :: ${orgs_name}")

          logger.info("Get organizations within query using AST")

          val js_organizations = context.eval("js", "module.getOrganizations(module.getAST(query));")

          logger.info(s"Organization from AST ${js_organizations.toString}")

          val js_org_list = js_organizations.toString.split(',')

          if (js_organizations.toString != "null" && js_organizations.toString != "" && js_org_list.size > 0) {

            js_org_list.map { org =>

              if (orgs_name.contains(org) == false) {

                logger.info(s"Unauthorized to access ${org} organization")

                org_flag = true

              }

            }

            if (org_flag == true) {

              logger.info("Sending unauthorized response")

              sender() ! LogsStatsResponse(403, "Unauthorized", None, None, 0)

            }

          }

          if (org_flag == false) {

            logger.info("No organization found in query")

            bindings.putMember("organizations", orgs_name.mkString(","))

            bindings.putMember("numberOfBins", params.numberOfBins)

            bindings.putMember("binField", params.binField)

            bindings.putMember("weight", params.weight)

            countQuery = context.eval("js", "module.getQuery(module.getCountQuery(module.addOrganizations(module.getAST(query), organizations)));").asString

            if (js_query_returns_category == true) {

              categoryQuery = context.eval("js", "module.getQuery(module.getCategoryCountsQuery(module.addOrganizations(module.getAST(query), organizations)));").asString

            }

            if (js_query_returns_timestamp == true) {

              histogramQuery = context.eval("js", "module.getQuery(module.getHistogram(module.addOrganizations(module.getAST(query), organizations), numberOfBins, binField, weight));").asString

            }

          }

        }

        else {

          bindings.putMember("numberOfBins", params.numberOfBins.toString)

          bindings.putMember("binField", params.binField)

          bindings.putMember("weight", params.weight.toString)

          countQuery = context.eval("js", "module.getQuery(module.getCountQuery(module.getAST(query)));").asString

          if (js_query_returns_category) {

            categoryQuery = context.eval("js", "module.getQuery(module.getCategoryCountsQuery(module.getAST(query)));").asString

          }

          if (js_query_returns_timestamp) {

            histogramQuery = context.eval("js", "module.getQuery(module.getHistogram(module.getAST(query), numberOfBins, binField, weight));").asString

          }

        }

        logger.info(s"Dashboard Log Sumary API AST Finished :: ${Calendar.getInstance().getTime()}")

        if (org_flag == false) {

          logger.info(s"Histogram Query :: ${histogramQuery}")

          logger.info(s"count Query :: ${countQuery}")

          logger.info(s"category Query :: ${categoryQuery}")

          var histogramResultFuture: Future[List[(String, String)]] = Future {
            List.empty
          }

          if (js_query_returns_timestamp == true) {
            histogramResultFuture = Future {
              logService.getHistogramResult(histogramQuery)
            }
          }

          val countResultFuture = Future {
            logService.getLogCount(countQuery).get
          }

          var categoryResultFuture: Future[List[Map[String, Int]]] = Future {
            List.empty
          }

          if (js_query_returns_category == true) {
            categoryResultFuture = Future {
              logService.getCategoryResult(categoryQuery)
            }
          }

          logger.info(s"Dashboard Log Sumary API combine all results started :: ${Calendar.getInstance().getTime()}")

          val futureResponse = for {

            histogramResult <- histogramResultFuture

            categoryResult <- categoryResultFuture

            countResult <- countResultFuture

          } yield (histogramResult, categoryResult, countResult)

          val senderMethod = sender()

          futureResponse.onComplete {

            case Success(data) =>

              logger.info(s"Dashboard Log Sumary API combine all results end :: ${Calendar.getInstance().getTime()}")

              senderMethod ! LogsStatsResponse(200, "Success", Some(data._1), Some(data._2), data._3)

            case Failure(error) =>

              logger.error(s"Error in Submitting stats query:: ${error}")

              senderMethod ! LogsStatsResponse(500, "Something went wrong, please try again", None, None, 0)

          }

        }

      } catch {

        case e: Exception => {


          logger.error(s"Error in Submitting stats query:: ${e.printStackTrace()}")

          sender() ! LogsStatsResponse(500, "Something went wrong, please try again", None, None, 0)

        }

      }

    }

  }

  def sortHistogramResults(histogramResult: String): List[(String, String)] = {

    val rawHistogram = histogramResult.replace("{", "").replace("}", "")

    val timeBasedCount = rawHistogram.split(",")

    val keyValueTimeCount = timeBasedCount.map(_.split("="))

    val rawResultHistogram: List[Map[String, String]] = keyValueTimeCount.map { arr =>

      arr.grouped(2).map { a => a(0).trim -> a(1) }.toMap

    }.toList

    val sortedHistogramResult = rawResultHistogram.flatten.toMap.toSeq.sortBy(_._1).toList

    sortedHistogramResult

  }

}

