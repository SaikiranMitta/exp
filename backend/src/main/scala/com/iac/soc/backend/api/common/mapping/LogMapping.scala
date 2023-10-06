
package com.iac.soc.backend.api.common.mapping

object LogMapping {

  final case class LogsResponse(status_code: Int, message: String, query_id: String, page: Int, has_next_page: Boolean = true, logs: List[Map[String, Any]]);

  final case class LogsStatsResponse(status_code: Int, message: String, histogram: Option[List[(String, String)]], categories: Option[List[Map[String, Int]]], total_records: Int);

  final case class LogsPostRequest(query: String);

  final case class LogsPostStatsRequest(query: String, numberOfBins: Int, binField: String, weight: Int);

  final case class LogsGetRequest(id: String, page: Int)

  final case class StatsResponse(state: String, queued: Boolean, scheduled: Boolean, nodes: Int, totalSplits: Int, queuedSplits: Int, runningSplits: Int, completedSplits: Int, cpuTimeMillis: Int, wallTimeMillis: Int, queuedTimeMillis: Int, elapsedTimeMillis: Int, processedRows: Int, processedBytes: Int, peakMemoryBytes: Int, spilledBytes: Int);

  final case class StatementResponse(id: String, infoUri: String, nextUri: String, stats: StatsResponse, warnings: Option[List[String]])

}