package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.NormalizerMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.normalizer.Manager._
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.Future

object Normalizer extends JsonSupport with ScalaPBJsonSupport with LazyLogging{

  implicit lazy val timeout = Timeout(600.seconds);

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, normalizerActor: ActorRef)(implicit  timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("normalizer") {

          concat(

            get {

              parameterMap { params =>

                val normalizer_details: Future[NormalizersResponse] = (normalizerActor ? getNormalizers(auth_details, params)).mapTo[NormalizersResponse]

                onSuccess(normalizer_details) { normalizer =>

                  complete(statusMap(normalizer.status_code), normalizer)

                }

              }

            },

            post {

              entity(as[NormalizerInsert]) { normalizer =>

                val logsources_create: Future[ActionPerformed] = (normalizerActor ? createNormalizer(auth_details, normalizer)).mapTo[ActionPerformed]

                onSuccess(logsources_create) { normalizer =>

                  complete(statusMap(normalizer.status_code), normalizer)

                }

              }

            }

          )

        },

        path("normalizer" / IntNumber) { normalizer_id =>

          concat(

            put {

              entity(as[NormalizerUpdate]) { normalizer =>

                val normalizer_update: Future[ActionPerformed] = (normalizerActor ? updateNormalizer(auth_details, normalizer, normalizer_id)).mapTo[ActionPerformed]

                onSuccess(normalizer_update) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            },

            delete {

              val normalizer_deleted: Future[ActionPerformed] = (normalizerActor ? deleteNormalizer(auth_details, normalizer_id)).mapTo[ActionPerformed]

              onSuccess(normalizer_deleted) { response =>

                complete(statusMap(response.status_code), response)

              }

            }
          )
        }

      )

    routes

  }

}
