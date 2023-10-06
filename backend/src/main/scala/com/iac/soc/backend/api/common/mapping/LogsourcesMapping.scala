package com.iac.soc.backend.api.common.mapping

object LogsourcesMapping {
  final case class LogsourceInsert(source: String, port: Option[String], bucket: Option[String], accessKey: Option[String], secretKey: Option[String]);
  final case class LogsourceUpdate(source: String, port: Option[String], bucket: Option[String], accessKey: Option[String], secretKey: Option[String]);
  final case class LogsourceResponse(id: Int, source: String, port: Option[String], bucket: Option[String], accessKey: Option[String], secretKey: Option[String]);
  final case class LogsourcesResponse(status_code: Int, message: String, page: Int, size: Int, total_records: Int, logsources: List[LogsourceResponse]);
}
