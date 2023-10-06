package com.iac.soc.backend.api.logsources

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.LogsourcesMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

object Manager {

  final case class getLogsources(auth_details: Claims, params: Map[String, String]);

  final case class createLogsource(auth_details: Claims, params: LogsourceInsert);

  final case class updateLogsource(auth_details: Claims, params: LogsourceUpdate, logsource_id: Int);

  final case class deleteLogsource(auth_details: Claims, logsource_id: Int);

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  implicit val timeout: Timeout = Timeout(600 seconds)

  def receive: Receive = {

    case getLogsources(auth_details, params) =>

      logger.info(s"Received request for get Logresources by user ${auth_details.user_id}")

      try {

        var page = 1

        var size = 5

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        logger.info(s"Request recevied with params :: ${params}")

        val totalCount: Int = Repository.getLogsourcesCount(auth_details, params)

        if (totalCount > 0) {
          val logsources: List[LogsourceResponse] = Repository.getLogsources(auth_details, params)

          if (logsources.length > 0) {

            sender() ! LogsourcesResponse(200, "", page, size, totalCount, logsources)

          }
          else {

            logger.info("Sending response with status code 200, no records found")

            sender() ! LogsourcesResponse(200, "No records found", page, size, totalCount, List.empty)

          }
        }
        else {

          logger.info("Sending response with status code 200, no records found")

          sender() ! LogsourcesResponse(200, "No records found", page, size, totalCount, List.empty)

        }

      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! LogsourcesResponse(500, "Something went wrong, please try again", 0, 0, 0, List.empty)

        }
      }

    case createLogsource(auth_details, params) =>

      logger.info(s"Received request for create Logresources by user ${auth_details.user_id}")
      logger.info(s"Params ${params}")

      try {
        Repository.createLogsource(auth_details, params)
        sender() ! ActionPerformed(201, "Logsource Created Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

    case updateLogsource(auth_details, params, logsource_id) =>

      logger.info(s"Received request for update Logresources by user ${auth_details.user_id}")
      logger.info(s"Params ${params}")

      try {
        Repository.updateLogsource(auth_details, params, logsource_id)
        sender() ! ActionPerformed(201, "Logsource Updated Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

    case deleteLogsource(auth_details, logsource_id) =>
      logger.info(s"Received request for delete Logresources by user ${auth_details.user_id}")
      logger.info(s"Params ${logsource_id}")

      try {
        Repository.deleteLogsource(auth_details, logsource_id)
        sender() ! ActionPerformed(201, "Logsource Deleted Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

  }
}

