package com.iac.soc.backend.api.dashboard

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.iac.soc.backend.api.common.OrganizationService
import com.iac.soc.backend.api.common.mapping.DashboardMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.utility.ASTUtility
import com.ibm.icu.text.SimpleDateFormat
import com.ibm.icu.util.Calendar
import com.typesafe.scalalogging.LazyLogging
import org.graalvm.polyglot.Context

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object Manager {

  final case class getQueryResult(auth_details: Claims, params: DashboardInput);

  final case class getAlertsResult(auth_details: Claims, params: Map[String, String]);

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  implicit val timeout: Timeout = Timeout(600 seconds)
  implicit val system: ActorSystem = context.system
  implicit val materializer = ActorMaterializer()
//  implicit val executionContext = system.dispatcher
  implicit val executionContext = system.dispatchers.lookup("api-dispatcher")

  val dashboardService: Repository = new Repository()

  def receive: Receive = {

    case getQueryResult(auth_details, params) =>

      logger.info(s"Recevied request to execute dashboard query :: ${params.query}")

      logger.info(s"Dashboard API Started :: ${Calendar.getInstance().getTime()}")

      var results: ListBuffer[Map[String, Any]] = new ListBuffer[Map[String, Any]]

      //val ASTContext: Context = ASTUtility.initializeContext()

      try {

        var query = params.query

        var org_flag = false

        if (params.query != null && params.query != "") {

          val me = sender()

          /**
            * Submit query to presto using JDBC
            */

          if ((auth_details.organizations != null && auth_details.organizations.size > 0)) {

            logger.info("Fetch Organization if user is a non soc user")

            val organizations = OrganizationService.getOrganizations(auth_details)

            val orgs_name = organizations.map(_.name)

            logger.info(s"User ${auth_details.user_id} organizations are :: ${orgs_name}")

            logger.info("Get organizations within query using AST")

            val js_organizations = ASTUtility.getOrganizations(query)

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

                me ! DashboardResponse(403, "Unauthorized", List.empty)

              }
              else {

                logger.info("Organization found in query, adding user's organization to query")

                query = ASTUtility.addOrganizations(orgs_name.mkString(","), query)
              }

            } else {

              logger.info("No organization found in query")

              query = ASTUtility.addOrganizations(orgs_name.mkString(","), query)

            }

          }

          logger.info(s"Add logdate to query :: ${query}")

          query = ASTUtility.addLogDate(query)

          logger.info(s"Final query :: ${query}")
          logger.info(s"Dashboard API AST changes finished :: ${Calendar.getInstance().getTime()}")

          if (org_flag == false) {

            val response = dashboardService.postStatement(query)
            logger.info(s"Dashboard API sending results :: ${Calendar.getInstance().getTime()}")

            sender() ! DashboardResponse(200, "", response)

          } else {

            me ! DashboardResponse(403, "Unauthorized", List.empty)
          }

        }

        else {

          sender() ! DashboardResponse(400, "Bad Request", results.toList)
        }

      }

      catch {

        case e: Exception => {

          logger.error(s"Error in Submitting query to presto:: ${e.toString}")

          sender() ! DashboardResponse(500, "Something went wrong, please try again", results.toList)

        }

      }

    case getAlertsResult(auth_details, params) =>
      logger.info(s"Recevied request to execute dashboard alerts by :: ${params} ")

      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
      val to = dateFormat.parse(params.get("to").get).getTime / 1000
      val from = dateFormat.parse(params.get("from").get).getTime / 1000
      val buckets = params.get("buckets").get.toInt
      val range = Math.round((to - from) / buckets)

      var results: ListBuffer[Map[String, Any]] = new ListBuffer[Map[String, Any]]

      try {

        val response = dashboardService.getAlertsCount(auth_details, params, range)

        sender() ! DashboardAlertResponse(200, "", response)
      }

      catch {

        case e: Exception => {

          logger.error(s"Error in Submitting query to presto:: ${e.printStackTrace()}")

          sender() ! DashboardAlertResponse(500, "Something went wrong, please try again", null)

        }

      }

  }
}
