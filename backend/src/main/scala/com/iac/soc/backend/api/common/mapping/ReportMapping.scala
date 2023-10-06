
package com.iac.soc.backend.api.common.mapping

import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization

object ReportMapping {


  final case class ReportCategory(name: String, id: Long, report_id: Long);

  final case class ReportCategories(category: List[ReportCategory]);

  final case class ReportOrganization(report_id: Long, name: String, id: Long);

  final case class ReportOrganizations(organization: List[ReportOrganization]);

  final case class ReportResponse(id: Long, name: String, query: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int], organizations: List[Organization], categories: List[Category]);

  final case class ReportMapping(id: Long, name: String, query: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int]);

  final case class ReportInsMapping(name: String, query: String, status: String, cron_expression: String, is_global: Boolean, organizations: List[Organization], categories: List[Category]);

  final case class ReportUpdateMapping(name: String, query: String, status: String, cron_expression: String, is_global: Boolean, organizations: List[Organization], categories: List[Category]);

  final case class Reports(reports: List[ReportMapping])

  final case class ReportsResponse(status_code: Int, message: String, page: Int, size: Int, sort_by: String, sort_order: String, total_records: Int, reports: List[ReportResponse])

  final case class GetReportResponse(status_code: Int, message: String, reports: List[ReportResponse])

  final case class GetReportsMapping(id: Long, name: String, query: String, status: String, cron_expression: String, is_global: Boolean, is_active: Boolean, created_on: String, updated_on: Option[String], created_by: Option[Int], updated_by: Option[Int], org_ids: Option[String], cat_ids: Option[String]);

}