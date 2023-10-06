package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.LogsourcesMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.logsources.Manager._
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.duration._
import scala.concurrent.Future

object Logsources extends JsonSupport with ScalaPBJsonSupport with LazyLogging{

  implicit lazy val timeout = Timeout(600.seconds);

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, logsourceActor: ActorRef)(implicit  timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("logsources") {

          concat(

            get {

              parameterMap { params =>

                val logsources_details: Future[LogsourcesResponse] = (logsourceActor ? getLogsources(auth_details, params)).mapTo[LogsourcesResponse]

                onSuccess(logsources_details) { logsources =>

                  complete(statusMap(logsources.status_code), logsources)

                }

              }

            },

            post {

              entity(as[LogsourceInsert]) { logsource =>

                val logsources_create: Future[ActionPerformed] = (logsourceActor ? createLogsource(auth_details, logsource)).mapTo[ActionPerformed]

                onSuccess(logsources_create) { logsources =>

                  complete(statusMap(logsources.status_code), logsources)

                }

              }

            }

          )

        },

        path("logsources" / IntNumber) { logsource_id =>

          concat(

            put {

              entity(as[LogsourceUpdate]) { logsource =>

                val logsources_create: Future[ActionPerformed] = (logsourceActor ? updateLogsource(auth_details, logsource, logsource_id)).mapTo[ActionPerformed]

                onSuccess(logsources_create) { logsources =>

                  complete(statusMap(logsources.status_code), logsources)

                }

              }

            },

            delete {

              val logsource_deleted: Future[ActionPerformed] = (logsourceActor ? deleteLogsource(auth_details, logsource_id)).mapTo[ActionPerformed]

              onSuccess(logsource_deleted) { response =>

                complete(statusMap(response.status_code), response)

              }

            }
          )
        }

      )

    routes

  }

}
