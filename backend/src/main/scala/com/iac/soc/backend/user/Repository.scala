package com.iac.soc.backend.user

import com.iac.soc.backend.api.common.OrganizationService
import com.iac.soc.backend.user.models.{Organization, User}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc._

/**
  * The user sub-system repository
  */
private[user] object Repository extends LazyLogging {

  implicit val session = AutoSession

  /**
    * Gets the organizations present in the system
    *
    * @return the collection of organizations
    */

  def getOrganizations(): Vector[Organization] = {

    logger.info("Fetching the organizations")

    val organizations: Vector[Organization] = OrganizationService.getOrganizations().map { org => Organization(org.id.toInt, org.name) }.toVector


    /*Vector(
      Organization(1, "homeadvisor"),
      Organization(2, "vimeo"),
      Organization(3, "dailybeast")
    )*/

    organizations

  }

  /**
    * Gets the SOC users (admin and site admins)
    *
    * @return the SOC users
    */
  def getSocUsers(): Vector[User] = {

    try {
      logger.info("Fetching SOC users")

      val sql = s"select concat(first_name, ' ', last_name) as name, id, email from users where role is not null and status = 'enabled' and is_active = 1"

      val users = SQL(sql).map(rs =>

        User(
          rs.int("id"),

          rs.string("name"),

          rs.string("email"),

        )).list().apply().toVector

      users

    } catch {

      case e: Exception => {

        logger.error("Error in fetching soc users")

        e.printStackTrace()

        null

      }

    }
  }

  /**
    * Gets the users within the specified organization
    *
    * @param organizationId the id of the organization
    * @return the users within the organization
    */
  def getUsersByOrganization(organizationId: Int): Vector[User] = {

    try {

      logger.info(s"Fetching the users for organization id: ${organizationId}")

      val sql = s"select concat(first_name, ' ', last_name) as name, u.id, email from users u inner join users_organizations uo on uo.user_id = u.id  where role is null and u.status = 'enabled' and u.is_active = 1 and uo.is_active = 1 and uo.organization_id = ${organizationId}"

      val users = SQL(sql).map(rs =>

        User(
          rs.int("u.id"),

          rs.string("name"),

          rs.string("email"),

        )).list().apply().toVector

      users

    } catch {

      case e: Exception => {

        logger.error("Error in fetching users by organization")

        e.printStackTrace()

        null

      }

    }
    /*Vector(
      User(2, "Kevin Rickard", "sunu.sasidharan@0gmail.com")
    )*/

  }

}
