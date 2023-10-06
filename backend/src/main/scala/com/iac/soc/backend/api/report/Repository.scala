package com.iac.soc.backend.api.report

import com.iac.soc.backend.api.common.mapping.ReportMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc._
import com.iac.soc.backend.api.common.Utils._

import scala.collection.mutable.ListBuffer

private[report] object Repository extends LazyLogging {

  implicit val session = AutoSession

  def getSearchFilters(auth_details: Claims, params: Map[String, String]): String = {

    /**
      * Filters
      */

    var whr = "";

    if (params.contains("name"))
      whr = whr + s""" r.name like "%${escapeString(LikeConditionEscapeUtil.escape(params("name")))}%" and """

    if (params.contains("status"))
      whr = whr + s" r.status = '${escapeString(params("status"))}' and "

    if (params.contains("is_global"))
      whr = whr + s" r.is_global = ${params("is_global").toBoolean} and "

    if (params.contains("id"))
      whr = whr + s" r.id = '${escapeString(params("id"))}' and "

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

  def getReportSearchCount(auth_details: Claims, params: Map[String, String]): Int = {

    val whr = getSearchFilters(auth_details, params)

    /**
      * fetch reports count
      */

    val innerJoinOrg = " left join reports_organizations as ro on (r.id = ro.report_id and ro.is_active = true) "
    val innerJoinCat = " left join reports_categories as rc on (r.id = rc.report_id and rc.is_active = true) "

    val getCountSql = s" select count(*) as total_count from ( select count(*) from reports as r  ${innerJoinOrg}  ${innerJoinCat} ${whr} group by r.id ) temp ";

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def getReports(auth_details: Claims, params: Map[String, String]): List[GetReportsMapping] = {

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
      * fetch reports count
      */

    val innerJoinOrg = " left join reports_organizations as ro on (r.id = ro.report_id and ro.is_active = true) "

    val innerJoinCat = " left join reports_categories as rc on (r.id = rc.report_id and rc.is_active = true) "

    val sql = s" select r.*, group_concat(distinct ro.organization_id) as filtered_org_ids, group_concat(distinct rc.category_id) as filtered_cat_ids, " +
      s" (select group_concat(distinct roi.organization_id) from reports_organizations roi where roi.report_id=r.id and roi.is_active = true) as org_ids," +
      s" (select group_concat(distinct rci.category_id) from reports_categories rci where rci.report_id=r.id and rci.is_active = true) as cat_ids " +
      s" from reports as r " +
      s"${innerJoinOrg} ${innerJoinCat}  ${whr} group by r.id  ${sort} limit ${(page - 1) * size}, ${size}";

    val reports = SQL(sql).map(rs =>

      GetReportsMapping(rs.int("r.id"),
        rs.string("r.name"),
        rs.string("r.query"),
        rs.string("r.status"),
        rs.string("r.cron_expression"),
        rs.boolean("r.is_global"),
        rs.boolean("r.is_active"),
        rs.string("r.created_on"),
        rs.stringOpt("r.updated_on"),
        rs.intOpt("r.created_by"),
        rs.intOpt("r.updated_by"),
        rs.stringOpt("org_ids"),
        rs.stringOpt("cat_ids")
      )).list().apply()

    return reports;

  }

  def getReportsOrganizations(report_id: Long): List[ReportOrganization] = {


    sql"select r.id as report_id, o.id, o.name from reports as r inner join reports_organizations as ro on r.id = ro.report_id inner join organizations o on (o.id = ro.organization_id ) where r.id in (${escapeString(report_id)}) and r.is_active = true and ro.is_active = true ".map(rs =>

      ReportOrganization(rs.long("report_id"), rs.string("o.name"), rs.long("o.id"))).list().apply()

  }

  def getReportsCategories(report_id: Long): List[ReportCategory] = {


    sql"select r.id as report_id, c.id, c.name from reports as r inner join reports_categories as rc on r.id = rc.report_id inner join categories c on c.id = rc.category_id where r.id in (${escapeString(report_id)}) and r.is_active = true and rc.is_active=true ".map(rs =>

      ReportCategory(rs.string("c.name"), rs.long("c.id"), rs.long("report_id"))).list().apply()

  }


  def insert(auth_details: Claims, report: ReportInsMapping)(implicit session: DBSession = AutoSession): Long = {

    /**
      * Insert into Reports
      */

    var report_id: Long = 0;

    val ins_sql = s"""insert into reports(`name`, `query`, `is_global`, `cron_expression`, `status`, `created_by`, `updated_by`) VALUES ('${escapeString(report.name)}','${escapeString(report.query)}', ${escapeString(report.is_global)}, '${escapeString(report.cron_expression)}', '${escapeString(report.status)}', ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)})"""

    report_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    return report_id;

  }

  def insertReportsOrganizations(auth_details: Claims, report: ReportInsMapping, report_id: Long)(implicit session: DBSession = AutoSession): Long = {

    var ins_str = "";

    report.organizations.foreach { org =>

      ins_str += s"(${escapeString(report_id)}, ${escapeString(org.id)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

    var org_ins_sql = "insert into reports_organizations(`report_id`, `organization_id`, `created_by`, `updated_by`) VALUES " + ins_str

    val org_report_id = SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

    return org_report_id

  }

  def insertReportsCategories(auth_details: Claims, report: ReportInsMapping, report_id: Long)(implicit session: DBSession = AutoSession): Long = {

    /**
      * Insert into Report Category Mapping
      */

    var cat_ins_str = "";

    report.categories.foreach { cat =>

      cat_ins_str += s"(${escapeString(report_id)}, ${escapeString(cat.id)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

    var cat_ins_sql = s"insert into reports_categories(`report_id`, `category_id`, `created_by`, `updated_by`) VALUES ${cat_ins_str}"

    val cat_report_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

    return cat_report_id

  }


  def updateReportOrganizations(auth_details: Claims, report: ReportUpdateMapping, report_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    /**
      * Select reports organizations
      */

    logger.info(s"Fetch report ${report_id} organizations")

    val present_org_list = SQL(s"select organization_id from reports_organizations where report_id = ${escapeString(report_id)}  and is_active = true ").map { rs => rs.int("organization_id") }.list().apply()

    val org_ins_list: ListBuffer[Long] = new ListBuffer[Long];

    val org_del_list: ListBuffer[Long] = new ListBuffer[Long];

    var request_org_list_temp: ListBuffer[Long] = new ListBuffer[Long];

    report.organizations.foreach { org =>

      request_org_list_temp += org.id

      if (!present_org_list.contains(org.id)) {

        org_ins_list += org.id

      }

    }

    if (!report.is_global) {

      present_org_list.foreach { org_id =>

        if (!request_org_list_temp.contains(org_id)) {

          org_del_list += org_id

        }

      }

      val request_org_list = request_org_list_temp.toList

      var ins_str = "";

      // insert org
      org_ins_list.foreach { org =>

        ins_str += s"(${escapeString(report_id)} , ${escapeString(org)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

      }

      ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

      var org_ins_sql = "";

      if (org_ins_list.size > 0) {

        org_ins_sql = s"insert into reports_organizations(`report_id`, `organization_id`, `created_by`, `updated_by`) VALUES ${ins_str} ON DUPLICATE KEY update is_active = true, updated_by = ${escapeString(auth_details.user_id)}"

        SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

      }

      // update org
      var org_update_sql = "";

      if (org_del_list.size > 0) {

        org_update_sql = s"update reports_organizations set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where report_id = " + report_id + s" and organization_id in (${org_del_list.toList.mkString(",")})"

        SQL(org_update_sql).update().apply()

      }

    } else {

      if (present_org_list.size > 0) {

        /**
          * Disable all organizations
          */

        logger.info(s" Disable all organizations for report ${report_id} as it is marked global")

        var org_update_sql = "";

        org_update_sql = s"update reports_organizations set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where report_id = ${escapeString(report_id)}"

        val org_report_update_id = SQL(org_update_sql).update().apply()

      }

    }

  }

  def updateReportCatgeories(auth_details: Claims, report: ReportUpdateMapping, report_id: Long)(implicit session: DBSession = AutoSession): Unit = {

    /**
      * Category
      */

    logger.info(s"Fetch report ${report_id} categories")

    val present_cat_list = SQL(s"select category_id from reports_categories where report_id = ${escapeString(report_id)} and is_active = true ").map { rs => rs.int("category_id") }.list().apply()

    val cat_ins_list: ListBuffer[Long] = new ListBuffer[Long];

    val cat_del_list: ListBuffer[Long] = new ListBuffer[Long];

    var request_cat_list_temp: ListBuffer[Long] = new ListBuffer[Long];

    report.categories.foreach { cat =>

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

      cat_ins_str += s"(${escapeString(report_id)}, ${escapeString(cat)}, ${escapeString(auth_details.user_id)}, ${escapeString(auth_details.user_id)}),"

    }

    cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

    var cat_ins_sql = "";

    if (cat_ins_list.size > 0) {

      logger.info(s"Insert categories for report ${report_id}")

      cat_ins_sql = s"insert into reports_categories(`report_id`, `category_id`, `created_by`, `updated_by`) VALUES ${cat_ins_str} ON DUPLICATE KEY update is_active = true, updated_by = ${auth_details.user_id}"

      val cat_report_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

    }

    /**
      * Update category
      */

    if (cat_del_list.size > 0) {

      logger.info(s"Disable categories for report ${report_id}")

      val cat_update_sql = s"update reports_categories set is_active = false, updated_by = ${escapeString(auth_details.user_id)} where report_id = ${escapeString(report_id)} and category_id in (${escapeString(cat_del_list.toList.mkString(","))})"

      SQL(cat_update_sql).update().apply()

    }

  }

  def updateReport(auth_details: Claims, report: ReportUpdateMapping, report_id: Int): Unit = {

    logger.info("Update report")

    var ins_sql = s"update reports set `name` = '${escapeString(report.name)}', `query` = '${escapeString(report.query)}', `is_global` = ${escapeString(report.is_global)}, `status` = '${escapeString(report.status)}', `cron_expression` = '${escapeString(report.cron_expression)}', updated_by = ${escapeString(auth_details.user_id)} where id = ${escapeString(report_id)}"

    SQL(ins_sql).update().apply()


  }

  def deleteReport(auth_details: Claims, report_id: Int): Unit = {

    logger.info(s"Delete report ${report_id}")

    val report_sql = s"update reports set status = 'disabled', `is_active` = false where id = ${escapeString(report_id)}"

    SQL(report_sql).update().apply()

  }

  def deleteReportOrganizations(auth_details: Claims, report_id: Int): Unit = {

    logger.info(s"Delete report organizations ${report_id}")

    val report_org_sql = s"update reports_organizations set `is_active` = false where report_id = ${escapeString(report_id)}"

    SQL(report_org_sql).update().apply()

  }

  def deleteReportCategories(auth_details: Claims, report_id: Int): Unit = {

    logger.info(s"Delete report categories ${report_id}")

    val report_cat_sql = s"update reports_categories set `is_active` = false where report_id = ${escapeString(report_id)}"

    SQL(report_cat_sql).update().apply()

  }

}