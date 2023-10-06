package com.iac.soc.backend.pipeline.normalization

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Date, TimeZone}

import com.iac.soc.backend.pipeline.messages.{IngestedLog, IngestedLogs, Normalizer}
import com.iac.soc.backend.schemas.Log
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, SQL}

/**
  * The log sub-system repository
  */
private[normalization] object Repository extends LazyLogging {

  private[normalization] implicit val session = AutoSession

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * Gets the logs for an organization
    *
    * @param query            the query to be executed
    * @param organizationId   the id of the organization
    * @param organizationName the name of the organization
    * @return the results of the execution
    */
  def getNormalizersByIngestorIds(ingestorIds: String = null): Map[Int, List[Normalizer]] = {

    logger.info(s"Fetching normalizer for ingestor :: ${ingestorIds} ")

    var sql = "";

    if (ingestorIds == null) {
      sql = s" Select * from normalizer "
    } else {
      sql = s" Select * from normalizer where log_source_id in(${ingestorIds})"
    }

    val normalizerList: List[Normalizer] = SQL(sql).map(rs =>

      Normalizer(
        rs.int("id"),
        rs.string("filters"),
        rs.int("log_source_id"),
        rs.string("grok_pattern"),
        rs.string("normalizer_type"),
        rs.string("mapping"),
        rs.boolean("is_global")
      )).list().apply()

    val normalizersByIngestors = normalizerList.sortBy(_.isGlobal).groupBy(_.ingestorId)

    return normalizersByIngestors

  }

  def convertIngestedLogToUnmappedLog(ingestedLogs: IngestedLogs): Seq[Log] = {

    val today = Date.from(Instant.parse(new Date().toInstant.toString))
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

    val normalized = ingestedLogs.value.map(ingestedLog => {

      Log(id = Some(ingestedLog.id), timestamp = Some(formatter.format(today)), organization = Some("Unknown"), `type` = Some("Unmapped"), message = Some(ingestedLog.log), date = Some(dateFormatter.format(today)))

    })

    normalized

  }

  def convertSingleIngestedLogToUnmappedLog(ingestedLog: IngestedLog): Log = {

    val today = Date.from(Instant.parse(new Date().toInstant.toString))
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

    Log(id = Some(ingestedLog.id), timestamp = Some(formatter.format(today)), organization = Some("Unknown"), `type` = Some("Unmapped"), message = Some(ingestedLog.log), date = Some(dateFormatter.format(today)))

  }

}