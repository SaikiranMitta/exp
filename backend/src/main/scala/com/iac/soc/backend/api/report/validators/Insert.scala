package com.iac.soc.backend.api.report

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import com.iac.soc.backend.api.common.mapping.CommonMapping.FieldErrorInfo
import com.iac.soc.backend.api.common.mapping.ReportMapping.ReportInsMapping
import com.iac.soc.backend.api.gateway.Validator
import org.apache.logging.log4j.core.util.CronExpression
import spray.json.DefaultJsonProtocol

object Insert extends Validator[ReportInsMapping] with SprayJsonSupport with DefaultJsonProtocol {

  def reportName(name: String): Boolean = {

    if (name.isEmpty) true else false

  }

  def reportCategories(cat: List[Category]): Boolean = {

    if (cat.isEmpty) true else false

  }

  def reportQuery(query: String): Boolean = {

    if (query.isEmpty) true else false

  }

  def reportCronExpression(cron_expression: String): Boolean = {

    if (cron_expression.isEmpty) true else false

  }

  def reportCronExpressionValidator(cron_expression: String): Boolean = {

    if (CronExpression.isValidExpression(cron_expression)) false else true

  }

  override def apply(model: ReportInsMapping): Seq[FieldErrorInfo] = {

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)

    val nameErrorOpt: Option[FieldErrorInfo] = validationStage(reportName(model.name), "name", "Name is a Compulsory field")

    val catErrorOpt: Option[FieldErrorInfo] = validationStage(reportCategories(model.categories), "categories", "Rule must have atleast one category")

    val queryErrorOpt: Option[FieldErrorInfo] = validationStage(reportQuery(model.query), "query", "Query is a Compulsory field")

    val cronExpressionErrorOpt: Option[FieldErrorInfo] = validationStage(reportCronExpression(model.cron_expression), "cron_expression", "Cron Expression is a Compulsory field")

    val cronExpressionValidatorErrorOpt: Option[FieldErrorInfo] = validationStage(reportCronExpressionValidator(model.cron_expression), "cron_expression", "Invalid Cron Expression")

    Seq(nameErrorOpt, catErrorOpt, queryErrorOpt, cronExpressionErrorOpt, cronExpressionValidatorErrorOpt).flatten

  }

}
