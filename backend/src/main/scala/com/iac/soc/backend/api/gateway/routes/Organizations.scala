package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.messages.Messages.{ActionPerformed => MutationResponse}
import com.iac.soc.backend.api.gateway.ValidationDirective.validateModel
import com.iac.soc.backend.api.organization.Manager._
import com.iac.soc.backend.api.organization.messages.Messages.{GetOrganizationsResponse, CreateRequest => OrganizationCreateRequest, UpdateRequest => OrganizationUpdateRequest, UpdateRequestValidator => OrganizationUpdateRequestValidator}
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport
import com.iac.soc.backend.api.organization.validators.Insert
import com.iac.soc.backend.api.organization.validators.Update

import scala.concurrent.Future

object Organizations extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims, organizationActor: ActorRef)(implicit  timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("organizations") {

          concat(

            get {

              parameterMap { params =>

                val organization_details: Future[GetOrganizationsResponse] = (organizationActor ? getOrganizations(auth_details, params)).mapTo[GetOrganizationsResponse]

                onSuccess(organization_details) { response =>

                  complete(statusMap(response.statusCode), response)

                }

              }

            },

            post {

              implicit val orgValidator = Insert

              entity(as[OrganizationCreateRequest]) { organization =>

                validateModel(organization).apply { validateOrganization =>

                  val organization_details: Future[MutationResponse] = (organizationActor ? createOrganization(auth_details, organization)).mapTo[MutationResponse]

                  onSuccess(organization_details) { response =>

                    complete(statusMap(response.statusCode), response)

                  }

                }

              }

            }

          )

        },

        path("organizations" / LongNumber) { org_id =>

          concat(

            put {

              implicit val orgValidator = Update

              entity(as[OrganizationUpdateRequest]) { organization =>

                val updateRequestValidator = OrganizationUpdateRequestValidator(org_id.toInt, organization.name, organization.status)

                validateModel(updateRequestValidator).apply { validateOrganization =>

                  val organization_details: Future[MutationResponse] = (organizationActor ? updateOrganization(auth_details, organization, org_id)).mapTo[MutationResponse]

                  onSuccess(organization_details) { response =>

                    complete(statusMap(response.statusCode), response)

                  }
                }

              }

            },

            delete {

              val organization_details: Future[MutationResponse] = (organizationActor ? deleteOrganization(auth_details, org_id)).mapTo[MutationResponse]

              onSuccess(organization_details) { response =>

                complete(statusMap(response.statusCode), response)

              }

            },

            get {

              val organization_details: Future[GetOrganizationsResponse] = (organizationActor ? getOrganization(auth_details, org_id)).mapTo[GetOrganizationsResponse]

              onSuccess(organization_details) { response =>

                complete(statusMap(response.statusCode), response)

              }

            }

          )

        }

      )

    routes

  }

}
