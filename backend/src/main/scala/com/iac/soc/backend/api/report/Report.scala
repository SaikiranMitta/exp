package com.iac.soc.backend.api.report

import akka.actor.{Actor, Props}
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization
import com.iac.soc.backend.api.common.mapping.ReportMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.{CategoryService, OrganizationService}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{DB, _}

import scala.collection.mutable.ListBuffer


object ReportActor {

  final case class getReports(auth_details: Claims, params: Map[String, String]);

  final case class createReport(auth_details: Claims, Report: ReportInsMapping)

  final case class updateReport(auth_details: Claims, report: ReportUpdateMapping, Report_id: Int)

  final case class getReport(auth_details: Claims, id: Int)

  final case class deleteReport(auth_details: Claims, id: Int)

  def props: Props = Props[ReportActor]
}

class ReportActor extends Actor with LazyLogging {

  import ReportActor._

  //getConnection();

  implicit val session = AutoSession

  def receive: Receive = {

    /*
      Fetch, Sort, Filter Reports
     */

    case getReports(auth_details, params) =>

      try {

        var page = 1
        var size = 5

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt
          size = params("size").toInt

        }

        var report_ids: ListBuffer[Long] = new ListBuffer[Long];

        var sort_by = ""

        var sort_order = ""

        var sort = ""

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = params("sort_by")
          sort_order = params("sort_order")
          sort = s"order by  ${sort_by}  ${sort_order} "

        }

        /**
          * Filters
          */
        var whr = "";

        if (params.contains("name"))
          whr = whr + s" r.name like '%${params("name")}%' and "

        if (params.contains("status"))
          whr = whr + s" r.status = ${params("status").toBoolean} and "

        if (params.contains("is_global"))
          whr = whr + s" r.is_global = ${params("is_global").toBoolean} and "

        /**
          * Authorization logic and organization filter
          */

        if (params.contains("organization")) {

          var organizations: List[String] = List.empty

          if (auth_details.organizations != null && auth_details.organizations.size > 0) {

            organizations = auth_details.organizations.intersect(params("organization").split(",").toList)

            whr = whr + s" (ro.organization_id in (${organizations.mkString(",")}) or (r.is_global = true)) and "

          } else {

            whr = whr + s" (ro.organization_id in (${params("organization")}) or (r.is_global = true)) and "

          }
        } else {

          if (auth_details.organizations != null && auth_details.organizations.size > 0) {

            whr = whr + s" (ro.organization_id in (${auth_details.organizations.mkString(",")}) or (r.is_global = true)) and "

          }

        }

        if (params.contains("category")) {
          whr = whr + s" rc.category_id in (${params("category")}) and "
        }

        if (whr != "") {

          whr = whr.stripSuffix("and ").trim() + " and r.is_active = true"
          whr = " where " + whr.stripSuffix("and ").trim()

        } else {

          whr = " where r.is_active = true"

        }

        /**
          * fetch reports count
          */

        var innerJoinOrg = "";
        var innerJoinCat = "";

        innerJoinOrg = " left join reports_organizations as ro on (r.id = ro.report_id and ro.is_active = true) "
        innerJoinCat = " left join reports_categories as rc on (r.id = rc.report_id and rc.is_active = true) "

