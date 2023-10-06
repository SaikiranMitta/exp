package com.iac.soc.backend.api.common

import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc._

object OrganizationService extends LazyLogging {

  implicit val session = AutoSession

  def getOrganizations(auth_details: Claims = null): List[Organization] = {

    try {
      /**
        * Execute Query
        */
      logger.info("Fetching organizations from DB")

      var org_filters = "";

      if (auth_details != null && auth_details.organizations != null && auth_details.organizations.size > 0) {

        org_filters = s" and id in (${auth_details.organizations.mkString(",")})"

      }

      val sql = s"select name, id from organizations where is_active = 1${org_filters}"

      val orgList = SQL(sql).map(rs =>

        Organization(

          rs.string("name"),

          rs.int("id")

        )).list().apply()

      return orgList

    }

    catch {

      case e: Exception => {

        e.printStackTrace()

        logger.error("Error in fetching organizations")

        null
      }

    }

  }

}