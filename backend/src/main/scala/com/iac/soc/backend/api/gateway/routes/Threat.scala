package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route

import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.ThreatMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport
import com.iac.soc.backend.api.threat.Manager._
import akka.pattern.ask

import scala.concurrent.Future
import scala.concurrent.duration._

object Threat extends JsonSupport with ScalaPBJsonSupport with LazyLogging{
  implicit lazy val timeout = Timeout(600.seconds);

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, threatActor: ActorRef)(implicit  timeout: Timeout): Route = {
    lazy val routes: Route =

      concat(

        path("threat" / "count") {

          concat(

            get {

              parameterMap { params =>

                val threat_count: Future[ThreatCountResponse] = (threatActor ? getThreatCount(auth_details, params)).mapTo[ThreatCountResponse]

                onSuccess(threat_count) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }

          )

        },
        path("threats") {

          concat(

            get {

              parameterMap { params =>

                val threat_count: Future[ThreatsResultResponse] = (threatActor ? getThreats(auth_details, params)).mapTo[ThreatsResultResponse]

                onSuccess(threat_count) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }

          )

        },
        path("matchedthreats") {

          concat(

            get {

              parameterMap { params =>

                val threat_count: Future[MatchedThreatsResultResponse] = (threatActor ? getMatchedThreats(auth_details, params)).mapTo[MatchedThreatsResultResponse]

                onSuccess(threat_count) { response =>

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
