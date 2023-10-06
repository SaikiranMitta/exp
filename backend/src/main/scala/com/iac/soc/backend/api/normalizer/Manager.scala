package com.iac.soc.backend.api.normalizer

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.NormalizerMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

object Manager {

  final case class getNormalizers(auth_details: Claims, params: Map[String, String]);

  final case class createNormalizer(auth_details: Claims, params: NormalizerInsert);

  final case class updateNormalizer(auth_details: Claims, params: NormalizerUpdate, normalizer_id: Int);

  final case class deleteNormalizer(auth_details: Claims, normalizer_id: Int);

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  implicit val timeout: Timeout = Timeout(600 seconds)

  def receive: Receive = {

    case getNormalizers(auth_details, params) =>

      logger.info(s"Received request for get Normalizer by user ${auth_details.user_id}")

      try {

        var page = 1

        var size = 5

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        logger.info(s"Request recevied with params :: ${params}")

        val totalCount: Int = Repository.getNormalizerCount(auth_details, params)
        logger.info(s"${totalCount}")

        if (totalCount > 0) {
          val normalizers: List[NormalizerResponse] = Repository.getNormalizers(auth_details, params)

          if (normalizers.length > 0) {

            sender() ! NormalizersResponse(200, "", page, size, totalCount, normalizers)

          }
          else {

            logger.info("Sending response with status code 200, no records found")

            sender() ! NormalizersResponse(200, "No records found", page, size, totalCount, List.empty)

          }
        }
        else {

          logger.info("Sending response with status code 200, no records found")

          sender() ! NormalizersResponse(200, "No records found", page, size, totalCount, List.empty)

        }

      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! NormalizersResponse(500, "Something went wrong, please try again", 0, 0, 0, List.empty)

        }
      }

    case createNormalizer(auth_details, params) =>

      logger.info(s"Received request for create Normalizer by user ${auth_details.user_id}")
      logger.info(s"Params ${params}")

      try {
        Repository.createNormalizer(auth_details, params)
        sender() ! ActionPerformed(201, "Normalizer Created Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

    case updateNormalizer(auth_details, params, normalizer_id) =>

      logger.info(s"Received request for update Normalizer by user ${auth_details.user_id}")
      logger.info(s"Params ${params}")

      try {
        Repository.updateNormalizer(auth_details, params, normalizer_id)
        sender() ! ActionPerformed(201, "Normalizer Updated Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

    case deleteNormalizer(auth_details, normalizer_id) =>
      logger.info(s"Received request for delete Normalizer by user ${auth_details.user_id}")
      logger.info(s"Params ${normalizer_id}")

      try {
        Repository.deleteNormalizer(auth_details, normalizer_id)
        sender() ! ActionPerformed(201, "Normalizer Deleted Successfully")
      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Log sources :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")
        }
      }

  }
}

