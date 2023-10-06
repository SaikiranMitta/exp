package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.IncidentMapping.{IncidentBucketResponse, IncidentByIdResponse, IncidentResponse, IncidentSummaryResponse}
import com.iac.soc.backend.api.common.mapping.RuleMapping.{GetRuleResponse, RuleInsMapping, RuleUpdateMapping, RulesResponse}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.gateway.ValidationDirective.validateModel
import com.iac.soc.backend.api.incident.Manager.{getIncidentSummary, getIncidentsById, getIncidentsByRule, getIncidentsSummaryByRule}
import com.iac.soc.backend.api.rule.Manager._
import com.iac.soc.backend.api.rule.validators.Insert
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.Future

object Rules extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, ruleActor: ActorRef, incidentActor: ActorRef, ruleSubSystem: ActorRef)(implicit timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("rules") {

          concat(

            get {

              parameterMap { params =>

                val rule_details: Future[RulesResponse] = (ruleActor ? getRules(auth_details, params)).mapTo[RulesResponse]

                onSuccess(rule_details) { rule =>

                  complete(statusMap(rule.status_code), rule)

                }

              }

            },

            post {

              implicit val ruleValidator = Insert

              entity(as[RuleInsMapping]) { rule =>

                validateModel(rule).apply { validateRule =>

                  val userCreated: Future[ActionPerformed] = (ruleActor ? createRule(auth_details, rule, ruleSubSystem)).mapTo[ActionPerformed]

                  onSuccess(userCreated) { response =>

                    complete(statusMap(response.status_code), response)

                  }

                }

              }

            }

          )

        },

        path("rules" / IntNumber) { rule_id =>

          concat(

            put {

              entity(as[RuleUpdateMapping]) { rule =>

                val userCreated: Future[ActionPerformed] = (ruleActor ? updateRule(auth_details, rule, rule_id, ruleSubSystem)).mapTo[ActionPerformed]

                onSuccess(userCreated) { response =>

                  complete(statusMap(response.status_code), response)

                }


              }

            },

            get {

              val rule_details: Future[GetRuleResponse] = (ruleActor ? getRule(auth_details, rule_id)).mapTo[GetRuleResponse]

              onSuccess(rule_details) { response =>

                complete(statusMap(response.status_code), response)

              }


            },

            delete {

              val rule_deleted: Future[ActionPerformed] = (ruleActor ? deleteRule(auth_details, rule_id, ruleSubSystem)).mapTo[ActionPerformed]

              onSuccess(rule_deleted) { response =>

                complete(statusMap(response.status_code), response)

              }

            }

          )

        },

        path("rules" / "incidents" / "summary") {

          concat(

            get {

              parameterMap { params =>

                val incident_details: Future[IncidentSummaryResponse] = (incidentActor ? getIncidentSummary(auth_details, params)).mapTo[IncidentSummaryResponse]

                onSuccess(incident_details) { response =>

                  complete(statusMap(response.status_code), response)

                }

              }

            }

          )

        },

        pathPrefix("rules" / LongNumber / "incidents") { rule_id =>

          concat(

            path("summary") {

              get {

                parameterMap { params =>

                  val incident_details: Future[IncidentBucketResponse] = (incidentActor ? getIncidentsSummaryByRule(auth_details, rule_id, params)).mapTo[IncidentBucketResponse]

                  onSuccess(incident_details) { response =>

                    complete(statusMap(response.status_code), response)

                  }

                }

              }

            },

            path(LongNumber) { incident_id =>

              get {

                parameterMap { params =>

                  val incident_details: Future[IncidentByIdResponse] = (incidentActor ? getIncidentsById(auth_details, incident_id)).mapTo[IncidentByIdResponse]

                  onSuccess(incident_details) { response =>

                    complete(statusMap(response.status_code), response)

                  }

                }

              }

            },

            get {

              parameterMap { params =>

                val incident_details: Future[IncidentResponse] = (incidentActor ? getIncidentsByRule(auth_details, rule_id, params)).mapTo[IncidentResponse]

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
