package com.iac.soc.backend.api.organization

import akka.actor.{Actor, Props}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.messages.Messages.ActionPerformed
import com.iac.soc.backend.api.organization.messages.Messages.{CreateRequest, GetOrganizationsResponse, Organization, UpdateRequest}
import com.typesafe.scalalogging.LazyLogging

object Manager {

  final case class getOrganizations(auth_details: Claims, params: Map[String, String]);

  final case class createOrganization(auth_details: Claims, organization: CreateRequest);

  final case class updateOrganization(auth_details: Claims, organization: UpdateRequest, org_id: Long);

  final case class deleteOrganization(auth_details: Claims, org_id: Long);

  final case class getOrganization(auth_details: Claims, org_id: Long);

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  def receive: Receive = {

    case getOrganizations(auth_details, params) =>

      try {

        var page = 1

        var size = 0

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        var sort_by = ""

        var sort_order = ""

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = params("sort_by")

          sort_order = params("sort_order")

        }

        logger.info("Received request to fetch organizations")

        logger.info(s"Fetch organizations count with params :: ${params}")

        val totalCount: Int = Repository.getOrganizationsSearchCount(auth_details, params)

        val organizations: List[Organization] = Repository.getOrganizations(auth_details, params)

        if (organizations.size > 0)

          sender() ! GetOrganizationsResponse(200, "Success", size, totalCount, organizations)

        else

          sender() ! GetOrganizationsResponse(200, "Not Found", 0, 0, organizations)

      } catch {

        case e: Exception => {

          logger.error(s"Exception in get Organizations :: ${e.printStackTrace()}")

          sender() ! GetOrganizationsResponse(500, "Something went wrong, please try again", 0, 0, List.empty)

        }

      }
    case getOrganization(auth_details, org_id) =>

      try {

        var page = 1

        var size = 0

        val params = Map("id" -> org_id.toString)

        logger.info("Received request to fetch organizations")

        logger.info(s"Fetch organizations count with params :: ${params}")

        val totalCount: Int = Repository.getOrganizationsSearchCount(auth_details, params)

        val organizations: List[Organization] = Repository.getOrganizations(auth_details, params)

        if (organizations.size > 0)

          sender() ! GetOrganizationsResponse(200, "Success", size, totalCount, organizations)

        else

          sender() ! GetOrganizationsResponse(200, "Not Found", 0, 0, organizations)

      } catch {

        case e: Exception => {

          logger.error(s"Exception in get Organizations :: ${e.printStackTrace()}")

          sender() ! GetOrganizationsResponse(500, "Something went wrong, please try again", 0, 0, List.empty)

        }

      }

    case createOrganization(auth_details, organization) =>

      try {

        val org_id = Repository.createOrganization(auth_details, organization)

        sender() ! ActionPerformed(201, "Organization Created")

      } catch {

        case e: Exception => {

          logger.error(s"Exception in create Organization :: ${e.printStackTrace()}")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case updateOrganization(auth_details, organization, org_id) =>

      try {

        Repository.updateOrganization(auth_details, organization, org_id)

        sender() ! ActionPerformed(200, "Organization Updated")

      } catch {

        case e: Exception => {

          logger.error(s"Exception in update organization :: ${e.printStackTrace()}")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case deleteOrganization(auth_details, org_id) =>

      try {

        Repository.deleteOrganizations(org_id)

        sender() ! ActionPerformed(200, "Organization Deleted")

      } catch {

        case e: Exception => {

          logger.error(s"Exception in delete organization :: ${e.printStackTrace()}")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }


  }

}
