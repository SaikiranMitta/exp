package com.iac.soc.backend.api.rule

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import com.iac.soc.backend.api.common.Datasource.getConnection
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization
import com.iac.soc.backend.api.common.mapping.RuleMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.{CategoryService, OrganizationService}
import com.iac.soc.backend.rules.messages.{RuleCreated, RuleDeleted, RuleUpdated, Severity, Category => CategoryMessage, Organization => OrganizationMessage}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.DB

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

object Manager {

  final case class getRules(auth_details: Claims, params: Map[String, String]);

  final case class createRule(auth_details: Claims, rule: RuleInsMapping, ruleSubSystem: ActorRef)

  final case class updateRule(auth_details: Claims, rule: RuleUpdateMapping, rule_id: Int, ruleSubSystem: ActorRef)

  final case class getRule(auth_details: Claims, id: Int)

  final case class deleteRule(auth_details: Claims, id: Int, ruleSubSystem: ActorRef)

  def props: Props = Props[Manager]

}

class Manager extends Actor with LazyLogging {

  import Manager._

  implicit val timeout: Timeout = Timeout(600 seconds)

  def prepareRuleResult(auth_details: Claims, rules: List[GetRulesMapping], organization: List[Organization], category: List[Category]): ListBuffer[RuleResponse] = {

    /**
      * Map Rules and their respective Organizations and Categories
      */

    var ruleResponse: ListBuffer[RuleResponse] = new ListBuffer[RuleResponse];

    logger.info(s"Rule List count :: ${rules.size}")

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

      if (rule.cat_ids != None && rule.cat_ids.get.split(",").distinct.size > 0) {

        rule.cat_ids.get.split(",").distinct.foreach { rule_cat_id =>

          category.foreach { cat =>

            if (rule_cat_id.toLong == cat.id) {

              catList += Category(cat.name, cat.id)

              catIds += cat.id

            }

          }

        }

      }

      val finalOrgList: List[Organization] = orgList.toList;

      val finalCatList: List[Category] = catList.toList;

      ruleResponse += RuleResponse(

        rule.id,
        rule.name,
        rule.query,
        rule.description,
        rule.severity,
        rule.status,
        rule.cron_expression,
        rule.is_global,
        rule.is_active,
        rule.created_on,
        rule.updated_on,
        rule.created_by,
        rule.updated_by,
        finalOrgList,
        finalCatList,
        rule.incidents_count,

      )

    }

