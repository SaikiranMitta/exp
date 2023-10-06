package com.iac.soc.backend.api.rule.validators

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.CommonMapping.FieldErrorInfo
import com.iac.soc.backend.api.common.mapping.RuleMapping.RuleInsMapping
import com.iac.soc.backend.api.gateway.Validator
import org.apache.logging.log4j.core.util.CronExpression
import spray.json.DefaultJsonProtocol

object Insert extends Validator[RuleInsMapping] with SprayJsonSupport with DefaultJsonProtocol {

  def ruleName(name: String): Boolean = {

    if (name.isEmpty) true else false

  }

  def ruleCategories(cat: List[Category]): Boolean = {

    if (cat.isEmpty) true else false

  }

  def ruleQuery(query: String): Boolean = {

    if (query.isEmpty) true else false

  }

  def ruleCronExpression(cron_expression: String): Boolean = {

    if (cron_expression.isEmpty) true else false

  }

  def ruleCronExpressionValidator(cron_expression: String): Boolean = {

    if (CronExpression.isValidExpression(cron_expression)) false else true

  }

  override def apply(model: RuleInsMapping): Seq[FieldErrorInfo] = {

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)

    val nameErrorOpt: Option[FieldErrorInfo] = validationStage(ruleName(model.name), "name", "Name is a Compulsory field")

    val catErrorOpt: Option[FieldErrorInfo] = validationStage(ruleCategories(model.categories), "categories", "Rule must have atleast one category")

    val queryErrorOpt: Option[FieldErrorInfo] = validationStage(ruleQuery(model.query), "query", "Query is a Compulsory field")

    val cronExpressionErrorOpt: Option[FieldErrorInfo] = validationStage(ruleCronExpression(model.cron_expression), "cron_expression", "Cron Expression is a Compulsory field")

    val cronExpressionValidatorErrorOpt: Option[FieldErrorInfo] = validationStage(ruleCronExpressionValidator(model.cron_expression), "cron_expression", "Invalid Cron Expression")

    Seq(nameErrorOpt, catErrorOpt, queryErrorOpt, cronExpressionErrorOpt, cronExpressionValidatorErrorOpt).flatten

  }

}
