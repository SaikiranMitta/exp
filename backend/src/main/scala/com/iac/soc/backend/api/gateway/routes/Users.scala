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
import com.iac.soc.backend.api.user.Manager._
import com.iac.soc.backend.api.user.messages.Messages.{CreateRequest, GetUsersResponse, PasswordResetRequest, UpdateRequest}
import com.iac.soc.backend.api.user.validators.Insert
import com.iac.soc.backend.api.user.validators.Update
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.Future

object Users extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap : Map[Int, StatusCode], auth_details:Claims, userActor: ActorRef)(implicit  timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("users") {

          concat(

            post {

              implicit val userValidator = Insert

              entity(as[CreateRequest]) { user =>

                validateModel(user).apply { validateUser =>

                  val userCreated: Future[MutationResponse] = (userActor ? CreateUser(auth_details, user)).mapTo[MutationResponse]

                  onSuccess(userCreated) { response =>

                    complete(statusMap(response.statusCode), response)

                  }

                }
              }

            },

            get {

              parameterMap { params =>

                val users: Future[GetUsersResponse] = (userActor ? GetUsers(auth_details, params)).mapTo[GetUsersResponse]

                onSuccess(users) { response =>

                  complete(statusMap(response.statusCode), response)

                }

              }

            }

          )

        },

        pathPrefix("users" / LongNumber) { user_id =>

          concat(

            put {

              implicit val userValidator = Update

              entity(as[UpdateRequest]) { user =>

                validateModel(user).apply { validateUser =>

                  val userCreated: Future[MutationResponse] = (userActor ? UpdateUser(auth_details, user_id, user)).mapTo[MutationResponse]

                  onSuccess(userCreated) { response =>

                    complete(statusMap(response.statusCode), response)

                  }
                }

              }

            },

            get {

              val users: Future[GetUsersResponse] = (userActor ? GetUser(auth_details, user_id)).mapTo[GetUsersResponse]

              onSuccess(users) { response =>

                complete(statusMap(response.statusCode), response)

              }

            },

            delete {

              val users: Future[MutationResponse] = (userActor ? DeleteUser(auth_details, user_id)).mapTo[MutationResponse]

              onSuccess(users) { response =>

                complete(statusMap(response.statusCode), response)

              }

            },

            path("reset_password") {

              put {

                entity(as[PasswordResetRequest]) { passwordResetRequest =>

                  val userCreated: Future[MutationResponse] = (userActor ? ResetPassword(auth_details, user_id, passwordResetRequest)).mapTo[MutationResponse]

                  onSuccess(userCreated) { response =>

                    complete(statusMap(response.statusCode), response)

                  }

                }

              }

            },

            path("send_password_email") {

              put {

                entity(as[PasswordResetRequest]) { passwordResetRequest =>

                  val userCreated: Future[MutationResponse] = (userActor ? SendPasswordEmail(auth_details, user_id, passwordResetRequest)).mapTo[MutationResponse]

                  onSuccess(userCreated) { response =>

                    complete(statusMap(response.statusCode), response)

                  }

                }

              }

            }

          )

        }

      )

      routes

  }

}
