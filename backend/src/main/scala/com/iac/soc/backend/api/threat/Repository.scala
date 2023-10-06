package com.iac.soc.backend.api.threat

import java.io.File
import java.net.InetAddress

import akka.actor.ActorSystem
import com.iac.soc.backend.api.common.JsonSupport
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, NamedDB, SQL}
import com.iac.soc.backend.api.common.mapping.ThreatMapping._
import com.maxmind.db.{CHMCache, Reader}
import com.typesafe.config.ConfigFactory

private[threat] class Repository()(implicit val system: ActorSystem) extends JsonSupport with LazyLogging {
  implicit val session = AutoSession

  /**
    * Maxmind Database reader instance
    */

  var database: Reader = null

  def getThreatsCount(params: Map[String, String]): BigInt = {

    val getCountSql = s""" select count(id) as total_count from threat_indicators WHERE created_on >= "${params.get("from").get}" and created_on<= "${params.get("to").get}"""";

    logger.info(s"SQL for test: ${getCountSql}")

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;
  }

  def getThreats(params: Map[String, String]): List[ThreatsResponse] = {
    var whereCondition = ""
    if (!params.get("onlyip").isEmpty) {
      whereCondition += " AND indicator_type = 'IP' ";
    }
    var getThreats = s""" select indicator, indicator_type from threat_indicators WHERE created_on >= "${params.get("from").get}" and created_on<= "${params.get("to").get}" ${whereCondition} order by created_on desc""";
    if (params.get("onlyip").isEmpty) {
      getThreats += " limit 0, 10";
    }

    logger.info(s"SQL for test: ${getThreats}")

    val main_config = ConfigFactory.load()
    val env = main_config.getString("environment")

    // Set the database
    if (env == "production") {

      database = new Reader(new File("resources/GeoLite2-City.mmdb"), new CHMCache())

    } else if (env == "staging") {

      database = new Reader(new File("resources/GeoLite2-City.mmdb"), new CHMCache())

    } else {

      val resourcesPath = getClass.getResource("/GeoLite2-City.mmdb")

      database = new Reader(new File(resourcesPath.getPath()), new CHMCache())

    }

    val threats = SQL(getThreats).map(rs => {
      var latitude = ""
      var longitude = ""
      var country: String = ""

      if (rs.string("indicator_type") == "IP" && !params.get("onlyip").isEmpty) {
        try {
          val geoInfo = database.get(InetAddress.getByName(rs.string("indicator")))
          val location = geoInfo.get("location")
          latitude = if (location.has("latitude")) location.get("latitude").toString else ""
          longitude = if (location.has("longitude")) location.get("longitude").toString else ""
          country = geoInfo.get("country").get("names").get("en").asText()

        } catch {
          case e: Exception => {

            logger.error(s"Error while fetching location of ${rs.string("indicator")} :: ${e}")

          }
        }
      }

      ThreatsResponse(
        latitude,
        longitude,
        rs.string("indicator"),
        rs.string("indicator_type"),
        1,
        country
      )
    }).list().apply()

    return threats;
  }

  def getMatchedThreats(params: Map[String, String]): List[Map[String, Any]] = {

    var query = s"""with  subset as
      (select resource,resourcecategory,sourceip,sourcecategory,destinationip,destinationcategory,signature,signaturecategory,sha1,sha1category,sha256,sha256category,md5,md5category from logs where timestamp between date_parse('${params.get("from").get}','%Y-%m-%dT%T.%fZ') and date_parse('${params.get("to").get}','%Y-%m-%dT%T.%fZ') and (resourcecategory is not null or sourcecategory is not null or destinationcategory is not null or signaturecategory is not null or sha1category is not null or sha256category is not null or sha256category is not null or md5category is not null ) )
      select indicator, category ,logs, indicator_type from (

      select resource as indicator ,resourcecategory as category ,count(resourcecategory) as logs, 'resource' as indicator_type from subset where resourcecategory is not null group by resource,resourcecategory
      UNION ALL
      select sourceip as indicator ,sourcecategory as category ,count(sourcecategory)  as logs,'IP' as indicator_type  from subset where sourcecategory is not null group by sourceip,sourcecategory
      UNION ALL
      select destinationip as indicator ,destinationcategory as category ,count(destinationcategory)  as logs,'IP' as indicator_type  from subset where destinationcategory is not null group by destinationip,destinationcategory
      UNION ALL
      select signature as indicator,signaturecategory as category ,count(signaturecategory)  as logs,'signature' as indicator_type  from subset where signaturecategory is not null group by signature,signaturecategory
      UNION ALL
      select sha1 as indicator ,sha1category as category ,count(sha1category)  as logs,'SHA1' as indicator_type  from subset where sha1category is not null group by sha1,sha1category
      UNION ALL
      select sha256 as indicator ,sha256category as category ,count(sha256category)  as logs,'SHA256' as indicator_type  from subset where sha256category is not null group by sha256,sha256category
      UNION ALL
      select md5 as indicator ,md5category as category ,count(md5category)  as logs,'MD5' as indicator_type  from subset where md5category is not null group by md5,md5category
      ) order by Logs desc limit 10"""

    if (!params.get("onlyip").isEmpty) {
      query = s"""with  subset as
        (select sourceip,sourcelongitude,sourcelatitude,destinationip,destinationlatitude,destinationlongitude,sourcecategory,destinationcategory, destinationcountry, sourcecountry from logs where
        timestamp between date_parse('${params.get("from").get}','%Y-%m-%dT%T.%fZ') and date_parse('${params.get("to").get}','%Y-%m-%dT%T.%fZ') and (sourcecategory is not null or destinationcategory is not null ) )

        select logs,latitude,longitude, country
        from
        (
        select count(sourceip) as logs,sourcelongitude as longitude ,sourcelatitude as latitude, sourcecountry as country
        from subset where sourcecategory is not null group by sourcelatitude,sourcelongitude,sourcecountry
        UNION All
        select count(destinationip) as logs,destinationlongitude as longitude ,destinationlatitude as latitude, destinationcountry as country
        from subset where  destinationcategory is not null group by destinationlatitude,destinationlongitude,destinationcountry
        )"""
    }

    println("================================")
    println(query)
    val logResult: List[Map[String, Any]] = NamedDB('presto_analyst) readOnly { implicit session =>
      SQL(query).map(_.toMap()).list().apply()
    }
    println("================================")
    println(logResult)


    return logResult

  }
}
