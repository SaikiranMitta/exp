package com.iac.soc.backend.api.common.mapping

object ThreatMapping {

  final case class ThreatCountInput(by: String, from: String, to: String, buckets: Int, organizations: Option[String]);
  final case class ThreatCountResponse(status_code: Int, message: String, count: BigInt);
  final case class ThreatsResponse(latitude: String, longitude: String, indicator: String, indicator_type: String, sources: Int, country: String)
  final case class ThreatsResultResponse(status_code: Int, message: String, threats: List[ThreatsResponse]);
  final case class MatchedThreatsResultResponse(status_code: Int, message: String, threats: List[Map[String, Any]]);

}
