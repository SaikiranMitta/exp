package com.iac.soc.backend.threat

import com.iac.soc.backend.api.common.Utils.escapeString
import com.iac.soc.backend.threat.models.Indicator
import com.iac.soc.backend.threat.models.{HashBlacklistStatus, IpBlacklistStatus}
import scalikejdbc.{AutoSession, SQL}
import com.typesafe.scalalogging.LazyLogging

/**
  * The threat sub-system repository
  */
private[threat] object Repository extends LazyLogging {

  private[threat] implicit val session = AutoSession

  /**
    * Gets the blacklist status of an IP
    *
    * @param ip the ip for which the blacklist status must be fetched
    * @return the blacklist status for the ip
    */
  def getIpBlacklistStatus(ip: String): IpBlacklistStatus = {
    IpBlacklistStatus(ip, false)
  }

  /**
    * Gets the blacklist status of a hash
    *
    * @param hash the hash for which the blacklist status must be fetched
    * @return the blacklist status for the hash
    */
  def getHashBlacklistStatus(hash: String): HashBlacklistStatus = {
    HashBlacklistStatus(hash, false)
  }

  def getThreatIndicators(indicators: Seq[String]): List[Map[String, String]] = {

    logger.info(s"Fetching indicators details for : ${indicators}")

    val threatIndicators = List.empty[Map[String, String]]

    try {

      if (indicators.size > 0) {

        var formattedIndicators: Seq[String] = Seq.empty
        indicators.foreach(indicator => {
          formattedIndicators =  formattedIndicators :+ escapeString(indicator)
        })

        val sql = s" select indicator, indicator_type from threat_indicators where indicator in('${formattedIndicators.mkString("','")}') AND created_on >= NOW() - INTERVAL 1 DAY"

        logger.info(s"Fetch indicators query - ${sql}")

        val threatIndicators = SQL(sql).map(rs =>
          Map(
            "indicator" -> rs.string("indicator"),
            "indicator_type" -> rs.string("indicator_type")
          )).list().apply()

      }

    } catch {

      case ex: Exception => {
        logger.info(s"Error in fetching indicators ${ex}")
        ex.printStackTrace()
      }

    }

    logger.info(s"Sending results for indicators : ${indicators}")

    threatIndicators

  }

  def bulkInsertThreatIndicator(items: Seq[Indicator]): Long = {

    var source_id: Long = 0;

    if (items.size != 0) {
      var ins_value_sql = items.map(item => s"('${item.value}','${item.indicatorType}')")


      val ins_sql = s"""insert into threat_indicators(`indicator`, `indicator_type`) VALUES ${ins_value_sql.mkString(",")}"""

      source_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()
    }


    return source_id;

  }

}
