package com.iac.soc.backend.pipeline.enrichment

import java.io.File
import java.net.InetAddress

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.JsonNode
import com.iac.soc.backend.schemas._
import com.iac.soc.backend.threat.messages._
import com.maxmind.db.{CHMCache, Reader}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

/**
  * The companion object to the enrichment worker actor
  */
object Worker {

  /**
    * Creates the configuration for the enrichment worker actor
    *
    * @param enrichment  the enrichment sub-system reference
    * @param log         the log sub-system reference
    * @param geolocation the geolocation sub-system reference
    * @param threat      the threat sub-system reference
    * @return the configuration for the enrichment worker actor
    */
  def props(enrichment: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef /*, maxMindDatabase: Reader*/) = Props(new Worker(enrichment, log, geolocation, threat /*, maxMindDatabase*/))

  /**
    * The name of the enrichment worker actor
    *
    * @return the name of the enrichment worker actor
    */
  def name = "worker"

}

class Worker(enrichment: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef /*, maxMindDatabase: Reader*/) extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * Maxmind Database reader instance
    */

  var database: Reader = null

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val timeout: Timeout = Timeout(60 seconds)

  private[this] def enrichSourceIP(sourceIpEndrichedData: JsonNode, enrichedLog: Log): Log = {

    var log = enrichedLog

    if (sourceIpEndrichedData != null) {

      if (sourceIpEndrichedData.has("country") && sourceIpEndrichedData.get("country").has("en")) {

        val country = sourceIpEndrichedData.get("country").get("en")

        log = log.copy(sourcecountry = Some(country.toString))

      }
      if (sourceIpEndrichedData.has("location") && sourceIpEndrichedData.get("location").has("latitude")) {

        val lat = sourceIpEndrichedData.get("location").get("latitude")

        log = log.copy(sourcelatitude = Some(lat.toString.toFloat))

      }

      if (sourceIpEndrichedData.has("location") && sourceIpEndrichedData.get("location").has("longitude")) {

        val long = sourceIpEndrichedData.get("location").get("longitude")

        log = log.copy(sourcelongitude = Some(long.toString.toFloat))

      }

    }

    log
  }

  private[this] def enrichDestIP(destIpEndrichedData: JsonNode, enrichedLog: Log): Log = {

    var log = enrichedLog

    if (destIpEndrichedData != null) {

      if (destIpEndrichedData.has("country") && destIpEndrichedData.get("country").has("en")) {

        val country = destIpEndrichedData.get("country").get("en")

        log = log.copy(destinationcountry = Some(country.toString))

      }

      if (destIpEndrichedData.has("location") && destIpEndrichedData.get("location").has("latitude")) {

        val lat = destIpEndrichedData.get("location").get("latitude")

        log = log.copy(destinationlatitude = Some(lat.toString.toFloat))

      }

      if (destIpEndrichedData.has("location") && destIpEndrichedData.get("location").has("longitude")) {

        val long = destIpEndrichedData.get("location").get("longitude")

        log = log.copy(destinationlongitude = Some(long.toString.toFloat))

      }

    }

    log
  }

  private[this] def enrich(logs: Logs): Seq[Log] = {

    val enrichedLogs = logs.value.map { log =>

      logger.info(s"Starting enrichment for log: ${log.id}")

      var enrichedLog = log

      var searchIndicators = scala.collection.immutable.Seq.empty[String]

      if (log.sourceip != None && log.sourceip.get != "" && log.sourceip.get != null && !log.sourceip.get.contains("/")) {

        try {

          val sourceip = log.sourceip

          logger.info(s"Fetching details of ${sourceip} for log ${log.id}")

          val addressDetails = InetAddress.getByName(sourceip.get).getAddress()

          val addressInfo = InetAddress.getByAddress(addressDetails)

          val isLocalIP = addressInfo.isSiteLocalAddress();

          if (!isLocalIP) {

            logger.info(s"Source Ip ${sourceip} is public IP for log ${log.id}")

            val sourceIpEndrichedData = database.get(InetAddress.getByName(sourceip.get))

            enrichedLog = enrichSourceIP(sourceIpEndrichedData, enrichedLog)

          }

        }
        catch {

          case e: Exception => {
            e.printStackTrace()
            logger.error(s"Exception in getting details of IP : ${log.sourceip} ")
          }

        }

        searchIndicators = searchIndicators :+ log.sourceip.get

      }

      if (log.destinationip != None && log.destinationip.get != "" && log.destinationip.get != null && !log.destinationip.get.contains("/")) {
        try {

          val destinationip = log.destinationip

          logger.info(s"Fetching details of destination ip :  ${destinationip}")

          val addressDetails = InetAddress.getByName(destinationip.get).getAddress()

          val addressInfo = InetAddress.getByAddress(addressDetails)

          val isLocalIP = addressInfo.isSiteLocalAddress();

          if (!isLocalIP) {

            logger.info(s"Destination Ip ${destinationip} is  public IP")

            val destIpEndrichedData = database.get(InetAddress.getByName(destinationip.get))

            enrichedLog = enrichDestIP(destIpEndrichedData, enrichedLog)

          }

        } catch {

          case e: Exception => {
            e.printStackTrace()
            logger.error(s"Exception in getting details of IP : ${log.destinationip} ")
          }

        }

        searchIndicators = searchIndicators :+ log.destinationip.get

      }

      if (log.sourcehost != None && log.sourcehost.get != "" && log.sourcehost.get != null) searchIndicators = searchIndicators :+ log.sourcehost.get

      if (log.destinationhost != None && log.destinationhost.get != "" && log.destinationhost.get != null) searchIndicators = searchIndicators :+ log.destinationhost.get

      if (log.resource != None && log.resource.get != "" && log.resource.get != null) searchIndicators = searchIndicators :+ log.resource.get

      if (log.signature != None && log.signature.get != "" && log.signature.get != null) searchIndicators = searchIndicators :+ log.signature.get

      if (log.sha1 != None && log.sha1.get != "" && log.sha1.get != null) searchIndicators = searchIndicators :+ log.sha1.get

      if (searchIndicators.size > 0) {

        try {

          logger.info(s"Fetching indicators for : ${searchIndicators}")
          val indicatorDetailsFuture = threat ? GetIndicatorDetails(searchIndicators)

          logger.info("Indicator details fetched : ")
          val indicatorDetails = Await.result(indicatorDetailsFuture, 2 minutes).asInstanceOf[List[Map[String, String]]]

          logger.info("Mapping indicators to log : ")

          indicatorDetails.foreach(indicator => {

            if (indicator("indicator") == log.sourcehost || indicator("indicator") == log.sourceip) enrichedLog = enrichedLog.copy(sourcecategory = Some(indicator("indicator_type")))

            if (indicator("indicator") == log.destinationhost || indicator("indicator") == log.destinationip) enrichedLog = enrichedLog.copy(destinationcategory = Some(indicator("indicator_type")))

            if (indicator("indicator") == log.resource) enrichedLog = enrichedLog.copy(resourcecategory = Some(indicator("indicator_type")))

            if (indicator("indicator") == log.signature) enrichedLog = enrichedLog.copy(signaturecategory = Some(indicator("indicator_type")))

            if (indicator("indicator") == log.sha1) enrichedLog = enrichedLog.copy(sha1Category = Some(indicator("indicator_type")))

          })

          logger.info(s"Final Enriched Data : ${enrichedLog} ")

        } catch {

          case ex: Exception => {
            logger.info(s"Error in fetching indicators for log ${log.id}")
            ex.printStackTrace()
          }

        }

      }
      // Publish the log to the log sub-system
      enrichedLog

    }

    enrichedLogs

  }


  private[this] def enrichBulk(logs: Logs): Seq[Log] = {

    try {

      for {

        indicators <- threat ? logs

      } yield indicators


      val enrichedLogs = logs.value.map { log =>

        logger.info(s"Starting enrichment for log: ${log.id}")

        var enrichedLog = log

        if (log.sourceip != None && log.sourceip.get != "" && log.sourceip.get != null && !log.sourceip.get.contains("/")) {

          try {

            val sourceip = log.sourceip

            logger.info(s"Fetching details of ${sourceip} for log ${log.id}")

            val addressDetails = InetAddress.getByName(sourceip.get).getAddress()

            val addressInfo = InetAddress.getByAddress(addressDetails)

            val isLocalIP = addressInfo.isSiteLocalAddress();

            if (!isLocalIP) {

              logger.info(s"Source Ip ${sourceip} is public IP for log ${log.id}")

              val sourceIpEndrichedData = database.get(InetAddress.getByName(sourceip.get))

              enrichedLog = enrichSourceIP(sourceIpEndrichedData, enrichedLog)

            }

          }
          catch {

            case e: Exception => {
              e.printStackTrace()
              logger.error(s"Exception in getting details of IP : ${log.sourceip} ")
            }

          }

        }

        if (log.destinationip != None && log.destinationip.get != "" && log.destinationip.get != null && !log.destinationip.get.contains("/")) {

          try {

            val destinationip = log.destinationip

            logger.info(s"Fetching details of destination ip :  ${destinationip}")

            val addressDetails = InetAddress.getByName(destinationip.get).getAddress()

            val addressInfo = InetAddress.getByAddress(addressDetails)

            val isLocalIP = addressInfo.isSiteLocalAddress();

            if (!isLocalIP) {

              logger.info(s"Destination Ip ${destinationip} is  public IP")

              val destIpEndrichedData = database.get(InetAddress.getByName(destinationip.get))

              enrichedLog = enrichDestIP(destIpEndrichedData, enrichedLog)

            }

          } catch {

            case e: Exception => {
              e.printStackTrace()
              logger.error(s"Exception in getting details of IP : ${log.destinationip} ")
            }

          }

        }

        // Publish the log to the log sub-system
        enrichedLog

      }

      enrichedLogs

    } catch {

      case ex: Exception => {

        logger.info(s"Error in enrichment of logs ${ex.toString} ")

        ex.printStackTrace()

        logs.value

      }

    }

  }

  private[this] def enrichSingleLog(log: Log): Log = {

    logger.info(s"Starting enrichment for log: ${log.id}")

    var enrichedLog = log

    var searchIndicators = scala.collection.immutable.Seq.empty[String]

    if (log.sourceip != None && log.sourceip.get != "" && log.sourceip.get != null && !log.sourceip.get.contains("/")) {

      try {

        val sourceip = log.sourceip

        logger.info(s"Fetching details of ${sourceip} for log ${log.id}")

        val addressDetails = InetAddress.getByName(sourceip.get).getAddress()

        val addressInfo = InetAddress.getByAddress(addressDetails)

        val isLocalIP = addressInfo.isSiteLocalAddress()

        if (!isLocalIP) {

          logger.info(s"Source Ip ${sourceip} is public IP for log ${log.id}")

          val sourceIpEndrichedData = database.get(InetAddress.getByName(sourceip.get))

          enrichedLog = enrichSourceIP(sourceIpEndrichedData, enrichedLog)

        }

      }
      catch {

        case e: Exception => {
          e.printStackTrace()
          logger.error(s"Exception in getting details of IP : ${log.sourceip} ")
        }

      }

      searchIndicators = searchIndicators :+ log.sourceip.get

    }

    if (log.destinationip != None && log.destinationip.get != "" && log.destinationip.get != null && !log.destinationip.get.contains("/")) {

      try {

        val destinationip = log.destinationip

        logger.info(s"Fetching details of destination ip :  ${destinationip}")

        val addressDetails = InetAddress.getByName(destinationip.get).getAddress()

        val addressInfo = InetAddress.getByAddress(addressDetails)

        val isLocalIP = addressInfo.isSiteLocalAddress();

        if (!isLocalIP) {

          logger.info(s"Destination Ip ${destinationip} is  public IP")

          val destIpEndrichedData = database.get(InetAddress.getByName(destinationip.get))

          enrichedLog = enrichDestIP(destIpEndrichedData, enrichedLog)

        }

      } catch {

        case e: Exception => {
          e.printStackTrace()
          logger.error(s"Exception in getting details of IP : ${log.destinationip} ")
        }

      }

      searchIndicators = searchIndicators :+ log.destinationip.get

    }

    if (log.sourcehost != None && log.sourcehost.get != "" && log.sourcehost.get != null) searchIndicators = searchIndicators :+ log.sourcehost.get

    if (log.destinationhost != None && log.destinationhost.get != "" && log.destinationhost.get != null) searchIndicators = searchIndicators :+ log.destinationhost.get

    if (log.resource != None && log.resource.get != "" && log.resource.get != null) searchIndicators = searchIndicators :+ log.resource.get

    if (log.signature != None && log.signature.get != "" && log.signature.get != null) searchIndicators = searchIndicators :+ log.signature.get

    if (log.sha1 != None && log.sha1.get != "" && log.sha1.get != null) searchIndicators = searchIndicators :+ log.sha1.get

    if (searchIndicators.size > 0) {

      try {

        logger.info(s"Fetching indicators for : ${searchIndicators}")
        val indicatorDetailsFuture = threat ? GetIndicatorDetails(searchIndicators)

        logger.info("Indicator details fetched : ")
        val indicatorDetails = Await.result(indicatorDetailsFuture, 2 minutes).asInstanceOf[List[Map[String, String]]]

        logger.info("Mapping indicators to log : ")

        indicatorDetails.foreach(indicator => {

          if (indicator("indicator") == log.sourcehost || indicator("indicator") == log.sourceip) enrichedLog = enrichedLog.copy(sourcecategory = Some(indicator("indicator_type")))

          if (indicator("indicator") == log.destinationhost || indicator("indicator") == log.destinationip) enrichedLog = enrichedLog.copy(destinationcategory = Some(indicator("indicator_type")))

          if (indicator("indicator") == log.resource) enrichedLog = enrichedLog.copy(resourcecategory = Some(indicator("indicator_type")))

          if (indicator("indicator") == log.signature) enrichedLog = enrichedLog.copy(signaturecategory = Some(indicator("indicator_type")))

          if (indicator("indicator") == log.sha1) enrichedLog = enrichedLog.copy(sha1Category = Some(indicator("indicator_type")))

        })

        logger.info(s"Final Enriched Data : ${enrichedLog} ")

      } catch {

        case ex: Exception => {
          logger.info(s"Error in fetching indicators for log ${log.id}")
          ex.printStackTrace()
        }

      }

    }
    // Publish the log to the log sub-system
    enrichedLog


  }

  /**
    * Hook into just before enrichment worker actor is started for any initialization
    */
  override def preStart(): Unit = {

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

    logger.info("Started enrichment worker actor")
  }

  /**
    * Handles incoming messages to the enrichment worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Enrich ingested logs
    case logs: Logs => {

      try {

        logger.info(s"Enrichment worker received ingested logs: ${logs}")

        val enriched: Seq[Log] = enrich(logs)

        logger.info(s"Enriched logs results : ${enriched}")

        sender() ! enriched

      } catch {

        case ex: Exception => {

          logger.error(s"Error in enrichment of logs :: ${ex}")

          ex.printStackTrace()

          sender() ! logs.value

        }

      }

    }

    // Enrich ingested log

    case log: Log => {

      try {

        logger.info(s"Enrichment worker received ingested logs: ${log}")

        val enriched: Log = enrichSingleLog(log)

        logger.info(s"Enriched logs results : ${enriched}")

        sender() ! enriched

      } catch {

        case ex: Exception => {

          logger.error(s"Error in enrichment of logs :: ${ex}")

          ex.printStackTrace()

          sender() ! log

        }

      }

    }


    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
