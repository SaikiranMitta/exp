package com.iac.soc.backend.api.incident

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.{Actor, Props}
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.IncidentMapping._
import com.iac.soc.backend.api.common.mapping.IncidentsAttachmentsMapping.IncidentsAttachments
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.{CategoryService, OrganizationService}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer

object Manager {

  final case class getIncidentSummary(auth_details: Claims, params: Map[String, String]);

  final case class getIncidentsByRule(auth_details: Claims, rule_id: Long, params: Map[String, String])

  final case class getIncidentsSummaryByRule(auth_details: Claims, rule_id: Long, params: Map[String, String])

  final case class getIncidentsById(auth_details: Claims, incident_id: Long)

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  //getConnection();

  private val dateFmt = "yyyy-MM-dd HH:mm:ss"

  def receive: Receive = {

    case getIncidentSummary(auth_details, params) => {

      logger.info(s"Received Request to fetch incident summary by user ${auth_details.user_id}")

      try {

        var page = 1

        var size = 5

        var time_range_from = ""

        var time_range_to = ""

        var sort_by = ""

        var sort_order = ""

        var sort = ""

        var setLimit = true;

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        if (!(params.contains("time_range_from") && params.contains(("time_range_to")))) {

          val currDate = new Date

          val sdf = new SimpleDateFormat(dateFmt)
          time_range_to = sdf.format(currDate)

          val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
          time_range_from = sdf.format(minus24HoursDate)

        }

        if ((params.contains("time_range_from") && params.contains("time_range_to")) && !(params.contains("page") && params.contains("size"))) {

          setLimit = false

        }

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = params("sort_by")

          sort_order = params("sort_order")

          sort = s"order by ${sort_by}  ${sort_order} "

        }

        logger.info(s"Fetch Incident Summary Count for params ${params}")

        val totalCount: Int = Repository.getIncidentSummaryCount(auth_details, params);

        logger.info(s"Fetch Incident Summary for params ${params}")

        val incidentResponse: List[IncidentRuleSummaryMapping] = Repository.getIncidentSummary(auth_details, params, setLimit);

        logger.info("Fetch categories")

        val category: List[Category] = CategoryService.getCategories()

        var ruleIncidentResponse: ListBuffer[IncidentRuleSummaryReponseMapping] = new ListBuffer[IncidentRuleSummaryReponseMapping];

        logger.info("Map Categories with rules")

        incidentResponse.map { incident =>

          val catList: ListBuffer[Category] = new ListBuffer[Category]

          if (incident.category_ids != None && incident.category_ids.get.split(",").distinct.size > 0) {

            incident.category_ids.get.split(",").distinct.foreach { incident_cat_id =>

              category.foreach { cat =>

                if (incident_cat_id.toLong == cat.id) {

                  catList += Category(cat.name, cat.id)

                }

              }

            }

            ruleIncidentResponse += IncidentRuleSummaryReponseMapping(

              incident.id,
              incident.name,
              incident.severity,
              incident.created_on,
              incident.incidents_count,
              catList.toList

            )

          }

        }

        if (ruleIncidentResponse.size > 0) {

          sender() ! IncidentSummaryResponse(200, "Success", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, ruleIncidentResponse.toList)

        }
        else {

          sender() ! IncidentSummaryResponse(200, "Not Found", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, ruleIncidentResponse.toList)

        }

      } catch {

        case e: Exception => sender() ! IncidentSummaryResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, "", "", List.empty)

      }

    }

    case getIncidentsByRule(auth_details, rule_id, params) => {

      try {

        logger.info(s"Received request to fetch incidents by rule ${rule_id} by user  ${auth_details.user_id}")

        var page = 1

        var size = 5

        var time_range_from = ""

        var time_range_to = ""

        var sort_by = ""

        var sort_order = ""

        var sort = ""

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt

          size = params("size").toInt

        }

        if (!(params.contains("time_range_from") && params.contains(("time_range_to")))) {

          val currDate = new Date

          val sdf = new SimpleDateFormat(dateFmt)

          time_range_to = sdf.format(currDate)

          val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))

          time_range_from = sdf.format(minus24HoursDate)

        }

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = params("sort_by")

          sort_order = params("sort_order")

        }

        logger.info("Fetch total count for incidents by rule")

        val totalCount: Long = Repository.getIncidentByRuleCount(auth_details, params, rule_id);

        logger.info(s"Total incidents counts for rule ${rule_id} with params ${params} is ${totalCount}")
        logger.info("Fetch incidents by rule")

        val incidentResponse: List[IncidentRuleMapping] = Repository.getIncidentByRule(auth_details, params, rule_id);

        logger.info("Fetch all the organizations")

        val organization: List[Organization] = OrganizationService.getOrganizations(auth_details)

        var ruleIncidentResponse: ListBuffer[IncidentRuleReponseMapping] = new ListBuffer[IncidentRuleReponseMapping];

        var incidentsAttachments: ListBuffer[IncidentsAttachments] = new ListBuffer[IncidentsAttachments]

        val allIncidentAttachmentIds = incidentResponse.map(_.attachmentIds)

        logger.info("Map organizations to the incidents")

        incidentResponse.map { incident =>

          val incidentOrganization: ListBuffer[Organization] = new ListBuffer[Organization]

          organization.foreach { org =>

            if (incident.organizationId == org.id) {

              incidentOrganization += Organization(org.name, org.id)

            }

          }

          ruleIncidentResponse += IncidentRuleReponseMapping(

            incident.id,
            incident.rule_id,
            incident.query,
            incident.created_on,
            incidentOrganization(0),
            incidentsAttachments.toList

          )

        }

        if (ruleIncidentResponse.size > 0)

          sender() ! IncidentResponse(200, "Success", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, ruleIncidentResponse.toList)

        else

          sender() ! IncidentResponse(200, "Not Found", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, ruleIncidentResponse.toList)

      } catch {

        case e: Exception => sender() ! IncidentResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, "", "", List.empty)

      }

    }

    case getIncidentsSummaryByRule(auth_details, rule_id, params) => {

      try {

        logger.info(s"Received request to fetch incidents summary by rule by user ${auth_details.user_id}")

        var page = 1

        var size = 5

        var time_range_from = ""

        var time_range_to = ""

        var sort_by = ""

        var sort_order = ""

        var sort = ""

        if (params.contains("page") && params.contains("size")) {

          page = params("page").toInt
          size = params("size").toInt

        }

        if (!(params.contains("time_range_from") && params.contains(("time_range_to")))) {

          val currDate = new Date

          val sdf = new SimpleDateFormat(dateFmt)
          time_range_to = sdf.format(currDate)

          val minus24HoursDate = new Date(System.currentTimeMillis() - (3600 * 1000 * 24))
          time_range_from = sdf.format(minus24HoursDate)

        }

        /**
          * Sorting
          */
        if (params.contains("sort_by") && params.contains("sort_order")) {

          sort_by = params("sort_by")

          sort_order = params("sort_order")

        }

        logger.info("Fetch total count for incident summary by rule")

        val totalCount: Long = Repository.getIncidentsSummaryByRuleCount(auth_details, params, rule_id);

        logger.info("Fetch incident summary by rule")

        val incidentResponse: List[IncidentRuleBucketReponseMapping] = Repository.getIncidentsSummaryByRule(auth_details, params, rule_id);

        if (incidentResponse.size > 0)

          sender() ! IncidentBucketResponse(200, "Success", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, incidentResponse)

        else

          sender() ! IncidentBucketResponse(200, "Not Found", page, size, sort_by, sort_order, totalCount, time_range_from, time_range_to, incidentResponse)

      } catch {

        case e: Exception => sender() ! IncidentBucketResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, "", "", List.empty)

      }

    }

    case getIncidentsById(auth_details, incident_id) => {

      try {

        logger.info(s"Received request to fetch incident by id by user ${auth_details.user_id}")

        val incidentResponse: List[IncidentById] = Repository.getIncidentsById(auth_details, incident_id);

        if (incidentResponse.size > 0) {

          if (auth_details.organizations != null && auth_details.organizations.map(_.toLong).contains(incidentResponse(0).orgnaization_id)) {

            sender() ! IncidentByIdResponse(200, "Success", incidentResponse)

          } else if (auth_details.organizations != null && !auth_details.organizations.contains(incidentResponse(0).orgnaization_id)) {

            sender() ! IncidentByIdResponse(401, "", List.empty)

          } else if (auth_details.organizations == null) {

            sender() ! IncidentByIdResponse(200, "Success", incidentResponse)

          } else {

            sender() ! IncidentByIdResponse(404, "Not Found", List.empty)

          }

        } else {

          sender() ! IncidentByIdResponse(404, "Not Found", incidentResponse)

        }

      } catch {

        case e: Exception => sender() ! IncidentByIdResponse(500, "Something went wrong, please try again", List.empty)

      }

    }

  }

}
