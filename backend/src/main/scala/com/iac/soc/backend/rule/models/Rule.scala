package com.iac.soc.backend.rule.models

/**
  * Represents a rule within the system
  *
  * @param id            the id of the rule
  * @param name          the name of the rule
  * @param isGlobal      whether the rule is a global rule or not
  * @param organizations the organizations the rule belongs to
  * @param categories    the categories the rule belongs to
  * @param query         the query for the rule
  * @param status        the status of the rule
  * @param severity      the severity of the rule
  * @param frequency     the frequency of the rule
  */
case class Rule(
  id: Int,
  name: String,
  isGlobal: Boolean,
  organizations: Vector[Organization],
  query: String,
  status: String,
  categories: Vector[Category],
  severity: Severity,
  frequency: String
)
