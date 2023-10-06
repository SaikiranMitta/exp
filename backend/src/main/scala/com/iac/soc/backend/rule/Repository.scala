package com.iac.soc.backend.rule

import com.iac.soc.backend.api.common.mapping.RuleMapping.GetRulesMapping
import com.iac.soc.backend.api.common.{CategoryService, OrganizationService}
import com.iac.soc.backend.api.common.mapping.CategoryMapping.{Category => CategoryMapping}
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.{Organization => OrganizationMapping}
import com.iac.soc.backend.rule.models._
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, SQL}

import scala.collection.mutable.ListBuffer

/**
  * The rule sub-system repository
  */
private[rule] object Repository extends LazyLogging {

  implicit val session = AutoSession

  /**
    * Gets the rules within the system
    *
    * @return the rules within the system
    */


  def getRules: Vector[Rule] = {

    logger.info("Fetching rules within the system")

    val sql = s" select r.*, " +
      s" (select group_concat(distinct roi.organization_id) from rules_organizations roi where roi.rule_id=r.id and roi.is_active = true) as org_ids," +
      s" (select count(id) from incidents where rule_id = r.id) as incidents_count, " +
      s" (select group_concat(distinct rci.category_id) from rules_categories rci where rci.rule_id=r.id and rci.is_active = true) as cat_ids " +
      s" from rules r where r.is_active = true and r.status = 'enabled'"

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

    logger.info("Fetch all organizations")

    val organization: List[OrganizationMapping] = OrganizationService.getOrganizations()

    logger.info("Fetch all categories")

    val category: List[CategoryMapping] = CategoryService.getCategories()

    var ruleResponse: ListBuffer[Rule] = new ListBuffer[Rule];

    if (rules.size > 0) {

      logger.info("Map Rules to respective organizations and categories")

      rules.foreach { rule =>

        val orgList: ListBuffer[Organization] = new ListBuffer[Organization]

        val catList: ListBuffer[Category] = new ListBuffer[Category]

        var orgIds: ListBuffer[Long] = new ListBuffer[Long];

        var catIds: ListBuffer[Long] = new ListBuffer[Long];

        if (rule.org_ids != None && rule.org_ids.get.split(",").distinct.size > 0) {

          rule.org_ids.get.split(",").foreach { rule_org_id =>

            organization.foreach { org =>

              if (rule_org_id.toLong == org.id) {

                orgList += Organization(org.id.toInt, org.name)

                orgIds += org.id

              }

            }

          }

        }

        if (rule.cat_ids != None && rule.cat_ids.get.split(",").distinct.size > 0) {

          rule.cat_ids.get.split(",").distinct.foreach { rule_cat_id =>

            category.foreach { cat =>

              if (rule_cat_id.toLong == cat.id) {

                catList += Category(cat.id.toInt, cat.name)

                catIds += cat.id

              }

            }

          }

        }

        val finalOrgList: List[Organization] = orgList.toList;

        val finalCatList: List[Category] = catList.toList;

        ruleResponse += Rule(

          rule.id.toInt,
          rule.name,
          rule.is_global,
          finalOrgList.toVector,
          rule.query,
          rule.status,
          finalCatList.toVector,
          Severity(1, rule.severity),
          rule.cron_expression,

        )

      }

    }

    // Return all the rules
    ruleResponse.toVector

  }

  /**
    * Creates an incident
    *
    * @param incident the incident to be created
    */
  def createIncident(incident: Incident): Long = {
    var incident_id: Long = 0

    try {

      logger.info(s"Creating incident for rule with id: ${incident.ruleId}")

      val sql = s"""Insert into incidents(rule_id, query, organization_id, total_hits) values(${incident.ruleId}, "${incident.query}", ${incident.organization.id}, 0)"""

      incident_id = SQL(sql).updateAndReturnGeneratedKey.apply()

      logger.info(s"Incident created with id ${incident_id}")


    } catch {

      case e: Exception => {

        logger.error("Exception in incident creation :: ", e.printStackTrace())


      }

    }
    incident_id

  }

}
