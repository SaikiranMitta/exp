package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.Timeout
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.{ActionPerformed, ModelValidationRejection}
import com.iac.soc.backend.api.gateway.AuthorizationHandler
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport
import spray.json._

import scala.concurrent.duration._

trait Routes extends JsonSupport with AuthorizationHandler with ScalaPBJsonSupport {

  val ruleActor: ActorRef

  val reportActor: ActorRef

  val organizationActor: ActorRef

  val categoryActor: ActorRef

  val incidentActor: ActorRef

  val logActor: ActorRef

  var ruleSubSystem: ActorRef

  val userActor: ActorRef

  val logsourcesActor: ActorRef

  val normalizerActor: ActorRef

  val dashboardActor: ActorRef

  val threatActor: ActorRef

  val materializerSettings = ActorMaterializerSettings.create(system)
  materializerSettings.withDispatcher("api-dispatcher")

  implicit val materializer_1: ActorMaterializer = ActorMaterializer(materializerSettings)

  val statusMap = Map(200 -> StatusCodes.OK, 201 -> StatusCodes.Created, 204 -> StatusCodes.NoContent, 404 -> StatusCodes.NotFound, 400 -> StatusCodes.BadRequest, 401 -> StatusCodes.Unauthorized, 403 -> StatusCodes.Forbidden, 500 -> StatusCodes.InternalServerError);

  val validationRejectionHandlers =

    RejectionHandler.newBuilder()

      .handleAll[ModelValidationRejection] { validationError =>

      val errorMessage = ActionPerformed(400, validationError(0).invalidFields(0).error).toJson

      complete(StatusCodes.BadRequest, errorMessage)

    }.result()

  implicit val api_ec = system.dispatchers.lookup("api-dispatcher")


  implicit val timeout = Timeout(600.seconds);

  def getRoutes(): Route = {

    initializeKeycloak()

    lazy val userRoutes: Route =

      pathPrefix("api" / "v1") {

        withExecutionContext(api_ec) {

          encodeResponse {

            extractRequestContext { ctx =>

              authenticate { user_details =>

                authorization(ctx, user_details) { authorization_details =>

                  handleRejections(validationRejectionHandlers) {

                    concat(

                      Users.getRoutes(statusMap, user_details, userActor),

                      Rules.getRoutes(statusMap, user_details, ruleActor, incidentActor, ruleSubSystem),

                      Reports.getRoutes(statusMap, user_details, reportActor, ruleSubSystem),

                      Organizations.getRoutes(statusMap, user_details, organizationActor),

                      Categories.getRoutes(statusMap, user_details, categoryActor),

                      Logs.getRoutes(statusMap, user_details, logActor),

                      Logsources.getRoutes(statusMap, user_details, logsourcesActor),

                      Normalizer.getRoutes(statusMap, user_details, normalizerActor),

                      Dashboard.getRoutes(statusMap, user_details, dashboardActor),

                      Threat.getRoutes(statusMap, user_details, threatActor)

                    )

                  }

                }

              }

            }

          }

        }
      }

    userRoutes

  }

}
