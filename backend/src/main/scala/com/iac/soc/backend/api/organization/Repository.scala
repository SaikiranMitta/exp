package com.iac.soc.backend.api.organization

import com.iac.soc.backend.api.organization.messages.Messages.Organization
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.organization.messages.Messages.CreateRequest
import com.iac.soc.backend.api.organization.messages.Messages.UpdateRequest
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, DBSession, LikeConditionEscapeUtil, SQL}
import com.iac.soc.backend.api.common.Utils._

private[organization] object Repository extends LazyLogging {

  def createOrganization(auth_details: Claims, organization: CreateRequest)(implicit session: DBSession = AutoSession): Long = {

    val ins_sql = s"""insert into organizations(`name`, `status`, `created_by`, `updated_by`) VALUES ('${escapeString(organization.name)}', '${escapeString(organization.status)}', ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})"""

    val org_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    org_id

  }

  def updateOrganization(auth_details: Claims, organization: UpdateRequest, org_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    val update_sql = s"""update organizations set `name` = '${escapeString(organization.name)}', `status` = '${escapeString(organization.status)}', `updated_by` = ${escapeString(auth_details.user_id)} where id = ${escapeString(org_id)} """

    SQL(update_sql).update().apply()

  }

  def getSearchFilters(auth_details: Claims, params: Map[String, String]): String = {

    /**
      * Filters
      */

    var whr = "";

    if (params.contains("name"))
      whr = whr + s""" name like '%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%' and """

    if (params.contains("status"))
      whr = whr + s""" status = '${escapeString(params("status"))}' and """

    if (params.contains("id"))
      whr = whr + s" id = ${escapeString(params("id"))} and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organizations")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organizations").split(",").toList)

        whr = whr + s" id in (${escapeString(organizations.mkString(","))}) and "

      } else {

        whr = whr + s" id in (${escapeString(params("organizations"))}) and "

      }
    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" id in (${escapeString(auth_details.organizations.mkString(","))}) and "

      }

    }

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim() + " and is_active = true"

      whr = " where " + whr.stripSuffix("and ").trim()

    } else {

      whr = " where is_active = true"

    }

    whr

  }

  def getOrganizations(auth_details: Claims, params: Map[String, String])(implicit session: DBSession = AutoSession): List[Organization] = {

    /**
      * Execute Query
      */

    logger.info("Fetching organizations from DB")

    var limit = ""

    if (params.contains("page") && params.contains("size")) {

      val page = params("page").toInt
      val size = params("size").toInt

      limit = s" limit ${(page - 1) * size}, ${size}"

    }

    var sort_by = ""

    var sort_order = ""

    var sort = ""

    /**
      * Sorting
      */

    if (params.contains("sort_by") && params.contains("sort_order")) {

      sort_by = s"${params("sort_by")}"

      sort_order = params("sort_order")

      sort = s"order by ${escapeString(sort_by)} ${escapeString(sort_order)}  "

    }

    var org_filters = getSearchFilters(auth_details, params);

    val sql = s"select * from organizations ${org_filters} ${sort} ${limit}"

    val orgList = SQL(sql).map(rs =>

      Organization(

        rs.int("id"),

        rs.string("name"),

        rs.string("status"),

        rs.int("created_by"),

        rs.string("created_on"),

        rs.int("updated_by"),

        rs.string("updated_on")

      )).list().apply()

    return orgList

  }

  def getOrganizationsSearchCount(auth_details: Claims, params: Map[String, String])(implicit session: DBSession = AutoSession): Int = {

    /**
      * Execute Query
      */

    logger.info("Fetching organizations count from DB")

    var org_filters = getSearchFilters(auth_details, params);

    val sql = s"select count(*) as count from organizations ${org_filters}"

    val totalCount = SQL(sql).map(rs =>

      rs.int("count")

    ).single().apply()

    return totalCount.get

  }

  def deleteOrganizations(org_id: Long)(implicit session: DBSession = AutoSession): Int = {

    /**
      * Execute Query
      */

    logger.info(s"Delete organization ${org_id}")

    val update_sql = s"""update organizations set `is_active` = false where id = ${escapeString(org_id)} """

    SQL(update_sql).update().apply()


  }

  def getOrganizationByName(org_name: String)(implicit session: DBSession = AutoSession): List[Organization] = {

    /**
      * Execute Query
      */

    logger.info(s"Searching organization ${org_name}")

    val search_sql = s"""select * from organizations where name = '${escapeString(org_name)}' """

    val orgList = SQL(search_sql).map(rs =>

      Organization(

        rs.int("id"),

        rs.string("name"),

        rs.string("status"),

        rs.int("created_by"),

        rs.string("created_on"),

        rs.int("updated_by"),

        rs.string("updated_on")

      )).list().apply()

    return orgList


  }
  def getUpdateOrganizationByName(org_name: String, id: Int)(implicit session: DBSession = AutoSession): List[Organization] = {

    /**
      * Execute Query
      */

    logger.info(s"Searching organization ${org_name}")

    val search_sql = s"""select * from organizations where name = '${escapeString(org_name)}' and id != ${id} """

    val orgList = SQL(search_sql).map(rs =>

      Organization(

        rs.int("id"),

        rs.string("name"),

        rs.string("status"),

        rs.int("created_by"),

        rs.string("created_on"),

        rs.int("updated_by"),

        rs.string("updated_on")

      )).list().apply()

    return orgList


  }

}