        val totalCount: Option[Int] = DB readOnly { implicit session =>

          val getCountSql = " select count(*) as total_count from ( select count(*) from reports as r   " + innerJoinOrg + " " + innerJoinCat + " " + whr + " group by r.id ) temp ";

          SQL(getCountSql).map(rs =>

            rs.int("total_count")

          ).single().apply()

        }
        /*
           Execute Query
       */
        val reports: List[GetReportsMapping] = DB readOnly { implicit session =>

          val sql = "select r.*, group_concat(distinct ro.organization_id) as filtered_org_ids, group_concat(distinct rc.category_id) as filtered_cat_ids, " +
            s" (select group_concat(distinct roi.organization_id) from reports_organizations roi where roi.report_id = r.id and roi.is_active = true) as org_ids, " +
            s" (select group_concat(distinct rci.category_id) from reports_categories rci where rci.report_id = r.id and rci.is_active = true) as cat_ids " +
            " from reports as r " +
            "" + innerJoinOrg + " " + innerJoinCat + whr + " group by r.id   " + " " + sort + " limit " + (page - 1) * size + ", " + size;

          SQL(sql).map(rs =>

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

        }


        val organization: List[Organization] = OrganizationService.getOrganizations(auth_details)

        val category: List[Category] = CategoryService.getCategories()

        /*
          Vairable for Final Response
       */

        var reportResponse: ListBuffer[ReportResponse] = new ListBuffer[ReportResponse];

        if (reports.size > 0) {

          /*
           Map Reports and their respective Organizations and Categories
        */

          reports.foreach { report =>

            val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

            val catList: ListBuffer[Category] = new ListBuffer[Category]

            var orgIds: ListBuffer[Long] = new ListBuffer[Long];

            var catIds: ListBuffer[Long] = new ListBuffer[Long];

            val report_id = report.id;

            if (report.org_ids != None && report.org_ids.get.split(",").distinct.size > 0) {

              report.org_ids.get.split(",").foreach { report_org_id =>

                organization.foreach { org =>

                  if (report_org_id.toLong == org.id) {

                    orgList += Organization(org.name, org.id)

                    orgIds += org.id

                  }

                }
              }
            }

            if (report.cat_ids != None && report.cat_ids.get.split(",").distinct.size > 0) {

              report.cat_ids.get.split(",").distinct.foreach { report_org_id =>

                category.foreach { cat =>

                  if (report_org_id.toLong == cat.id) {

                    catList += Category(cat.name, cat.id)

                    catIds += cat.id

                  }

                }
              }
            }

            val finalOrgList: List[Organization] = orgList.toList;

            val finalCatList: List[Category] = catList.toList;

            var addToResultByOrgFilter = true;

            var addToResultByCatFilter = true;

            if (addToResultByCatFilter && addToResultByOrgFilter)

              reportResponse += ReportResponse(

                report.id,
                report.name,
                report.query,
                report.status,
                report.cron_expression,
                report.is_global,
                report.is_active,
                report.created_on,
                report.updated_on,
                report.created_by,
                report.updated_by,
                finalOrgList,
                finalCatList

              )
          }
        }

        if (reportResponse.size > 0)
          sender() ! ReportsResponse(200, "Success", page, size, sort_by, sort_order, totalCount.get, reportResponse.toList)

        else
          sender() ! ReportsResponse(200, "Not Found", page, size, sort_by, sort_order, totalCount.get, reportResponse.toList)

      } catch {

        case e: Exception => {

          logger.error(s"Exception :: ${e.printStackTrace()}")

          sender() ! ReportsResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, List.empty)

        }

      }

    case createReport(auth_details, report) =>

      try {

        val count = DB localTx { implicit session =>

          /**
            * Insert into Reports
            */
          var ins_sql = s"""insert into reports(`name`, `query`,`is_global`, `cron_expression`,`status`, `created_by`, `updated_by`) VALUES ('${report.name}', "${report.query}", ${report.is_global} , '${report.cron_expression}', '${report.status}', ${auth_details.user_id}, ${auth_details.user_id})"""

          val report_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

          /**
            * Insert into Report Organization Mapping
            */

          if (!report.is_global) {

            var ins_str = "";

            report.organizations.foreach { org =>

              ins_str += s"(${report_id},${org.id},${auth_details.user_id}, ${auth_details.user_id}),"

            }

            ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

            var org_ins_sql = "insert into reports_organizations(`report_id`, `organization_id`, `created_by`, `updated_by`) VALUES " + ins_str

            val org_report_id = SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

          }

          /**
            * Insert into Categories
            */

          var cat_ins_str = "";

          report.categories.foreach { cat =>

            cat_ins_str += s"(${report_id},${cat.id},${auth_details.user_id},${auth_details.user_id}),"

          }

          cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

          var cat_ins_sql = "insert into reports_categories(`report_id`, `category_id`, `created_by`, `updated_by`) VALUES " + cat_ins_str

          val cat_report_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

        }

        sender() ! ActionPerformed(200, "Report Created Successfully")

      } catch {

        case e: Exception => sender() ! ActionPerformed(500, "Something went wrong, please try again")

      }

    case updateReport(auth_details, report, report_id) =>

      try {

        val count = DB localTx { implicit session =>

          var ins_sql = s"update reports set `name` = '${report.name}', `query` = '${report.query}',  `is_global`=${report.is_global}, `status`='${report.status}', `cron_expression` = '${report.cron_expression}', updated_by = ${auth_details.user_id} where id = ${report_id}"

          val updated_report_id = SQL(ins_sql).update().apply()

          /**
            * Update Organization
            */

          val present_org_list = SQL(s"select organization_id from reports_organizations where report_id =${report_id} and is_active = true ").map { rs => rs.int("organization_id") }.list().apply()

          if (!report.is_global) {

            val org_ins_list: ListBuffer[Long] = new ListBuffer[Long];

            val org_del_list: ListBuffer[Long] = new ListBuffer[Long];

            var request_org_list_temp: ListBuffer[Long] = new ListBuffer[Long];

            report.organizations.foreach { org =>

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

            var ins_str = "";

            /**
              * Insert into Organization
              */

            org_ins_list.foreach { org =>

              ins_str += s"(${report_id},${org}, ${auth_details.user_id}, ${auth_details.user_id}),"

            }

            ins_str = ins_str.stripSuffix(",").stripPrefix(",").trim

            var org_ins_sql = "";

            if (org_ins_list.size > 0) {

              org_ins_sql = "insert into reports_organizations(`report_id`, `organization_id`, `created_by`, `updated_by`) VALUES " + ins_str + s" ON DUPLICATE KEY update is_active = true, updated_by = ${auth_details.user_id}"

              val org_report_id = SQL(org_ins_sql).updateAndReturnGeneratedKey.apply()

            }

            /**
              * Update Organizations
              */

            var org_update_sql = "";

            if (org_del_list.size > 0) {

              org_update_sql = s"update reports_organizations set is_active = false, `updated_by` = ${auth_details.user_id} where report_id = ${report_id} and organization_id in (${org_del_list.toList.mkString(",")})"

              val org_report_update_id = SQL(org_update_sql).update().apply()

            }

          } else {

            if (present_org_list.size > 0) {

              /**
                * Update organization is is global true
                */

              val org_update_sql = s"update reports_organizations set is_active = false, `updated_by` = ${auth_details.user_id} where report_id =${report_id}"

              val org_report_update_id = SQL(org_update_sql).update().apply()

            }
          }

          /**
            * Category
            */

          val present_cat_list = SQL(s"select category_id from reports_categories where report_id = ${report_id} and is_active = true ").map { rs => rs.int("category_id") }.list().apply()

          val cat_ins_list: ListBuffer[Long] = new ListBuffer[Long];

          val cat_del_list: ListBuffer[Long] = new ListBuffer[Long];

          var request_cat_list_temp: ListBuffer[Long] = new ListBuffer[Long];

          report.categories.foreach { org =>

            request_cat_list_temp += org.id

            if (!present_cat_list.contains(org.id)) {

              cat_ins_list += org.id

            }
          }

          present_cat_list.foreach { org_id =>

            if (!request_cat_list_temp.contains(org_id)) {

              cat_del_list += org_id

            }

          }

          val request_cat_list = request_cat_list_temp.toList

          var cat_ins_str = "";

          /**
            * Insert into reports category
            */

          cat_ins_list.foreach { org =>

            cat_ins_str += s"(${report_id},${org}, ${auth_details.user_id}, ${auth_details.user_id}),"

          }

          cat_ins_str = cat_ins_str.stripSuffix(",").stripPrefix(",").trim

          var cat_ins_sql = "";

          if (cat_ins_list.size > 0) {

            cat_ins_sql = s"insert into reports_categories(`report_id`, `category_id`, `created_by`, `updated_by`) VALUES ${cat_ins_str} ON DUPLICATE KEY update is_active = true, `updated_by` = ${auth_details.user_id} "

            val cat_report_id = SQL(cat_ins_sql).updateAndReturnGeneratedKey.apply()

          }

          /**
            * Update Reports category
            */

          var cat_update_sql = "";

          if (cat_del_list.size > 0) {

            cat_update_sql = s"update reports_categories set is_active = false, `updated_by` = ${auth_details.user_id} where report_id = ${report_id} and category_id in (${cat_del_list.toList.mkString(",")})"

            val cat_report_update_id = SQL(cat_update_sql).update().apply()

          }

        }

        sender() ! ActionPerformed(200, "Report Updated Successfully")

      } catch {

        case e: Exception => sender() ! ActionPerformed(500, "Something went wrong, please try again")

      }

    case getReport(auth_details, id) =>

      try {

        var report_ids: ListBuffer[Long] = new ListBuffer[Long];

        var reportResponse: ListBuffer[ReportResponse] = new ListBuffer[ReportResponse];

        val reports: List[ReportMapping] = DB readOnly { implicit session =>

          val sql = s"select * from reports where id = ${id} and is_active = true";

          SQL(sql).map(rs =>

            ReportMapping(
              rs.int("id"),
              rs.string("name"),
              rs.string("query"),
              rs.string("status"),
              rs.string("cron_expression"),
              rs.boolean("is_global"),
              rs.boolean("is_active"),
              rs.string("created_on"),
              rs.stringOpt("updated_on"),
              rs.intOpt("created_by"),
              rs.intOpt("updated_by")
            )).list().apply()

        }

        reports.foreach { report =>

          report_ids += report.id

        }

        var org_ids: ListBuffer[Long] = new ListBuffer[Long]

        if (report_ids.size > 0) {

          val orgs: List[ReportOrganization] = DB readOnly { implicit session =>

            sql"select r.id as report_id, o.id, o.name from reports as r inner join reports_organizations as ro on r.id = ro.report_id inner join organizations o on o.id = ro.organization_id where r.id in (${report_ids}) and r.is_active=true and ro.is_active=true  ".map(rs =>

              ReportOrganization(rs.long("report_id"), rs.string("o.name"), rs.long("o.id"))).list().apply()
          }

          val cats: List[ReportCategory] = DB readOnly { implicit session =>

            sql"select r.id as report_id, c.id, c.name from reports as r inner join reports_categories as rc on r.id = rc.report_id inner join categories c on c.id = rc.category_id where r.id in (${report_ids})  and r.is_active=true and rc.is_active=true  ".map(rs =>

              ReportCategory(rs.string("c.name"), rs.long("c.id"), rs.long("report_id"))).list().apply()
          }

          reports.foreach { report =>

            val report_id = report.id;

            val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

            val catList: ListBuffer[Category] = new ListBuffer[Category]


            orgs.foreach { org =>

              if (report_id == org.report_id) {

                /**
                  * Apply filter for authorizations
                  */

                if (auth_details.organizations != null) {

                  if (auth_details.organizations.map(_.toLong).contains(org.id)) {

                    orgList += Organization(org.name, org.id)

                  }

                }
                else {

                  orgList += Organization(org.name, org.id)

                }

              }

            }

            cats.foreach { cat =>

              if (report_id == cat.report_id) catList += Category(cat.name, cat.id)

            }

            val finalOrgList: List[Organization] = orgList.toList;

            val finalCatList: List[Category] = catList.toList;

            reportResponse += ReportResponse(

              report.id,
              report.name,
              report.query,
              report.status,
              report.cron_expression,
              report.is_global,
              report.is_active,
              report.created_on,
              report.updated_on,
              report.created_by,
              report.updated_by,
              finalOrgList,
              finalCatList

            )

          }

        }

        if (reportResponse.size > 0 && reportResponse(0).is_global) {

          if (reportResponse.size > 0) {

            sender() ! GetReportResponse(200, "Success", reportResponse.toList)

          } else {

            sender() ! GetReportResponse(404, "Rule Not Found", reportResponse.toList)

          }

        } else if ((auth_details.organizations != null && auth_details.organizations.map(_.toLong).intersect(org_ids.toList).size < 1)) {

          sender() ! GetReportResponse(401, "", List.empty)

        } else {

          if (reportResponse.size > 0) {

            sender() ! GetReportResponse(200, "Success", reportResponse.toList)

          } else {

            sender() ! GetReportResponse(404, "Rule Not Found", reportResponse.toList)

          }

        }

      } catch {

        case e: Exception => sender() ! GetReportResponse(500, "Something went wrong, please try again", List.empty)
      }

    case deleteReport(auth_details, report_id) =>

      try {


        val count = DB localTx { implicit session =>

          var report_sql = s"update reports set status = 'disabled', `is_active` = false, `updated_by` = ${auth_details.user_id}  where id = ${report_id}"

          val updated_report = SQL(report_sql).update().apply()

          var report_cat_sql = s"update reports_categories set `is_active` = false, `updated_by` = ${auth_details.user_id}  where report_id = ${report_id}"

          val updated_report_cat = SQL(report_cat_sql).update().apply()

          var report_org_sql = s"update reports_organizations set `is_active` = false, `updated_by` = ${auth_details.user_id}  where report_id = ${report_id}"

          val updated_report_org = SQL(report_org_sql).update().apply()

          sender() ! ActionPerformed(200, "Report Deleted Successfully")

        }


      } catch {

        case e: Exception => sender() ! ActionPerformed(500, "Something went wrong, please try again")

      }
  }
}
