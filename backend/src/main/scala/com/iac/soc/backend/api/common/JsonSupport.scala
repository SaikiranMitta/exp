package com.iac.soc.backend.api.common


import java.sql.Timestamp

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.iac.soc.backend.api.common.mapping.CategoryMapping.{Categories, CategoriesReponse, Category}
import com.iac.soc.backend.api.common.mapping.CommonMapping.ActionPerformed
import com.iac.soc.backend.api.common.mapping.DashboardMapping._
import com.iac.soc.backend.api.common.mapping.IncidentMapping._
import com.iac.soc.backend.api.common.mapping.IncidentsAttachmentsMapping.IncidentsAttachments
import com.iac.soc.backend.api.common.mapping.LogMapping._
import com.iac.soc.backend.api.common.mapping.LogsourcesMapping._
import com.iac.soc.backend.api.common.mapping.NormalizerMapping._
import com.iac.soc.backend.api.common.mapping.OrganizationMapping.{Organization, Organizations, OrganizationsReponse}
import com.iac.soc.backend.api.common.mapping.ReportMapping._
import com.iac.soc.backend.api.common.mapping.RuleMapping._
import com.iac.soc.backend.api.common.mapping.ThreatMapping._
import spray.json.DefaultJsonProtocol._
import spray.json.{JsFalse, JsNumber, JsString, JsTrue, JsValue, JsonFormat}

trait JsonSupport extends SprayJsonSupport {

  /**
    * Implicit mappings to handle Any type
    */
  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case l: Long => JsNumber(l)
      case s: String => JsString(s)
      case f: Float => JsNumber(f)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
      case d: Double => JsNumber(d)
      case ti:Timestamp => JsString(ti.toString)
      case _ => null
    }

    def read(value: JsValue) = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
      case _ => null

    }

  }

  implicit val actionPerformedResponseJsonFormat = jsonFormat2(ActionPerformed)

  implicit val organizationJsonFormat = jsonFormat2(Organization)
  implicit val organizationsResponseJsonFormat = jsonFormat1(Organizations)

  implicit val categoryJsonFormat = jsonFormat2(Category)
  implicit val categoryResponseJsonFormat = jsonFormat1(Categories)

  implicit val ruleJsonFormat = jsonFormat16(RuleResponse);
  implicit val rulesGetResponseJsonFormat = jsonFormat8(RulesResponse);

  implicit val ruleInsRequestJsonFormat = jsonFormat9(RuleInsMapping);
  implicit val ruleUpdateRequestJsonFormat = jsonFormat9(RuleUpdateMapping);
  implicit val ruleGetRequestJsonFormat = jsonFormat3(GetRuleResponse);

  implicit val reportResponseJsonFormat = jsonFormat13(ReportResponse);
  implicit val reportsResponseJsonFormat = jsonFormat8(ReportsResponse);

  implicit val reportInsRequestJsonFormat = jsonFormat7(ReportInsMapping);
  implicit val reportUpdateRequestJsonFormat = jsonFormat7(ReportUpdateMapping);
  implicit val reportGetRequestJsonFormat = jsonFormat3(GetReportResponse);

  implicit val organizationGetRequestJsonFormat = jsonFormat3(OrganizationsReponse);
  implicit val categoryGetRequestJsonFormat = jsonFormat3(CategoriesReponse);

  // incident
  implicit val incidentRuleSummaryGetResponseJsonFormat = jsonFormat6(IncidentRuleSummaryReponseMapping);
  implicit val incidentSummaryGetResponseJsonFormat = jsonFormat10(IncidentSummaryResponse);

  implicit val incidentAttachmentGetResponseJsonFormat = jsonFormat3(IncidentsAttachments);
  implicit val incidentRuleGetResponseJsonFormat = jsonFormat6(IncidentRuleReponseMapping);
  implicit val incidentGetResponseJsonFormat = jsonFormat10(IncidentResponse);

  implicit val incidentRuleBucketGetResponseJsonFormat = jsonFormat3(IncidentRuleBucketReponseMapping);
  implicit val incidentBucketGetResponseJsonFormat = jsonFormat10(IncidentBucketResponse);

  implicit val LogsPostRequestJsonFormat = jsonFormat1(LogsPostRequest)
  implicit val LogsPostStatsRequestJsonFormat = jsonFormat4(LogsPostStatsRequest)

  implicit val StatResponseFormat = jsonFormat16(StatsResponse)
  implicit val StatementResponseFormat = jsonFormat5(StatementResponse)

  implicit val logsResponseFormat = jsonFormat6(LogsResponse)
  implicit val logsStatsResponseFormat = jsonFormat5(LogsStatsResponse)


  implicit val incidentByIdFormat = jsonFormat6(IncidentById)
  implicit val incidentByIdResponseFormat = jsonFormat3(IncidentByIdResponse)

  implicit val LogsourceJsonFormat = jsonFormat6(LogsourceResponse)
  implicit val LogsourcesJsonFormat = jsonFormat6(LogsourcesResponse)
  implicit val LogsourceInsertJsonFormat = jsonFormat5(LogsourceInsert)
  implicit val LogsourceUpdateJsonFormat = jsonFormat5(LogsourceUpdate)

  implicit val NormalizerJsonFormat = jsonFormat7(NormalizerResponse)
  implicit val NormalizersJsonFormat = jsonFormat6(NormalizersResponse)
  implicit val NormalizerInsertJsonFormat = jsonFormat6(NormalizerInsert)
  implicit val NormalizerUpdateJsonFormat = jsonFormat6(NormalizerUpdate)

  implicit val DashboardInputJsonFormat = jsonFormat1(DashboardInput)
  implicit val DashboardResponseJsonFormat = jsonFormat3(DashboardResponse)
  implicit val DashboardAlertInputJsonFormat = jsonFormat5(DashboardAlertInput)
  implicit val DashboardAlertResponseJsonFormat = jsonFormat3(DashboardAlertResponse)

  implicit val ThreatCountResponseJsonFormat = jsonFormat3(ThreatCountResponse)
  implicit val ThreatsResponseJsonFormat= jsonFormat6(ThreatsResponse)
  implicit val ThreatsResultResponseJsonFormat = jsonFormat3(ThreatsResultResponse)
  implicit val MatchedThreatsResultResponseJsonFormat = jsonFormat3(MatchedThreatsResultResponse)


}
