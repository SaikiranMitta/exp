
package com.iac.soc.backend.api.common.mapping

import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.IncidentsAttachmentsMapping.IncidentsAttachments
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.Organization

object IncidentMapping {

  final case class IncidentRuleSummaryMapping(id: Long, name: String, severity: String, created_on: String, incidents_count: Int, category_ids: Option[String]);

  final case class IncidentRuleSummaryReponseMapping(id: Long, name: String, severity: String, created_on: String, incidents_count: Int, categories: List[Category]);

  final case class IncidentSummaryResponse(status_code: Int, message: String, page: Int, size: Int, sort_by: String, sort_order: String, total_records: Int, time_range_from: String, time_range_to: String, rules: List[IncidentRuleSummaryReponseMapping])

  final case class IncidentRuleMapping(id: Long, rule_id: Long, query: String, created_on: String, organizationId: Long, attachmentIds: Option[String]);

  final case class IncidentRuleReponseMapping(id: Long, rule_id: Long, query: String, created_on: String, organization: Organization, attachments: List[IncidentsAttachments]);

  final case class IncidentResponse(status_code: Int, message: String, page: Int, size: Int, sort_by: String, sort_order: String, total_records: Long, time_range_from: String, time_range_to: String, incidents: List[IncidentRuleReponseMapping])

  final case class IncidentRuleBucketReponseMapping(time_range_from: String, time_range_to: String, count: Long);

  final case class IncidentBucketResponse(status_code: Int, message: String, page: Int, size: Int, sort_by: String, sort_order: String, total_records: Long, time_range_from: String, time_range_to: String, buckets: List[IncidentRuleBucketReponseMapping])

  final case class IncidentById(id: Long, rule_id: Long, query: String, orgnaization_id: Long, total_hits: Long, created_on: String)

  final case class IncidentByIdResponse(status_code: Int, message: String, incidents: List[IncidentById])

}