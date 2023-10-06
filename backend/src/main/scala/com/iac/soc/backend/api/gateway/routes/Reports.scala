package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.ReportMapping.{GetReportResponse, ReportInsMapping, ReportUpdateMapping, ReportsResponse}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.report.Manager._
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.Future
import scala.concurrent.duration._

object Reports extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, reportActor: ActorRef, ruleSubSystem: ActorRef)(implicit timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("reports") {

          concat(

            get {

              parameterMap { params =>

                val report_details: Future[ReportsResponse] = (reportActor ? getReports(auth_details, params)).mapTo[ReportsResponse]

                onSuccess(report_details) { report =>

                  complete(statusMap(report.status_code), report)

                }

              }

            },

            post {

              entity(as[ReportInsMapping]) { rule =>

                val report_created: Future[ActionPerformed] = (reportActor ? createReport(auth_details, rule)).mapTo[ActionPerformed]

                onSuccess(report_created) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }

          )

        },

        path("reports" / IntNumber) { rule_id =>

          concat(

            put {

              entity(as[ReportUpdateMapping]) { rule =>

                val update_report: Future[ActionPerformed] = (reportActor ? updateReport(auth_details, rule, rule_id)).mapTo[ActionPerformed]

                onSuccess(update_report) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            },

            get {

              val report_details: Future[GetReportResponse] = (reportActor ? getReport(auth_details, rule_id)).mapTo[GetReportResponse]

              onSuccess(report_details) { response =>

                complete(statusMap(response.status_code), response)

              }

            },

            delete {

              val report_deleted: Future[ActionPerformed] = (reportActor ? deleteReport(auth_details, rule_id)).mapTo[ActionPerformed]

              onSuccess(report_deleted) { response =>

                complete(statusMap(response.status_code), response)

              }

            }

          )

        }

      )

    routes

  }

}
