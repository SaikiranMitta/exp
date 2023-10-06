package com.iac.soc.backend.api.common.mapping

import spray.json.{DefaultJsonProtocol, JsFalse, JsNumber, JsString, JsTrue, JsValue, JsonFormat}

final case class columns(name: String)

final case class Values[T](values: List[T])

final case class StatsResponse(state: String, queued: Boolean, scheduled: Boolean, nodes: Int, totalSplits: Int, queuedSplits: Int, runningSplits: Int, completedSplits: Int, cpuTimeMillis: Int, wallTimeMillis: Int, queuedTimeMillis: Int, elapsedTimeMillis: Int, processedRows: Int, processedBytes: Int, peakMemoryBytes: Int, spilledBytes: Int);

final case class QueryResponse(id: String, infoUri: String, nextUri: Option[String] = None, columns: Option[List[columns]] = None, data: Option[List[List[Option[Any]]]] = None, stats: Option[StatsResponse] = None)

object LogFormat extends DefaultJsonProtocol {

  implicit object AnyJsonFormat extends JsonFormat[Any] {

    def write(x: Any) = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b == true => JsTrue
      case b: Boolean if b == false => JsFalse
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

  implicit val columnFormat = jsonFormat1(columns)

  implicit val statsResponseFormat = jsonFormat16(StatsResponse)

  implicit val logFormat = jsonFormat6(QueryResponse)

}
