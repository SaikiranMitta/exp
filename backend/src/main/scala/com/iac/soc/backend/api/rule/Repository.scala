package com.iac.soc.backend.api.rule

import com.iac.soc.backend.api.common.Utils._
import com.iac.soc.backend.api.common.mapping.RuleMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc._

import scala.collection.mutable.ListBuffer

private[rule] object Repository extends LazyLogging {

  implicit val session = AutoSession

  def getSearchFilters(auth_details: Claims, params: Map[String, String]): String = {

    /**
      * Filters
      */

    var whr = "";

    if (params.contains("name"))
      whr = whr + s""" r.name like "%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%" and """

    if (params.contains("severity"))
      whr = whr + s" r.severity in ('${escapeString(params("severity")).split(",").mkString("','")}') and "

    if (params.contains("status"))
      whr = whr + s""" r.status = '${escapeString(params("status"))}' and """

    if (params.contains("is_global"))
      whr = whr + s" r.is_global = ${params("is_global").toBoolean} and "

    if (params.contains("id"))
      whr = whr + s" r.id = ${escapeString(params("id"))} and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organization")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

        whr = whr + s" (ro.organization_id in (${escapeString(organizations.mkString(","))}) or (r.is_global = true)) and "

      } else {

        whr = whr + s" (ro.organization_id in (${escapeString(params("organization"))}) or (r.is_global = true)) and "

      }

    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" (ro.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) or (r.is_global = true)) and "

      }

    }

    if (params.contains("category"))
      whr = whr + s" rc.category_id in (${escapeString(params("category"))}) and "

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim() + " and r.is_active = true"

      whr = " where " + whr.stripSuffix("and ").trim()

    } else {

      whr = " where r.is_active = true"

    }
    whr
  }

  def getRuleSearchCount(auth_details: Claims, params: Map[String, String]): Int = {

    val whr = getSearchFilters(auth_details, params)

    /**
      * fetch rules count
      */

    val innerJoinOrg = s" left join rules_organizations as ro on (r.id = ro.rule_id and ro.is_active = true) "
    val innerJoinCat = s" left join rules_categories as rc on (r.id = rc.rule_id and rc.is_active = true) "

    val getCountSql = s" select count(*) as total_count from ( select count(*) from rules as r  ${innerJoinOrg}  ${innerJoinCat} ${whr} group by r.id ) temp ";

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def getRules(auth_details: Claims, params: Map[String, String]): List[GetRulesMapping] = {

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

      sort_by = s"r.${params("sort_by")}"

      sort_order = params("sort_order")

      sort = s"order by ${escapeString(sort_by)} ${escapeString(sort_order)}  "

    }

    val whr = getSearchFilters(auth_details, params)

    /**
      * fetch rules count
      */

    val innerJoinOrg = s" left join rules_organizations as ro on (r.id = ro.rule_id and ro.is_active = true) "

    val innerJoinCat = s" left join rules_categories as rc on (r.id = rc.rule_id and rc.is_active = true) "

    val sql = s" select r.*, group_concat(distinct ro.organization_id) as filtered_org_ids, group_concat(distinct rc.category_id) as filtered_cat_ids, " +
      s" (select count(id) from incidents where rule_id = r.id) as incidents_count, " +
      s" (select group_concat(distinct roi.organization_id) from rules_organizations roi where roi.rule_id=r.id and roi.is_active = true) as org_ids," +
      s" (select group_concat(distinct rci.category_id) from rules_categories rci where rci.rule_id=r.id and rci.is_active = true) as cat_ids " +
      s" from rules as r " +
      s" ${innerJoinOrg} ${innerJoinCat}  ${whr} group by r.id  ${sort} limit ${(page - 1) * size}, ${size}";

    val rules = SQL(sql).map(rs =>

      GetRulesMapping(rs.int("r.id"),
        rs.string("r.name"),
        rs.string("r.query"),
        rs.stringOpt("r.description"),
        rs.string("r.severity"),
        rs.string("r.status"),
        rs.string("r.cron_expression"),
        rs.boolean("r.is_global"),
        rs.boolean("r.is_active"),
        rs.string("r.created_on"),
        rs.stringOpt("r.updated_on"),
        rs.intOpt("r.created_by"),
        rs.intOpt("r.updated_by"),
        rs.stringOpt("org_ids"),
        rs.stringOpt("cat_ids"),
        rs.int("incidents_count")
      )).list().apply()

    return rules;

  }

  def getRulesOrganizations(rule_id: Long): List[RuleOrganization] = {


    sql"select r.id as rule_id, o.id, o.name from rules as r inner join rules_organizations as ro on r.id = ro.rule_id inner join organizations o on (o.id = ro.organization_id ) where r.id in (${escapeString(rule_id)}) and r.is_active = true and ro.is_active = true ".map(rs =>

      RuleOrganization(rs.long("rule_id"), rs.string("o.name"), rs.long("o.id"))).list().apply()

  }

  def getRulesCategories(rule_id: Long): List[RuleCategory] = {


    sql"select r.id as rule_id, c.id, c.name from rules as r inner join rules_categories as rc on r.id = rc.rule_id inner join categories c on c.id = rc.category_id where r.id in (${escapeString(rule_id)}) and r.is_active = true and rc.is_active=true ".map(rs =>

      RuleCategory(rs.string("c.name"), rs.long("c.id"), rs.long("rule_id"))).list().apply()

  }


  def insert(auth_details: Claims, rule: RuleInsMapping)(implicit session: DBSession = AutoSession): Long = {

    /**
      * Insert into Rules
      */

    var rule_id: Long = 0;

    val ins_sql = s"""insert into rules(`name`, `query`, `description`, `severity`, `is_global`, `cron_expression`, `status`, `created_by`, `updated_by`) VALUES ('${escapeString(rule.name)}','${escapeString(rule.query)}','${escapeString(rule.description.getOrElse(""))}', '${escapeString(rule.severity)}', ${escapeString(rule.is_global)}, '${escapeString(rule.cron_expression)}', '${escapeString(rule.status)}', ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})"""

    rule_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    return rule_id;

  }

  def insertRulesOrganizations(auth_details: Claims, rule: RuleInsMapping, rule_id: Long)(implicit session: DBSession = AutoSession): Long = {

    var ins_str = "";

    rule.organizations.foreach { org =>

      ins_str += s"(${escapeString(rule_id)}, ${escapeString(org.id)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

    var org_ins_sql = "insert into rules_organizations(`rule_id`, `organization_id`, `created_by`, `updated_by`) VALUES " + ins_str

    val org_rule_id = SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

    return org_rule_id

  }

  def insertRulesCategories(auth_details: Claims, rule: RuleInsMapping, rule_id: Long)(implicit session: DBSession = AutoSession): Long = {

    /**
      * Insert into Rule Category Mapping
      */

    var cat_ins_str = "";

    rule.categories.foreach { cat =>

      cat_ins_str += s"(${escapeString(rule_id)}, ${escapeString(cat.id)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

    var cat_ins_sql = s"insert into rules_categories(`rule_id`, `category_id`, `created_by`, `updated_by`) VALUES ${cat_ins_str}"

    val cat_rule_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

    return cat_rule_id

  }


  def updateRuleOrganizations(auth_details: Claims, rule: RuleUpdateMapping, rule_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    /**
      * Select rules organizations
      */

    logger.info(s"Fetch rule ${rule_id} organizations")

    val present_org_list = SQL(s"select organization_id from rules_organizations where rule_id = ${escapeString(rule_id)}  and is_active = true ").map { rs => rs.int("organization_id") }.list().apply()

    val org_ins_list: ListBuffer[Long] = new ListBuffer[Long];

    val org_del_list: ListBuffer[Long] = new ListBuffer[Long];

    var request_org_list_temp: ListBuffer[Long] = new ListBuffer[Long];

    rule.organizations.foreach { org =>

      request_org_list_temp += org.id

      if (!present_org_list.contains(org.id)) {

        org_ins_list += org.id

      }

    }

    if (!rule.is_global) {

      present_org_list.foreach { org_id =>

        if (!request_org_list_temp.contains(org_id)) {

          org_del_list += org_id

        }

      }

      val request_org_list = request_org_list_temp.toList

      var ins_str = "";

      // insert org
      org_ins_list.foreach { org =>

        ins_str += s"(${escapeString(rule_id)} , ${escapeString(org)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

      }

      ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

      var org_ins_sql = "";

      if (org_ins_list.size > 0) {

        org_ins_sql = s"insert into rules_organizations(`rule_id`, `organization_id`, `created_by`, `updated_by`) VALUES ${ins_str} ON DUPLICATE KEY update is_active = true, updated_by = ${auth_details.user_id}"

        SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

      }

      // update org
      var org_update_sql = "";

      if (org_del_list.size > 0) {

        org_update_sql = s"update rules_organizations set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where rule_id = " + escapeString(rule_id) + s" and organization_id in (${escapeString(org_del_list.toList.mkString(","))})"

        SQL(org_update_sql).update().apply()

      }

    } else {

      if (present_org_list.size > 0) {

        /**
          * Disable all organizations
          */

        logger.info(s" Disable all organizations for rule ${rule_id} as it is marked global")

        var org_update_sql = "";

        org_update_sql = s"update rules_organizations set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where rule_id = ${escapeString(rule_id)}"

        val org_rule_update_id = SQL(org_update_sql).update().apply()

      }

    }

  }

  def updateRuleCatgeories(auth_details: Claims, rule: RuleUpdateMapping, rule_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    /**
      * Category
      */

    logger.info(s"Fetch rule ${rule_id} categories")

    val present_cat_list = SQL(s"select category_id from rules_categories where rule_id = ${escapeString(rule_id)} and is_active = true ").map { rs => rs.int("category_id") }.list().apply()

    val cat_ins_list: ListBuffer[Long] = new ListBuffer[Long];

    val cat_del_list: ListBuffer[Long] = new ListBuffer[Long];

    var request_cat_list_temp: ListBuffer[Long] = new ListBuffer[Long];

    rule.categories.foreach { cat =>

      request_cat_list_temp += cat.id

      if (present_cat_list.size == 0 || !present_cat_list.contains(cat.id)) {

        cat_ins_list += cat.id

      }

    }
    present_cat_list.foreach { cat_id =>

      if (!request_cat_list_temp.contains(cat_id)) {

        cat_del_list += cat_id

      }

    }

    val request_cat_list = request_cat_list_temp.toList

    var cat_ins_str = "";

    /**
      * Insert category
      */

    cat_ins_list.foreach { cat =>

      cat_ins_str += s"(${escapeString(rule_id)}, ${escapeString(cat)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

    var cat_ins_sql = "";

    if (cat_ins_list.size > 0) {

      logger.info(s"Insert categories for rule ${rule_id}")

      cat_ins_sql = s"insert into rules_categories(`rule_id`, `category_id`, `created_by`, `updated_by`) VALUES ${cat_ins_str} ON DUPLICATE KEY update is_active = true, updated_by = ${escapeString(auth_details.user_id)}"

      val cat_rule_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

    }

    /**
      * Update category
      */

    if (cat_del_list.size > 0) {

      logger.info(s"Disable categories for rule ${rule_id}")

      val cat_update_sql = s"update rules_categories set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where rule_id = ${escapeString(rule_id)} and category_id in (${escapeString(cat_del_list.toList.mkString(","))})"

      SQL(cat_update_sql).update().apply()

    }

  }

  def updateRule(auth_details: Claims, rule: RuleUpdateMapping, rule_id: Int): Unit = {

    logger.info("Update rule")

    var ins_sql = s"update rules set `name` = '${escapeString(rule.name)}', `query` = '${escapeString(rule.query)}', `description` = '${escapeString(rule.description.getOrElse(""))}', `severity` = '${escapeString(rule.severity)}', `is_global` = ${escapeString(rule.is_global)}, `status` = '${escapeString(rule.status)}', `cron_expression` = '${escapeString(rule.cron_expression)}', updated_by = ${escapeString(auth_details.user_id)} where id = ${escapeString(rule_id)}"

    SQL(ins_sql).update().apply()

  }

  def deleteRule(auth_details: Claims, rule_id: Int): Unit = {

    logger.info(s"Delete rule ${rule_id}")

    val rule_sql = s"update rules set status = 'disabled', `is_active` = false where id = ${escapeString(rule_id)}"

    SQL(rule_sql).update().apply()

  }

  def deleteRuleOrganizations(auth_details: Claims, rule_id: Int): Unit = {

    logger.info(s"Delete rule organizations ${rule_id}")

    val rule_org_sql = s"update rules_organizations set `is_active` = false where rule_id = ${escapeString(rule_id)}"

    SQL(rule_org_sql).update().apply()

  }

  def deleteRuleCategories(auth_details: Claims, rule_id: Int): Unit = {

    logger.info(s"Delete rule categories ${rule_id}")

    val rule_cat_sql = s"update rules_categories set `is_active` = false where rule_id = ${escapeString(rule_id)}"

    SQL(rule_cat_sql).update().apply()

  }

}