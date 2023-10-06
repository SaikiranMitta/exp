package com.iac.soc.backend.api.threat

import akka.actor.{Actor, ActorSystem}
import akka.util.Timeout
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import com.iac.soc.backend.api.common.mapping.ThreatMapping._

import scala.concurrent.duration._

object Manager {
  final case class getThreatCount(auth_details: Claims, params: Map[String, String]);
  final case class getThreats(auth_details: Claims, params: Map[String, String]);
  final case class getMatchedThreats(auth_details: Claims, params: Map[String, String]);
}

class Manager extends Actor with LazyLogging {

  import Manager._

  implicit val timeout: Timeout = Timeout(600 seconds)
  implicit val system: ActorSystem = context.system

  val threatService: Repository = new Repository()

  def receive: Receive = {
    case getThreatCount(auth_details, params) =>

      logger.info(s"Received request for get Threat Indicators Count by user ${auth_details.user_id}")

      try {

        val threats: BigInt = threatService.getThreatsCount(params)

        sender() ! ThreatCountResponse(200, "", threats)

      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Threat Indicators Count:: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ThreatCountResponse(500, "Something went wrong, please try again", 0)

        }
      }
    case getThreats(auth_details, params) =>

      logger.info(s"Received request for get Threat Indicators by user ${auth_details.user_id}")

      try {

        val result: List[ThreatsResponse] = threatService.getThreats(params)

        sender() ! ThreatsResultResponse(200, "", result)

      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Threat Indicators :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ThreatsResultResponse(500, "Something went wrong, please try again", List.empty)

        }
      }
    case getMatchedThreats(auth_details, params) =>
      logger.info(s"Received request for get Matched Threat Indicators by user ${auth_details.user_id}")

      try {

        val result: List[Map[String, Any]] = threatService.getMatchedThreats(params)

        sender() ! MatchedThreatsResultResponse(200, "", result)

      } catch {
        case e: Exception => {

          logger.error(s"Failed to fetch Matched Threat Indicators :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ThreatsResultResponse(500, "Something went wrong, please try again", List.empty)

        }
      }
  }
}