package com.iac.soc.backend.api.report

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization
import com.iac.soc.backend.api.common.mapping.ReportMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.{CategoryService, OrganizationService}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.DB

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object Manager {

  final case class getReports(auth_details: Claims, params: Map[String, String]);

  final case class createReport(auth_details: Claims, report: ReportInsMapping)

  final case class updateReport(auth_details: Claims, report: ReportUpdateMapping, report_id: Int)

  final case class getReport(auth_details: Claims, id: Int)

  final case class deleteReport(auth_details: Claims, id: Int)

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  implicit val timeout: Timeout = Timeout(600 seconds)


  def prepareReportResult(auth_details: Claims, reports: List[GetReportsMapping], organization: List[Organization], category: List[Category]): ListBuffer[ReportResponse] = {

    /**
      * Map Reports and their respective Organizations and Categories
      */

    var reportResponse: ListBuffer[ReportResponse] = new ListBuffer[ReportResponse];

    logger.info(s"Report List count :: ${reports.size}")

    logger.info("Map Reports to respective organizations and categories")

    reports.foreach { report =>

      val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

      val catList: ListBuffer[Category] = new ListBuffer[Category]

      var orgIds: ListBuffer[Long] = new ListBuffer[Long];

      var catIds: ListBuffer[Long] = new ListBuffer[Long];

      if (report.org_ids != None && report.org_ids.get.split(",").distinct.size > 0) {

        report.org_ids.get.split(",").foreach { report_org_id =>

          organization.foreach { org =>

            if (report_org_id.toLong == org.id) {

              /**
                * Apply filter for authorizations
                */


              if (auth_details.organizations != null) {

                if (auth_details.organizations.map(_.toLong).contains(org.id)) {

                  orgList += Organization(org.name, org.id)

                  orgIds += org.id

                }

              }
              else {

                orgList += Organization(org.name, org.id)

                orgIds += org.id

              }

            }

          }

        }

      }

      if (report.cat_ids != None && report.cat_ids.get.split(",").distinct.size > 0) {

        report.cat_ids.get.split(",").distinct.foreach { report_cat_id =>

          category.foreach { cat =>

            if (report_cat_id.toLong == cat.id) {

              catList += Category(cat.name, cat.id)

              catIds += cat.id

            }

          }

        }

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

    reportResponse

  }


  def receive: Receive = {

    case getReports(auth_details, params) =>

      logger.info(s"Received request for get Reports by user ${auth_details.user_id}")

      try {

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

        logger.info(s"Request recevied with params :: ${params}")

        var report_ids: ListBuffer[Long] = new ListBuffer[Long];

        /**
          * Get Report Search Count
          */

        logger.info(s"Fetch reports count with params :: ${params}")

        val totalCount: Int = Repository.getReportSearchCount(auth_details, params)

        logger.info(s"Fetch reports with params :: ${params}")

        val reports: List[GetReportsMapping] = Repository.getReports(auth_details, params)

        logger.info("Fetch all organizations")

        val organization: List[Organization] = OrganizationService.getOrganizations(auth_details)

        logger.info("Fetch all categories")

        val category: List[Category] = CategoryService.getCategories()

        /**
          * Final Response
          */

        var reportResponse: ListBuffer[ReportResponse] = new ListBuffer[ReportResponse];

        if (reports.size > 0) {

          reportResponse = prepareReportResult(auth_details, reports, organization, category)

        }

        if (reportResponse.size > 0) {

          logger.info("Sending response with status code 200")

          sender() ! ReportsResponse(200, "Success", page, size, sort_by, sort_order, totalCount, reportResponse.toList)

        }
        else {

          logger.info("Sending response with status code 200, no records found")

          sender() ! ReportsResponse(200, "No records found", page, size, sort_by, sort_order, totalCount, reportResponse.toList)

        }

      } catch {

        case e: Exception => {

          logger.error(s"Failed to fetch reports :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ReportsResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, List.empty)

        }

      }

    case createReport(auth_details, report) =>

      logger.info(s"Received request to create report by user ${auth_details.user_id}")

      var report_id = 0L

      try {

        logger.info(s"Transaction initiated to create report with params ${report}")

        val count = DB localTx { implicit session =>

          /**
            * Insert into Reports
            */

          logger.info("Insert Report")

          report_id = Repository.insert(auth_details, report)


          /**
            * Skip Organization insertion if its a global report
            */

          logger.info("Check if report is global or organization specific")

          if (!report.is_global) {

            /**
              * Insert into Report Organization Mapping
              */

            if (report.organizations.size > 0) {

              logger.info("Insert into Reports Organizations ")

              val report_org_id = Repository.insertReportsOrganizations(auth_details, report, report_id)

            }

          }

          /**
            * Insert into Report Category Mapping
            */
          if (report.categories.size > 0) {

            logger.info("Insert into Reports Categories")

            val cat_report_id = Repository.insertReportsCategories(auth_details, report, report_id)

          }

        }

        logger.info("Sending response with status code 201, Report Created")

        sender() ! ActionPerformed(201, "Report Created Successfully")

      } catch {

        case e: Exception => {

          logger.error(s"Failed to create report :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case updateReport(auth_details, report, report_id) =>

      logger.info(s"Received request to update report by user ${auth_details.user_id}")

      try {

        logger.info(s"Transaction initiated to update report with params ${report} and report id ${report_id}")

        val count = DB localTx { implicit session =>

          Repository.updateReport(auth_details, report, report_id)

          Repository.updateReportCatgeories(auth_details, report, report_id)

          Repository.updateReportOrganizations(auth_details, report, report_id)

        }

        logger.info("Sending response with status code 200, Report Updated")

        sender() ! ActionPerformed(200, "Report Updated Successfully")

      }
      catch {

        case e: Exception => {

          logger.error(s"Failed to update report :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case getReport(auth_details, id) =>

      logger.info(s"Recevied request to fetch report with params ${id} by user ${auth_details.user_id}")

      try {

        var report_ids: ListBuffer[Long] = new ListBuffer[Long];

        var reportResponse: ListBuffer[ReportResponse] = new ListBuffer[ReportResponse];

        val params = Map("id" -> id.toString)

        val reports: List[GetReportsMapping] = Repository.getReports(auth_details, params);

        reports.foreach { report =>

          report_ids += report.id

        }

        var org_ids: List[Long] = List.empty[Long]

        if (report_ids.size > 0) {

          logger.info("Fetch report organizations")

          val orgs: List[Organization] = OrganizationService.getOrganizations(auth_details)

          logger.info("Fetch report Categories")

          val cats: List[Category] = CategoryService.getCategories()

          if (orgs.size > 0) {

            org_ids = orgs.map(_.id);

          }

          reportResponse = prepareReportResult(auth_details, reports, orgs, cats)

        }

        if (reportResponse.size > 0 && reportResponse(0).is_global) {

          if (reportResponse.size > 0) {

            logger.info("Sending response with status code 200, Report Found")

            sender() ! GetReportResponse(200, "Success", reportResponse.toList)

          } else {

            logger.info("Sending response with status code 404, Report Not Found")

            sender() ! GetReportResponse(404, "Report Not Found", reportResponse.toList)

          }

        }
        else if ((auth_details.organizations != null && auth_details.organizations.map(_.toLong).intersect(org_ids).size < 1)) {

          logger.info(s"Access prohibited for the report to user :: ${auth_details.user_id}")

          sender() ! GetReportResponse(401, "", List.empty)

        } else {

          if (reportResponse.size > 0) {

            logger.info("Sending response with status code 200, Report Found")

            sender() ! GetReportResponse(200, "Success", reportResponse.toList)

          } else {

            logger.info("Sending response with status code 404, Report Not Found")

            sender() ! GetReportResponse(404, "Report Not Found", reportResponse.toList)

          }

        }

      }

      catch {

        case e: Exception => {

          logger.error(s"Failed to get report :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! GetReportResponse(500, "Something went wrong, please try again", List.empty)

        }

      }

    case deleteReport(auth_details, report_id) =>

      logger.info(s"Recevied request to delete report ${report_id} by user ${auth_details.user_id}")

      try {

        logger.info(s"Transaction initiated to delete report ${report_id} ")

        val count = DB localTx { implicit session =>

          Repository.deleteReport(auth_details, report_id)

          Repository.deleteReportCategories(auth_details, report_id)

          Repository.deleteReportOrganizations(auth_details, report_id)

          sender() ! ActionPerformed(200, "Report Deleted Successfully")

        }

      } catch {

        case e: Exception => {

          logger.error(s"Failed to delete report :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

  }

}
