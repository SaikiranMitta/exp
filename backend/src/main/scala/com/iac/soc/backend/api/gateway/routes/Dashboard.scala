package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import scala.concurrent.duration._
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.DashboardMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.dashboard.Manager._
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport
import akka.pattern.ask

import scala.concurrent.Future

object Dashboard extends JsonSupport with ScalaPBJsonSupport with LazyLogging{
  implicit lazy val timeout = Timeout(600.seconds);

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, dashboardActor: ActorRef)(implicit  timeout: Timeout): Route = {
    lazy val routes: Route =

      concat(

        path("dashboard") {

          concat(

            post {

              entity(as[DashboardInput]) { log =>

                val logs_details: Future[DashboardResponse] = (dashboardActor ? getQueryResult(auth_details, log)).mapTo[DashboardResponse]

                onSuccess(logs_details) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }
          )
        },
        path("dashboard" / "alert") {

          concat(

//            post {
//
//              entity(as[Map[String, String]]) { alerts =>
//
//                val logs_details: Future[DashboardAlertResponse] = (dashboardActor ? getAlertsResult(auth_details, alerts)).mapTo[DashboardAlertResponse]
//
//                onSuccess(logs_details) { response =>
//
//                  complete(statusMap(response.status_code), response)
//
//                }
//
//              }
//
//            }
            get {

              parameterMap { params =>

                val incident_details: Future[DashboardAlertResponse] = (dashboardActor ? getAlertsResult(auth_details, params)).mapTo[DashboardAlertResponse]

                onSuccess(incident_details) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }
          )
        }
      )
    routes
  }

}