    ruleResponse

  }


  def receive: Receive = {

    case getRules(auth_details, params) =>

      logger.info(s"Received request for get Rules by user ${auth_details.user_id}")

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

        var rule_ids: ListBuffer[Long] = new ListBuffer[Long];

        /**
          * Get Rule Search Count
          */

        logger.info(s"Fetch rules count with params :: ${params}")

        val totalCount: Int = Repository.getRuleSearchCount(auth_details, params)

        logger.info(s"Fetch rules with params :: ${params}")

        val rules: List[GetRulesMapping] = Repository.getRules(auth_details, params)

        logger.info("Fetch all organizations")

        val organization: List[Organization] = OrganizationService.getOrganizations(auth_details)

        logger.info("Fetch all categories")

        val category: List[Category] = CategoryService.getCategories()

        /**
          * Final Response
          */

        var ruleResponse: ListBuffer[RuleResponse] = new ListBuffer[RuleResponse];

        if (rules.size > 0) {

          ruleResponse = prepareRuleResult(auth_details, rules, organization, category)

        }

        if (ruleResponse.size > 0) {

          logger.info("Sending response with status code 200")

          sender() ! RulesResponse(200, "Success", page, size, sort_by, sort_order, totalCount, ruleResponse.toList)

        }
        else {

          logger.info("Sending response with status code 200, no records found")

          sender() ! RulesResponse(200, "No records found", page, size, sort_by, sort_order, totalCount, ruleResponse.toList)

        }

      } catch {

        case e: Exception => {

          logger.error(s"Failed to fetch rules :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! RulesResponse(500, "Something went wrong, please try again", 0, 0, "", "", 0, List.empty)

        }

      }

    case createRule(auth_details, rule, ruleSubSystem) =>

      logger.info(s"Received request to create rule by user ${auth_details.user_id}")

      var rule_id = 0L

      try {

        logger.info("Validate input data")

        logger.info(s"Transaction initiated to create rule with params ${rule}")

        val count = DB localTx { implicit session =>

          /**
            * Insert into Rules
            */

          logger.info("Insert Rule")

          rule_id = Repository.insert(auth_details, rule)


          /**
            * Skip Organization insertion if its a global rule
            */

          logger.info("Check if rule is global or organization specific")

          if (!rule.is_global) {

            /**
              * Insert into Rule Organization Mapping
              */

            if (rule.organizations.size > 0) {

              logger.info("Insert into Rules Organizations ")

              val rule_org_id = Repository.insertRulesOrganizations(auth_details, rule, rule_id)

            }

          }

          /**
            * Insert into Rule Category Mapping
            */
          if (rule.categories.size > 0) {

            logger.info("Insert into Rules Categories")

            val cat_rule_id = Repository.insertRulesCategories(auth_details, rule, rule_id)

          }

        }

        logger.info("Sending response with status code 201, Rule Created")

        sender() ! ActionPerformed(201, "Rule Created Successfully")

        logger.info("Sending rule creation message to Rule Sub System")

        ruleSubSystem ! RuleCreated(rule_id.toInt, rule.name, rule.is_global, rule.organizations.map(org => OrganizationMessage(id = org.id.toInt, name = org.name)), rule.query, rule.status, rule.categories.map(cat => CategoryMessage(id = cat.id.toInt, name = cat.name)), Option(Severity(1, rule.severity)), rule.cron_expression)

      } catch {

        case e: Exception => {

          logger.error(s"Failed to create rule :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case updateRule(auth_details, rule, rule_id, ruleSubSystem) =>

      logger.info(s"Received request to update rule by user ${auth_details.user_id}")

      try {

        logger.info(s"Transaction initiated to update rule with params ${rule} and rule id ${rule_id}")

        val count = DB localTx { implicit session =>

          Repository.updateRule(auth_details, rule, rule_id)

          Repository.updateRuleCatgeories(auth_details, rule, rule_id)

          Repository.updateRuleOrganizations(auth_details, rule, rule_id)

        }

        logger.info("Sending response with status code 200, Rule Updated")

        sender() ! ActionPerformed(200, "Rule Updated Successfully")

        ruleSubSystem ! RuleUpdated(rule_id.toInt, rule.name, rule.is_global, rule.organizations.map(org => OrganizationMessage(id = org.id.toInt, name = org.name)), rule.query, rule.status, rule.categories.map(cat => CategoryMessage(id = cat.id.toInt, name = cat.name)), Option(Severity(1, rule.severity)), rule.cron_expression)

      }
      catch {

        case e: Exception => {

          logger.error(s"Failed to update rule :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

    case getRule(auth_details, id) =>

      logger.info(s"Recevied request to fetch rule with params ${id} by user ${auth_details.user_id}")

      try {

        var rule_ids: ListBuffer[Long] = new ListBuffer[Long];

        var ruleResponse: ListBuffer[RuleResponse] = new ListBuffer[RuleResponse];

        val params = Map("id" -> id.toString)

        val rules: List[GetRulesMapping] = Repository.getRules(auth_details, params);

        rules.foreach { rule =>

          rule_ids += rule.id

        }

        var org_ids: List[Long] = List.empty[Long]

        if (rule_ids.size > 0) {

          logger.info("Fetch rule organizations")

          val orgs: List[Organization] = OrganizationService.getOrganizations(auth_details)

          logger.info("Fetch rule Categories")

          val cats: List[Category] = CategoryService.getCategories()

          if (orgs.size > 0) {

            org_ids = orgs.map(_.id);

          }

          ruleResponse = prepareRuleResult(auth_details, rules, orgs, cats)

        }

        if (ruleResponse.size > 0 && ruleResponse(0).is_global) {

          if (ruleResponse.size > 0) {

            logger.info("Sending response with status code 200, Rule Found")

            sender() ! GetRuleResponse(200, "Success", ruleResponse.toList)

          } else {

            logger.info("Sending response with status code 404, Rule Not Found")

            sender() ! GetRuleResponse(404, "Rule Not Found", ruleResponse.toList)

          }

        }
        else if ((auth_details.organizations != null && auth_details.organizations.map(_.toLong).intersect(org_ids).size < 1)) {

          logger.info(s"Access prohibited for the rule to user :: ${auth_details.user_id}")

          sender() ! GetRuleResponse(401, "", List.empty)

        } else {

          if (ruleResponse.size > 0) {

            logger.info("Sending response with status code 200, Rule Found")

            sender() ! GetRuleResponse(200, "Success", ruleResponse.toList)

          } else {

            logger.info("Sending response with status code 404, Rule Not Found")

            sender() ! GetRuleResponse(404, "Rule Not Found", ruleResponse.toList)

          }

        }

      }

      catch {

        case e: Exception => {

          logger.error(s"Failed to get rule :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! GetRuleResponse(500, "Something went wrong, please try again", List.empty)

        }

      }

    case deleteRule(auth_details, rule_id, ruleSubSystem) =>

      logger.info(s"Recevied request to delete rule ${rule_id} by user ${auth_details.user_id}")

      try {

        logger.info(s"Transaction initiated to delete rule ${rule_id} ")

        val count = DB localTx { implicit session =>

          Repository.deleteRule(auth_details, rule_id)

          Repository.deleteRuleCategories(auth_details, rule_id)

          Repository.deleteRuleOrganizations(auth_details, rule_id)

          sender() ! ActionPerformed(200, "Rule Deleted Successfully")

          ruleSubSystem ! RuleDeleted(rule_id.toInt)

        }

      } catch {

        case e: Exception => {

          logger.error(s"Failed to delete rule :: ${e}")

          logger.info("Sending response with status code 500")

          sender() ! ActionPerformed(500, "Something went wrong, please try again")

        }

      }

  }

}
