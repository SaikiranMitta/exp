package com.iac.soc.backend.api.user

import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.user.messages.Messages.{CreateRequest, UpdateRequest, User}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, DBSession, LikeConditionEscapeUtil, SQL}
import com.iac.soc.backend.api.common.Utils._

import scala.collection.mutable.ListBuffer

private[user] object Repository extends LazyLogging {

  def createUser(auth_details: Claims, user: CreateRequest)(implicit session: DBSession = AutoSession): Long = {

    /**
      * Special Handling for role value if null scalapb return it as blank
      */

    var role_column = ""

    var role_value = ""

    if (user.role != "") {

      role_column = "`role`, "

      role_value = s"""'${escapeString(user.role)}',"""

    }

    val ins_sql = s"""insert into users(`first_name`,`last_name`, `username`, `email`, ${role_column} `status`, `created_by`, `updated_by`) VALUES ('${escapeString(user.firstName)}', '${escapeString(user.lastName)}', '${escapeString(user.username)}', '${escapeString(user.email)}',  ${role_value} '${escapeString(user.status)}', ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})"""

    val user_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    /**
      * Insert into users organizations
      */

    if ((user.role == "" || user.role == None) && user.organizations.size > 0) {

      val user_organization = user.organizations.map(org => s"(${escapeString(user_id)}, ${escapeString(org.id)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})")

      val ins_user_org_sql = s"""insert into users_organizations(user_id, organization_id, created_by, updated_by) values ${user_organization.mkString(",")}"""

      val users_org = SQL(ins_user_org_sql).updateAndReturnGeneratedKey.apply()

    }

    user_id

  }

  def updateUser(auth_details: Claims, user_id: Long, user: UpdateRequest)(implicit session: DBSession = AutoSession): Unit = {

    /**
      * Special Handling for role value if null scalapb return it as blank
      */

    var role_column = ""

    var role_value = ""

    if (user.role != None && user.role != "") {

      role_column = s"""`role` = '${escapeString(user.role)}',"""

    } else {

      role_column = s"""`role` = null,"""

    }

    val ins_sql = s"""update users set `first_name` = '${escapeString(user.firstName)}', `last_name` = '${escapeString(user.lastName)}', `email` = '${escapeString(user.email)}', ${role_column} `status` = '${escapeString(user.status)}', `updated_by` = ${escapeString(auth_details.user_id)} where id = ${escapeString(user_id)}"""

    SQL(ins_sql).update().apply()

    /**
      * Update users organizations
      */

    var present_org_list: List[Int] = SQL(s"select organization_id from users_organizations where user_id = ${escapeString(user_id)} and is_active = true ").map { rs => rs.int("organization_id") }.list().apply()

    if ((user.role == "" || user.role == None) && user.organizations.size > 0) {

      logger.info(s"Fetch user  ${user_id}'s existing organizations")

      val org_ins_list: ListBuffer[Long] = new ListBuffer[Long];

      val org_del_list: ListBuffer[Long] = new ListBuffer[Long];

      var request_org_list_temp: ListBuffer[Long] = new ListBuffer[Long];

      user.organizations.foreach { org =>

        request_org_list_temp += org.id

        if (!present_org_list.contains(org.id)) {

          org_ins_list += org.id

        }

      }

      present_org_list.foreach { org_id =>

        if (!request_org_list_temp.contains(org_id)) {

          org_del_list += org_id

        }

      }

      val request_org_list = request_org_list_temp.toList

      var ins_list: ListBuffer[String] = new ListBuffer[String];

      // insert org
      ins_list = org_ins_list.map { org =>

        s"(${escapeString(user_id)} , ${escapeString(org)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})"

      }

      var org_ins_sql = "";

      if (org_ins_list.size > 0) {

        org_ins_sql = "insert into users_organizations(`user_id`, `organization_id`,`created_by`, `updated_by`) VALUES " + ins_list.mkString(",") + s" ON DUPLICATE KEY update is_active = true, updated_by = ${escapeString(auth_details.user_id)}"

        SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

      }

      // update org
      var org_update_sql = "";

      if (org_del_list.size > 0) {

        org_update_sql = s"update users_organizations set is_active = false, updated_by=${escapeString(auth_details.user_id)} where user_id = " + escapeString(user_id) + s" and organization_id in (${escapeString(org_del_list.toList.mkString(","))})"

        SQL(org_update_sql).update().apply()

      }

    } else {

      if (present_org_list.size > 0) {

        /**
          * Disable all organizations
          */

        logger.info(s" Disable all organizations for user ${user_id} as per request")

        var org_update_sql = "";

        org_update_sql = s"update users_organizations set is_active = false, updated_by=${escapeString(auth_details.user_id)} where user_id = ${escapeString(user_id)}"

        val org_user_update_id = SQL(org_update_sql).update().apply()

      }

    }

  }

  def updateUserIDP(auth_details: Claims, keycloak_user_id: String, user_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    val ins_sql = s"""update users set `idp_user_id` = '${escapeString(keycloak_user_id)}', updated_by = ${escapeString(auth_details.user_id)} where id = '${escapeString(user_id)}'"""

    SQL(ins_sql).update().apply()

  }

  def deleteUser(auth_details: Claims, user_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    val del_sql = s"""update users set `is_active` = false, updated_by=${escapeString(auth_details.user_id)} where id = '${escapeString(user_id)}'"""

    SQL(del_sql).update().apply()

    val del_user_org_sql = s"""update users_organizations set `is_active` = false, updated_by = ${escapeString(auth_details.user_id)} where user_id = '${escapeString(user_id)}'"""

    SQL(del_user_org_sql).update().apply()

  }

  def getUser(user_id: Long)(implicit session: DBSession = AutoSession): Option[User] = {

    val ins_sql = s"""select * from users  where id = "${escapeString(user_id)}" and is_active=true"""

    val user = SQL(ins_sql).map(rs =>

      User(
        rs.int("id"),
        rs.string("first_name"),
        rs.string("last_name"),
        rs.string("username"),
        rs.string("email"),
        rs.string("role"),
        rs.string("status"),
        rs.string("idp_user_id"),
        rs.int("created_by"),
        rs.string("created_on"),
        rs.int("updated_by"),
        rs.string("updated_on"),
      )).single().apply()

    return user;

  }

  def getSearchFilters(params: Map[String, String]): String = {

    /**
      * Filters
      */

    var whr = "";

    if (params.contains("name"))
      whr = whr + s" (u.first_name like '%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%' or u.last_name like '%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%')  and "

    if (params.contains("username"))
      whr = whr + s" u.username like '%${escapeString(LikeConditionEscapeUtil.escape(params("username")))}%' and "

    if (params.contains("status"))
      whr = whr + s" u.status = '${escapeString(params("status"))}' and "

    if (params.contains("email"))
      whr = whr + s" u.email like '%${escapeString(LikeConditionEscapeUtil.escape(params("email")))}%' and "

    if (params.contains("role"))
      whr = whr + s" u.role = '${escapeString(params("role"))}' and "

    if (params.contains("id"))
      whr = whr + s" u.id = ${escapeString(params("id"))} and "

    /**
      * Organization filter
      */

    if (params.contains("organizations")) {

      whr = whr + s" uo.organization_id in (${escapeString(params("organizations"))}) and "

    }

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim() + " and u.is_active = true"

      whr = " where " + whr.stripSuffix("and ").trim()

    } else {

      whr = " where u.is_active = true"

    }

    whr

  }

  def getUsers(params: Map[String, String])(implicit session: DBSession = AutoSession): List[User] = {

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

      sort_by = s"u.${params("sort_by")}"

      sort_order = params("sort_order")

      sort = s"order by ${escapeString(sort_by)} ${escapeString(sort_order)}  "

    }

    var whr = getSearchFilters(params)

    var innerJoinOrg = "";

    innerJoinOrg = " left join users_organizations as uo on (u.id = uo.user_id and uo.is_active = true) "

    val sql = s" select u.*, group_concat(distinct uo.organization_id) as filtered_org_ids, " +
      s" (select group_concat(distinct uoi.organization_id) from users_organizations uoi where uoi.user_id=u.id and uoi.is_active = true) as org_ids " +
      s" from users as u " +
      s"${innerJoinOrg} ${whr} group by u.id  ${sort} limit ${(page - 1) * size}, ${size}";

    val users = SQL(sql).map(rs =>

      User(
        rs.int("u.id"),
        rs.string("u.first_name"),
        rs.string("u.last_name"),
        rs.string("u.username"),
        rs.string("u.email"),
        rs.string("u.role"),
        rs.string("u.status"),
        rs.string("u.idp_user_id"),
        rs.int("u.created_by"),
        rs.string("u.created_on"),
        rs.int("u.updated_by"),
        rs.string("u.updated_on"),
        null,
        rs.string("org_ids")
      )).list().apply()

    return users;

  }

  def getUserSearchCount(params: Map[String, String])(implicit session: DBSession = AutoSession): Int = {

    var whr = getSearchFilters(params)

    var innerJoinOrg = "";

    innerJoinOrg = " left join users_organizations as uo on (u.id = uo.user_id and uo.is_active = true) "

    val getCountSql = s" select count(*) as total_count from ( select count(*) from users as u ${innerJoinOrg} ${whr} group by u.id ) temp ";

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def getCheckUserExists(username: String, email: String)(implicit session: DBSession = AutoSession): List[User] = {
    var whr = " where username != '" + escapeString(username) + "' and email = '" + escapeString(email) + "' and is_active = true"

    val sql = s" select * from users " + whr;

    println(sql)

    val users = SQL(sql).map(rs =>

      User(
        rs.int("id"),
        rs.string("first_name"),
        rs.string("last_name"),
        rs.string("username"),
        rs.string("email"),
        rs.string("role"),
        rs.string("status"),
        rs.string("idp_user_id"),
        rs.int("created_by"),
        rs.string("created_on"),
        rs.int("updated_by"),
        rs.string("updated_on")
      )).list().apply()

    return users;
  }

  def getUserByUsername(username: String)(implicit session: DBSession = AutoSession): List[User] = {

    var whr = " where username = '" + escapeString(username) + "' and is_active = true"

    val sql = s" select * from users " + whr;

    val users = SQL(sql).map(rs =>

      User(
        rs.int("id"),
        rs.string("first_name"),
        rs.string("last_name"),
        rs.string("username"),
        rs.string("email"),
        rs.string("role"),
        rs.string("status"),
        rs.string("idp_user_id"),
        rs.int("created_by"),
        rs.string("created_on"),
        rs.int("updated_by"),
        rs.string("updated_on")
      )).list().apply()

    return users;

  }

  def getUserByEmail(email: String)(implicit session: DBSession = AutoSession): List[User] = {

    var whr = " where email = '" + escapeString(email) + "' and is_active = true"

    val sql = s" select * from users " + whr;

    val users = SQL(sql).map(rs =>

      User(
        rs.int("id"),
        rs.string("first_name"),
        rs.string("last_name"),
        rs.string("username"),
        rs.string("email"),
        rs.string("role"),
        rs.string("status"),
        rs.string("idp_user_id"),
        rs.int("created_by"),
        rs.string("created_on"),
        rs.int("updated_by"),
        rs.string("updated_on")
      )).list().apply()

    return users;

  }

}