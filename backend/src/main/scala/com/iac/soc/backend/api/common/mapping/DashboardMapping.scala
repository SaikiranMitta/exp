package com.iac.soc.backend.api.common.mapping

object DashboardMapping {

  final case class DashboardResponse(status_code: Int, message: String, logs: List[Map[String, Any]]);

  final case class DashboardInput(query: String);
  final case class DashboardAlertInput(by: String, from: String, to: String, buckets: Int, organizations: Option[String]);
  final case class DashboardAlertResponse(status_code: Int, message: String, alerts: Map[String, List[Double]]);

  final case class AlertCountResponse(status_code: Int, message: String, logs: Map[String, Map[String, Int]]);

}
