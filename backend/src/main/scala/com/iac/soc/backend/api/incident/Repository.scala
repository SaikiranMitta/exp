package com.iac.soc.backend.api.incident

import java.text.SimpleDateFormat
import java.util.{Date, SimpleTimeZone}

import com.iac.soc.backend.api.common.Utils._
import com.iac.soc.backend.api.common.mapping.IncidentMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, LikeConditionEscapeUtil, SQL}

private[incident] object Repository extends LazyLogging {

  implicit val session = AutoSession

  private val dateFmt = "yyyy-MM-dd HH:mm:ss"

  def getIncidentSummaryFilters(auth_details: Claims, params: Map[String, String]): String = {

    /**
      * Filters
      */

    var whr = "";

    if (params.contains("name"))
      whr = whr + s" r.name like '%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%' and "

    if (params.contains("severity"))
      whr = whr + s" r.severity in ('${escapeString(params("severity")).split(",").mkString("','")}') and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organization")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

        whr = whr + s" i.organization_id in (${escapeString(organizations.mkString(","))}) and "

      } else {

        whr = whr + s" i.organization_id in (${escapeString(params("organization"))}) and "

      }

    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) and "

      }

    }

    if (params.contains("rule")) {
      val rules = params("rule").split(",").toList
      whr = whr + s" r.id in (${escapeString(rules.mkString(","))})" + " and "
    }


    if (params.contains("category"))
      whr = whr + s" rc.category_id in (${escapeString(params("category"))})" + " and "

    if (params.contains("time_range_from") && params.contains(("time_range_to")))
      whr = whr + s" i.created_on >= '${escapeString(params(s"time_range_from"))}' and i.created_on <= '${escapeString(params(s"time_range_to"))}' and "

    else {

      val currDate = new Date

      val sdf = new SimpleDateFormat(dateFmt)

      val queryCurrDate = sdf.format(currDate)

      val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))

      val queryMinus24HoursDate = sdf.format(minus24HoursDate)

      whr = whr + s" i.created_on >= '${queryMinus24HoursDate}' and i.created_on <= '${queryCurrDate}' and "
    }

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim()

      whr = " where " + whr.stripSuffix("and ").trim()

    }

    whr

  }

  def getIncidentSummaryCount(auth_details: Claims, params: Map[String, String]): Int = {

    val whr = getIncidentSummaryFilters(auth_details, params)

//    val sql = s" select count(*) as total_count from ( select count(*) from incidents as i " +
//      s" inner join rules as r on i.rule_id = r.id " +
//      s" inner join organizations as o on i.organization_id = o.id " +
//      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )" + whr + " group by r.id ) temp";

    val sql = s" select count(distinct(r.id)) as total_count from incidents as i " +
      s" inner join rules as r on i.rule_id = r.id " +
//      s" inner join organizations as o on i.organization_id = o.id " +
      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )" + whr

    val totalCount = SQL(sql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def getIncidentSummary(auth_details: Claims, params: Map[String, String], setLimit: Boolean): List[IncidentRuleSummaryMapping] = {

    var limit = ""

    if (params.contains("page") && params.contains("size")) {

      val page = params("page").toInt
      val size = params("size").toInt

      if (setLimit) {

        limit = s" limit ${(page - 1) * size} , ${size}"

      }

    }

    var sort_by = ""

    var sort_order = ""

    var sort = ""

    /**
      * Sorting
      */
    if (params.contains("sort_by") && params.contains("sort_order")) {

      if (params("sort_by") == "incidents_count") {

        sort_by = params("sort_by")

      } else if (params("sort_by") == "created_on") {

        sort_by = params("sort_by")

      } else {

        sort_by = "r." + params("sort_by")

      }

      sort_order = params("sort_order")

      sort = s"order by ${escapeString(sort_by)} ${escapeString(sort_order)}"

    }

    /**
      * Filters
      */

    val whr =  getIncidentSummaryFilters(auth_details, params)

    val sql = s"select r.id as rule_id, r.name, r.severity, max(i.created_on) as created_on, count(i.id) as incidents_count, GROUP_CONCAT(distinct rc.category_id) as category_ids  from incidents as i " +
      s" inner join rules as r on i.rule_id = r.id " +
//      s" inner join organizations as o on i.organization_id = o.id " +
      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )" + whr + " group by r.id " + sort + limit;

    val RuleIncident: List[IncidentRuleSummaryMapping] = SQL(sql).map(rs =>

      IncidentRuleSummaryMapping(
        rs.int("r.rule_id"),
        rs.string("r.name"),
        rs.string("r.severity"),
        rs.string("created_on"),
        rs.int("incidents_count"),
        rs.stringOpt("category_ids")
      )).list().apply()

    return RuleIncident;

  }

  def getIncidentByRuleCount(auth_details: Claims, params: Map[String, String], rule_id: Long): Long = {

    /**
      * Filters
      */
    var whr = "";

    if (params.contains("name"))
      whr = whr + " r.name like '%" + escapeString(LikeConditionEscapeUtil.escape(params("name"))) + "%' and "

    if (params.contains("severity"))
      whr = whr + " r.severity in ('" + escapeString(params("severity")).split(",").mkString("','") + "') and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organization")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

        whr = whr + s" i.organization_id in (${escapeString(organizations.mkString(","))}) and "

      } else {

        whr = whr + s" i.organization_id in (${escapeString(params("organization"))}) and "

      }

    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) and "

      }

    }

    if (params.contains("category"))
      whr = whr + s" rc.category_id in (${escapeString(params("category"))}) and "

    if (params.contains("time_range_from") && params.contains(("time_range_to")))
      whr = whr + s" i.created_on >= '${escapeString(params(s"time_range_from"))}' and i.created_on<= '${escapeString(params(s"time_range_to"))}' and "

    else {

      val currDate = new Date
      val sdf = new SimpleDateFormat(dateFmt)
      val queryCurrDate = sdf.format(currDate)

      val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
      val queryMinus24HoursDate = sdf.format(minus24HoursDate)

      whr = whr + s" i.created_on >= '${queryMinus24HoursDate}' and i.created_on <= '${queryCurrDate}' and "

    }

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim()

      whr = s" where i.rule_id = ${rule_id} and ${whr.stripSuffix("and ").trim()}"

    } else {

      whr = s" where i.rule_id = ${rule_id} "

    }

    val sql = s"select count(*) as total_count from (select count(*)  from incidents as i " +
      s" inner join rules as r on i.rule_id = r.id " +
      s" inner join organizations as o on i.organization_id = o.id " +
      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )" +
      s" left join incidents_attachments as ia on i.id = ia.incident_id " + whr + " group by i.id ) temp";

    val totalCount = SQL(sql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;
  }

  def getIncidentByRule(auth_details: Claims, params: Map[String, String], rule_id: Long): List[IncidentRuleMapping] = {

    var limit = ""

    if (params.contains("page") && params.contains("size")) {

      val page = params("page").toInt
      val size = params("size").toInt

      limit = s" limit ${(page - 1) * size} , ${size}"

    }

    var sort_by = ""

    var sort_order = ""

    var sort = ""

    /**
      * Sorting
      */
    if (params.contains("sort_by") && params.contains("sort_order")) {

      if (params("sort_by") == "incidents_count") {

        sort_by = params("sort_by")

      } else {

        sort_by = "i." + params("sort_by")

      }
      sort_order = params("sort_order")
      sort = "order by " + escapeString(sort_by) + " " + escapeString(sort_order) + " "

    }

    /**
      * Filters
      */
    var whr = "";

    if (params.contains("name"))
      whr = whr + " r.name like '%" + escapeString(LikeConditionEscapeUtil.escape(params("name"))) + "%' and "

    if (params.contains("severity"))
      whr = whr + " r.severity in ('" + escapeString(params("severity")).split(",").mkString("','") + "') and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organization")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

        whr = whr + s" i.organization_id in (${escapeString(organizations.mkString(","))}) and "

      } else {

        whr = whr + s" i.organization_id in (${escapeString(params("organization"))}) and "

      }

    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) and "

      }

    }

    if (params.contains("category"))
      whr = whr + s" rc.category_id in (${escapeString(params("category"))}) and "

    if (params.contains("time_range_from") && params.contains(("time_range_to")))
      whr = whr + s" i.created_on >= '${escapeString(params(s"time_range_from"))}' and i.created_on<= '${escapeString(params(s"time_range_to"))}' and "

    else {

      val currDate = new Date
      val sdf = new SimpleDateFormat(dateFmt)
      val queryCurrDate = sdf.format(currDate)

      val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
      val queryMinus24HoursDate = sdf.format(minus24HoursDate)

      whr = whr + s" i.created_on >= '${queryMinus24HoursDate}' and i.created_on <= '${queryCurrDate}' and "

    }
    if (whr != "") {

      whr = whr.stripSuffix("and ").trim()

      whr = s" where i.rule_id = ${escapeString(rule_id)} and ${whr.stripSuffix("and ").trim()}"

    } else {

      whr = s" where i.rule_id = ${escapeString(rule_id)} "

    }


    val sql = s"select i.id, i.rule_id, i.query, i.created_on, i.organization_id, GROUP_CONCAT(ia.id) as attachment_ids from incidents as i " +
      s" inner join rules as r on i.rule_id = r.id " +
      s" inner join organizations as o on i.organization_id = o.id " +
      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )" +
      s" left join incidents_attachments as ia on i.id = ia.incident_id " + whr + " group by i.id " + sort + limit;

    val Incidents: List[IncidentRuleMapping] = SQL(sql).map(rs =>

      IncidentRuleMapping(

        rs.long("i.id"),

        rs.long("i.rule_id"),

        rs.string("i.query"),

        rs.string("i.created_on"),

        rs.int("i.organization_id"),

        rs.stringOpt("attachment_ids")

      )).list().apply()

    return Incidents;

  }

  def getIncidentsSummaryByRuleCount(auth_details: Claims, params: Map[String, String], rule_id: Long): Long = {

    /**
      * Filters
      */
    var whr = "";

    if (params.contains("name"))
      whr = whr + " r.name like '%" + escapeString(LikeConditionEscapeUtil.escape(params("name"))) + "%' and "

    if (params.contains("severity"))
      whr = whr + " r.severity in ('" + escapeString(params("severity")).split(",").mkString("','") + "') and "

    /**
      * Authorization logic and organization filter
      */

    if (params.contains("organization")) {

      var organizations: List[String] = List.empty

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

        whr = whr + s" i.organization_id in (${escapeString(organizations.mkString(","))}) and "

      } else {

        whr = whr + s" i.organization_id in (${escapeString(params("organization"))}) and "

      }

    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) and "

      }

    }

    if (params.contains("category"))
      whr = whr + s" rc.category_id in (${params("category")}) and "

    if (params.contains("time_range_from") && params.contains(("time_range_to")))
      whr = whr + s" i.created_on >= '${escapeString(params(s"time_range_from"))}' and i.created_on<= '${escapeString(params(s"time_range_to"))}' and "

    else {

      val currDate = new Date
      val sdf = new SimpleDateFormat(dateFmt)
      val queryCurrDate = sdf.format(currDate)

      val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
      val queryMinus24HoursDate = sdf.format(minus24HoursDate)

      whr = whr + s" i.created_on >= '${queryMinus24HoursDate}' and i.created_on <= '${queryCurrDate}' and "

    }

    if (whr != "") {

      whr = whr.stripSuffix("and ").trim()

      whr = s" where i.rule_id = ${escapeString(rule_id)} and ${whr.stripSuffix("and ").trim()}"

    } else {

      whr = s" where i.rule_id = ${escapeString(rule_id)} "

    }

    var groupBy = "";

    if (params.contains("time_slot_unit")) {

      var groupValue = 1;

      if (params.contains("time_slot_value")) {
        groupValue = params("time_slot_value").toInt
      }

      if (params("time_slot_unit") == "year") {
        groupBy = s" FLOOR(Year(i.created_on)/${groupValue})"
      }

      if (params("time_slot_unit") == "month") {
        groupBy = s" Year(i.created_on), FLOOR(Month(i.created_on)/${groupValue})"
      }

      if (params("time_slot_unit") == "week") {
        groupBy = s" Year(i.created_on),Month(i.created_on), FLOOR(Week(i.created_on)/${groupValue})"
      }

      if (params("time_slot_unit") == "day") {
        groupBy = s" Year(i.created_on),Month(i.created_on), FLOOR(Day(i.created_on)/${groupValue})"
      }

      if (params("time_slot_unit") == "hour") {
        groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on), FLOOR(Hour(i.created_on)/${groupValue})"
      }

      if (params("time_slot_unit") == "minute") {

        groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on),Hour(i.created_on), FLOOR(Minute(i.created_on)/${groupValue})"

      }

    } else {

      groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on),Hour(i.created_on)"

    }

    val sql = s"select count(*) as total_count from (select count(*) from incidents as i" +
      s" inner join rules as r on i.rule_id = r.id " +
      s" inner join organizations as o on i.organization_id = o.id " +
      s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )  ${whr} group by ${groupBy}) temp";

    val totalCount = SQL(sql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def getIncidentsSummaryByRule(auth_details: Claims, params: Map[String, String], rule_id: Long): List[IncidentRuleBucketReponseMapping] = {

    try {

      var limit_query = ""

      if (params.contains("page") && params.contains("size")) {

        val page = params("page").toInt
        val size = params("size").toInt

        limit_query = s"limit ${(page - 1) * size} , ${size}"
      }

      var sort_by = ""

      var sort_order = ""

      var sort = ""

      /**
        * Sorting
        */
      if (params.contains("sort_by") && params.contains("sort_order")) {

        if (params("sort_by") == "incidents_count" || params("sort_by") == "time_frame_from" || params("sort_by") == "time_frame_to") {

          sort_by = params("sort_by")

        } else {

          sort_by = "r." + params("sort_by")

        }
        sort_order = params("sort_order")
        sort = "order by " + escapeString(sort_by) + " " + escapeString(sort_order) + " "

      }

      /**
        * Filters
        */
      var whr = "";

      if (params.contains("name"))
        whr = whr + " r.name like '%" + escapeString(LikeConditionEscapeUtil.escape(params("name"))) + "%' and "

      if (params.contains("severity"))
        whr = whr + " r.severity in ('" + escapeString(params("severity")).split(",").mkString("','") + "') and "

      if (params.contains("organization"))
        whr = whr + s" o.id in (${escapeString(params("organization"))}) and "

      /**
        * Authorization logic and organization filter
        */

      if (params.contains("organization")) {

        var organizations: List[String] = List.empty

        if (auth_details.organizations != null && auth_details.organizations.size > 0) {

          organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

          whr = whr + s" i.organization_id in (${escapeString(organizations.mkString(","))}) and "

        } else {

          whr = whr + s" i.organization_id in (${escapeString(params("organization"))}) and "

        }

      } else {

        if (auth_details.organizations != null && auth_details.organizations.size > 0) {

          whr = whr + s" i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) and "

        }

      }

      if (params.contains("category"))
        whr = whr + s" rc.category_id in (${params("category")}) and "

      if (params.contains("time_range_from") && params.contains(("time_range_to")))
        whr = whr + s" i.created_on >= '${escapeString(params(s"time_range_from"))}' and i.created_on<= '${escapeString(params(s"time_range_to"))}' and "

      else {

        val currDate = new Date
        val sdf = new SimpleDateFormat(dateFmt)
        val queryCurrDate = sdf.format(currDate)

        val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
        val queryMinus24HoursDate = sdf.format(minus24HoursDate)

        whr = whr + s" i.created_on >= '${queryMinus24HoursDate}' and i.created_on <= '${queryCurrDate}' and "

      }

      if (whr != "") {

        whr = whr.stripSuffix("and ").trim()

        whr = s" where i.rule_id = ${escapeString(rule_id)} and ${whr.stripSuffix("and ").trim()}"

      } else {

        whr = s" where i.rule_id = ${escapeString(rule_id)} "

      }

      var groupBy = "";

      if (params.contains("time_slot_unit")) {

        var groupValue = 1;

        if (params.contains("time_slot_value")) {
          groupValue = params("time_slot_value").toInt
        }

        if (params("time_slot_unit") == "year") {
          groupBy = s" FLOOR(Year(i.created_on)/${groupValue})"
        }

        if (params("time_slot_unit") == "month") {
          groupBy = s" Year(i.created_on), FLOOR(Month(i.created_on)/${groupValue})"
        }

        if (params("time_slot_unit") == "week") {
          groupBy = s" Year(i.created_on),Month(i.created_on), FLOOR(Week(i.created_on)/${groupValue})"
        }

        if (params("time_slot_unit") == "day") {
          groupBy = s" Year(i.created_on),Month(i.created_on), FLOOR(Day(i.created_on)/${groupValue})"
        }

        if (params("time_slot_unit") == "hour") {
          groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on), FLOOR(Hour(i.created_on)/${groupValue})"
        }

        if (params("time_slot_unit") == "minute") {
          groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on),Hour(i.created_on), FLOOR(Minute(i.created_on)/${groupValue})"
        }

      } else {

        groupBy = s" Year(i.created_on),Month(i.created_on),Day(i.created_on),Hour(i.created_on)"

      }

      val sql = s"select min(i.created_on) as time_range_from, max(i.created_on) as time_range_to , count(*) as incidents_count from incidents as i  " +
        s" inner join rules as r on i.rule_id = r.id " +
        s" inner join organizations as o on i.organization_id = o.id " +
        s" inner join rules_categories as rc on (rc.rule_id = r.id and rc.is_active = true )  ${whr} group by ${groupBy} ${sort} ${limit_query}";

      val Incidents: List[IncidentRuleBucketReponseMapping] = SQL(sql).map(rs =>

        IncidentRuleBucketReponseMapping(rs.string("time_range_from"),
          rs.string("time_range_to"),
          rs.long("incidents_count"),
        )).list().apply()

      return Incidents;


    } catch {

      case e: Exception => {

        logger.error(s"Error while fetching incident summary by rule ${e}")

        return null

      }

    }

  }


  def getIncidentsById(auth_details: Claims, incident_id: Long): List[IncidentById] = {

    try {

      /**
        * Authorization logic and organization filter
        */
      var whr = ""


      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" and i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) "

      }

      val sql = s"select * from incidents as i where i.id = ${escapeString(incident_id)}" + whr;

      val Incidents: List[IncidentById] = SQL(sql).map(rs =>

        IncidentById(

          rs.long("i.id"),
          rs.long("i.rule_id"),
          rs.string("i.query"),
          rs.long("i.organization_id"),
          rs.long("i.total_hits"),
          rs.string("i.created_on"),

        )).list().apply()

      return Incidents;


    } catch {

      case e: Exception => {

        logger.error(s"Error while fetching incident summary by rule ${e}")

        return null

      }

    }

  }

}