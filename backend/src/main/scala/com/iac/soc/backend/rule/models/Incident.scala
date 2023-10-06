package com.iac.soc.backend.rule.models

import java.time.ZonedDateTime

/**
  * Represents an incident
  *
  * @param id           the id of the incident
  * @param ruleId       the associated rule's id
  * @param organization the organization the incident was triggered for
  * @param query        the query of the incident
  * @param createdOn    the date the incident was created on
  */
case class Incident(id: String, ruleId: Int, organization: Organization, query: String, createdOn: ZonedDateTime)
