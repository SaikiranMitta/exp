package com.iac.soc.backend.api.common.mapping

import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization

object RuleMapping {

  final case class RuleCategory(name: String, id: Long, rule_id: Long);

  final case class RuleCategories(category: List[RuleCategory]);

  final case class RuleOrganization(rule_id: Long, name: String, id: Long);

  final case class RuleOrganizations(organization: List[RuleOrganization]);

  final case class RuleResponse(id: Long, name: String, query: String, description: Option[String], severity: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int], organizations: List[Organization], categories: List[Category], incidents_count: Int);

  final case class RuleMapping(id: Long, name: String, query: String, description: Option[String], severity: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int]);

  final case class RuleInsMapping(name: String, query: String, description: Option[String], severity: String, status: String, cron_expression: String, is_global: Boolean, organizations: List[Organization], categories: List[Category]);

  final case class RuleUpdateMapping(name: String, query: String, description: Option[String], severity: String, status: String, cron_expression: String, is_global: Boolean, organizations: List[Organization], categories: List[Category]);

  final case class Rules(rules: List[RuleMapping])

  final case class RulesResponse(status_code: Int, message: String, page: Int, size: Int, sort_by: String, sort_order: String, total_records: Int, rules: List[RuleResponse])

  final case class GetRuleResponse(status_code: Int, message: String, rules: List[RuleResponse])

  final case class GetRulesMapping(id: Long, name: String, query: String, description: Option[String], severity: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int], org_ids: Option[String], cat_ids: Option[String], incidents_count: Int);

}