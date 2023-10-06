package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.LogMapping.{LogsPostRequest, LogsPostStatsRequest, LogsResponse, LogsStatsResponse}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.log.Manager.{getLogs, getLogsByQueryId, getLogsQueryStats}
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.Future

object Logs extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, logActor: ActorRef)(implicit timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        pathPrefix("logs") {

          concat(

            pathEnd {

              post {

                entity(as[LogsPostRequest]) { log =>

                  val logs_details: Future[LogsResponse] = (logActor ? getLogs(auth_details, log)).mapTo[LogsResponse]

                  onSuccess(logs_details) { response =>

                    complete(statusMap(response.status_code), response)

                  }

                }

              }

            },

            path("summary") {

              post {

                entity(as[LogsPostStatsRequest]) { log =>

                  val logs_details: Future[LogsStatsResponse] = (logActor ? getLogsQueryStats(auth_details, log)).mapTo[LogsStatsResponse]

                  onSuccess(logs_details) { response =>

                    complete(statusMap(response.status_code), response)

                  }

                }

              }

            }

          )

        },

        pathPrefix("logs" / Segment) { query_id =>

          path(IntNumber) { page =>

            get {

              val logs_details: Future[LogsResponse] = (logActor ? getLogsByQueryId(auth_details, query_id, page)).mapTo[LogsResponse]

              onSuccess(logs_details) { response =>

                complete(statusMap(response.status_code), response)

              }

            }

          }

        }

      )

    routes

  }

}
