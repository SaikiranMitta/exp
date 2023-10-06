package com.iac.soc.backend.api.user

import akka.actor.{Actor, ActorLogging, Props}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.messages.Messages.{ActionPerformed, Organization}
import com.iac.soc.backend.api.common.{KeycloakUtils, OrganizationService}
import com.iac.soc.backend.api.user.messages.Messages._
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.DB

import scala.collection.mutable.ListBuffer

object Manager {

  final case class GetUsers(auth_details: Claims, params: Map[String, String]);

  final case class CreateUser(auth_details: Claims, user: CreateRequest)

  final case class UpdateUser(auth_details: Claims, user_id: Long, user: UpdateRequest)

  final case class GetUser(auth_details: Claims, user_id: Long)

  final case class DeleteUser(auth_details: Claims, id: Long)

  final case class ResetPassword(auth_details: Claims, user_id: Long, passwordResetRequest: PasswordResetRequest)

  final case class SendPasswordEmail(auth_details: Claims, user_id: Long, passwordResetRequest: PasswordResetRequest)

  def props: Props = Props[Manager]

}

class Manager extends Actor with ActorLogging with LazyLogging {

  import Manager._

  //getConnection();

  def receive: Receive = {

    case GetUsers(auth_details, params) =>

      try {

        logger.info("Received request to fetch users")

        var page = 1

        var size = 5

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        var sort_by = ""

        var sort_order = ""

        var sort = ""

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = "r." + params("sort_by")

          sort_order = params("sort_order")

          sort = "order by " + sort_by + " " + sort_order + " "

        }

        /**
          * Get User Search Count
          */

        logger.info(s"Fetch users count with params :: ${params}")

        val totalCount: Int = Repository.getUserSearchCount(params)

        val user_list = Repository.getUsers(params);

        val organizations = OrganizationService.getOrganizations(auth_details)

        val user_response = user_list.map { user =>

          val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

          if (user.orgIds != null && user.orgIds != None) {

            val user_org = user.orgIds.split(",").toList

            if (user_org.size > 0 && organizations.size > 0) {

              organizations.foreach { org =>

                if (user_org.map(_.toInt).contains(org.id)) {

                  orgList += Organization(org.name, org.id.toInt)

                }

              }

            }

          }
          User(
            user.id,
            user.firstName,
            user.lastName,
            user.username,
            user.email,
            user.role,
            user.status,
            user.idpUserId,
            user.createdBy,
            user.createdOn,
            user.updatedBy,
            user.updatedOn,
            orgList.toList
          )

        }

        sender() ! GetUsersResponse(200, "Success", size, totalCount, user_response)

      } catch {

        case exception: Exception => {

          logger.error("Exception in fetching users :: ", exception.printStackTrace())

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case CreateUser(auth_details, user) =>

      try {

        var keycloak_user_id = ""

        logger.info(s"Received Request to create user ::  ${user}")

        val count = DB localTx { implicit session =>

          logger.info("Insert details into database")

          val user_id = Repository.createUser(auth_details, user)

          logger.info("Insert details into keycloak")

          keycloak_user_id = KeycloakUtils.createUser(user, user_id);

          Repository.updateUserIDP(auth_details, keycloak_user_id, user_id)

        }

        logger.info("Sending response with status code 201, User Created")

        sender() ! ActionPerformed(201, "User Created Successfully")

        if (keycloak_user_id != "") {

          KeycloakUtils.sentUpdatePasswordEmail(keycloak_user_id)

        }

      } catch {

        case exception: Exception => {

          logger.error("Exception in creating user :: ", exception.printStackTrace())

          sender() ! ActionPerformed(400, "Bad Request")

        }

      }

    case UpdateUser(auth_details, user_id, user) =>

      try {

        logger.info(s"Received Request to update user :: ${user_id} ${user}")

        val count = DB localTx { implicit session =>

          logger.info("Update details into database")

          Repository.updateUser(auth_details, user_id, user)

          logger.info("Update details into keycloak")

          val user_keycloak = KeycloakUtils.updateUser(user, user_id);

        }

        logger.info("Sending response with status code 200, User Updated")

        sender() ! ActionPerformed(200, "User Updated Successfully")

      } catch {

        case exception: Exception => {

          logger.error("Exception in updating user :: ", exception.printStackTrace())

          sender() ! ActionPerformed(400, "Bad Request")

        }

      }

    case GetUser(auth_details, id) =>

      try {

        logger.info(s"Received request to fetch user ${id}")

        var page = 1

        var size = 1

        val params = Map("id" -> id.toString)

        val user_list = Repository.getUsers(params);

        val organizations = OrganizationService.getOrganizations(auth_details)

        val user_response: List[User] = user_list.map { user =>

          val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

          if (user.orgIds != null && user.orgIds != None) {

            val user_org = user.orgIds.split(",").toList

            if (user_org.size > 0 && organizations.size > 0) {

              organizations.foreach { org =>

                if (user_org.map(_.toInt).contains(org.id)) {

                  orgList += Organization(org.name, org.id.toInt)

                }

              }

            }

          }

          User(
            user.id,
            user.firstName,
            user.lastName,
            user.username,
            user.email,
            user.role,
            user.status,
            user.idpUserId,
            user.createdBy,
            user.createdOn,
            user.updatedBy,
            user.updatedOn,
            orgList.toList
          )

        }
        if (user_response.size > 0) {

          sender() ! GetUsersResponse(200, "Success", size, user_response.size, user_response)

        } else {

          sender() ! GetUsersResponse(404, "Not Found", 0, 0, List.empty)

        }

      } catch {

        case exception: Exception => {

          logger.error("Exception in fetching user :: ", exception.printStackTrace())

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case DeleteUser(auth_details, user_id) =>

      try {

        logger.info(s"Received Request to delete user :: ${user_id} ")

        val count = DB localTx { implicit session =>

          logger.info("Delete user from database")

          val user_details = Repository.getUser(user_id)

          if (user_details != None) {

            Repository.deleteUser(auth_details, user_id)

            logger.info("Delete user from keycloak")

            val user_keycloak = KeycloakUtils.deleteUser(user_details.get.idpUserId);

          } else {

            sender() ! ActionPerformed(404, "Not Found")

          }

        }

        logger.info("Sending response with status code 200, User Deleted")

        sender() ! ActionPerformed(200, "User Deleted Successfully")

      } catch {

        case exception: Exception => {

          logger.error("Exception in deleting user :: ", exception.printStackTrace())

          sender() ! ActionPerformed(400, "Bad Request")

        }

      }

    case ResetPassword(auth_details, user_id, passwordResetRequest) =>

      try {

        val user_details = Repository.getUser(user_id)

        if (user_details != None) {

          KeycloakUtils.resetUserPassword(user_details.get.idpUserId, passwordResetRequest.password)

          logger.info("Sending response with status code 200, User password reset successful")

          sender() ! ActionPerformed(200, "Password changed")

        } else {

          sender() ! ActionPerformed(404, "Not Found")

        }

      } catch {

        case exception: Exception => {

          logger.error("Exception in reset user password :: ", exception.printStackTrace())

          sender() ! ActionPerformed(400, "Bad Request")

        }

      }
    case SendPasswordEmail(auth_details, user_id, passwordResetRequest) =>

      try {

        val user_details = Repository.getUser(user_id)

        if (user_details != None) {

          KeycloakUtils.sentUpdatePasswordEmail(user_details.get.idpUserId)

          logger.info("Sending response with status code 200, User password reset email send successful")

          sender() ! ActionPerformed(200, "Email Sent")

        } else {

          sender() ! ActionPerformed(404, "Not Found")

        }

      } catch {

        case exception: Exception => {

          logger.error("Exception in send mail password :: ", exception.printStackTrace())

          sender() ! ActionPerformed(400, "Bad Request")

        }

      }

  }

}
