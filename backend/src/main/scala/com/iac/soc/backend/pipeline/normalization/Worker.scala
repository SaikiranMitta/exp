package com.iac.soc.backend.pipeline.normalization

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.regex.Pattern
import java.util.{Date, TimeZone}

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.util.Timeout
import com.iac.soc.backend.pipeline.messages._
import com.iac.soc.backend.pipeline.models.{Normalizer => NormalizerModel}
import com.iac.soc.backend.schemas._
import com.typesafe.scalalogging.LazyLogging
import io.krakens.grok.api.GrokCompiler
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.proxy.ProxyObject
import scalapb.json4s.JsonFormat

import scala.concurrent.duration._

/**
  * The companion object to the normalization worker actor
  */
object Worker {

  /**
    * Creates the configuration for the normalization worker actor
    *
    * @param normalization the normalization sub-system reference
    * @param enrichment    the enrichment sub-system reference
    * @return the configuration for the normalization worker actor
    */
  def props(normalization: ActorRef, enrichment: ActorRef /*, graalvmContext: Context*/ , allNormalizers: Map[Int, List[Normalizer]]) = Props(new Worker(normalization, enrichment /*, graalvmContext*/ , allNormalizers))

  /**
    * The name of the normalization worker actor
    *
    * @return the name of the normalization worker actor
    */
  def name = "worker"

}

/**
  * The normalization worker actor's class
  *
  * @param normalization the normalization sub-system reference
  * @param enrichment    the enrichment sub-system reference
  */
class Worker(normalization: ActorRef, enrichment: ActorRef /*, graalvmContext: Context*/ , allNormalizers: Map[Int, List[Normalizer]]) extends Actor with LazyLogging {

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val timeout: Timeout = Timeout(60 seconds)

  /**
    * The normalizers defined in the system, grouped by ingestor ids
    */
  private[this] var normalizers: Map[Int, Vector[NormalizerModel]] = _

  /**
    * The normalizers defined in the system, grouped by ingestor ids
    */
  private[this] var graalvmContext: Context = _

  /**
    * Sets the system normalizes from the normalizers message
    */
  private[this] def setNormalizers(): Unit = {

    logger.info("Getting normalizers defined in the system")

    // Get all the defined normalizers
    //val future = normalization ? GetNormalizers

    // Get the result of the ask query
    //val normalizersGroupedByIngestorId = Await.result(future, 5 seconds).asInstanceOf[NormalizersGroupedByIngestorId]

    // Transform to normalizer model grouped by ingestor id
    /*normalizers =
      normalizersGroupedByIngestorId
        .normalizers
        .map(ingestor => {
          (ingestor._1, ingestor._2.value.map(n => NormalizerModel(n.id, n.schema, n.ingestorId, n.pattern, n.processor)).toVector)
        })*/
  }

  override def supervisorStrategy = OneForOneStrategy() {

    case e => {
      e.printStackTrace()
      logger.error(s"Normalization Worker Supervision from Worker :: ${e.toString}")
      Restart

    }

  }

  private[this] def initializeContextWithMapper(): Context = {

    graalvmContext = Context.newBuilder("js").allowIO(true).build

    //Include lodash
    graalvmContext.eval("js", "load('classpath:normalizer_scripts/lodash.min.js');")

    //Include node-json-transform
    graalvmContext.eval("js", "load('classpath:normalizer_scripts/node-json-transform.js');")

    //Include csvtojson
    graalvmContext.eval("js", "load('classpath:normalizer_scripts/csvtojson.min.js');")

    //Include moment
    graalvmContext.eval("js", "load('classpath:normalizer_scripts/moment.min.js');")

    graalvmContext
  }

  private[this] def isValidTimestamp(timestamp: String): Boolean = {

    try {

      Timestamp.from(Instant.parse(timestamp))
      true
    }
    catch {
      case _: Exception => false
    }
  }


  private[this] def parse(ingestedLog: IngestedLog, matchedNormalizer: Normalizer, status: String = "success"): String = {

    val grokCompiler: GrokCompiler = GrokCompiler.newInstance()
    grokCompiler.registerDefaultPatterns()

    val bindings = graalvmContext.getBindings("js")

    var mappedData = ""

    if (matchedNormalizer != null) {

      graalvmContext.eval("js", matchedNormalizer.mapping)

      if (matchedNormalizer.grokPattern != null && matchedNormalizer.grokPattern != "") {

        val grok = grokCompiler.compile(matchedNormalizer.grokPattern)
        val gm = grok.`match`(ingestedLog.log)

        /* Get the map with matches. Convert this to JSON object and pass to mapper*/
        val capture = gm.capture

        if (capture != null) {

          logger.info(s"Grok parser success for log ${ingestedLog.id} with ingestor ${ingestedLog.ingestorId} : ${capture}")

          bindings.putMember("log", ProxyObject.fromMap(capture))
          bindings.putMember("rawLog", ingestedLog.log)
          mappedData = graalvmContext.eval("js", "module(log, rawLog, 'success')").toString

        } else {

          logger.info(s"Grok parser failed for log ${ingestedLog.id} with ingestor ${ingestedLog.ingestorId}")
          bindings.putMember("log", ingestedLog.log)
          mappedData = graalvmContext.eval("js", "module(log, 'grokparsefail')").toString

        }

      } else {

        logger.info(s"No Grok pattern for log ${ingestedLog.id} with ingestor ${ingestedLog.ingestorId}")

        bindings.putMember("log", ingestedLog.log)
        mappedData = graalvmContext.eval("js", "module(log, 'success')").toString

      }

    } else {

      logger.info(s"No normalizer found for log ${ingestedLog.id} with ingestor id ${ingestedLog.ingestorId}")

    }

    mappedData

  }

  private[this] def normalize(ingestedLogs: IngestedLogs): Seq[Log] = {

    var normalizerLogs: Seq[Log] = Seq.empty[Log]

    try {

      logger.info(s"Ingested Logs count ${ingestedLogs.value.size}")

      ingestedLogs.value.map { ingestedLog =>

        var matchedNormalizer: Normalizer = null

        try {

          //logger.info(s"Normalizing log: ${ingestedLog}")

          val normalizers = allNormalizers(ingestedLog.ingestorId)

          logger.info(s"Normalizer ${normalizers.size} found for ingestors ${ingestedLog.ingestorId}")
          logger.info("Validate Patterns : ")

          normalizers.map(p => {
            logger.info(s"${p.filters} : ${Pattern.compile(p.filters).matcher(ingestedLog.log).find()}")
          })

          // Get the matching normalizer
          matchedNormalizer = normalizers.find(n => Pattern.compile(n.filters).matcher(ingestedLog.log).find()).orNull

          logger.info(s"Matched Filter for log ${ingestedLog.id} : ${matchedNormalizer}")

          val mappedData = parse(ingestedLog, matchedNormalizer)

          if (mappedData != "" && mappedData.nonEmpty) {
            val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))
            normalizerLogs = normalizerLogs :+ normalizerLogsTemp
          }

        } catch {

          case ex: Exception => {

            ex.printStackTrace()
            logger.error(s"Exception occurred in normalizing log ${ingestedLog.id} ${ex}")

            if (matchedNormalizer != null) {

              logger.info("Received Log : " + ingestedLog.log)
              logger.info("Matched Grok : " + matchedNormalizer.grokPattern)

              val mappedData = parse(ingestedLog, matchedNormalizer)

              if (mappedData != "" && mappedData.nonEmpty) {
                val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))
                normalizerLogs = normalizerLogs :+ normalizerLogsTemp
              }

            } else {

              logger.info(s"Fetching global normalizer for ingestor ${ingestedLog.ingestorId}")
              matchedNormalizer = allNormalizers(ingestedLog.ingestorId).find(_.isGlobal == true).orNull

              val mappedData = parse(ingestedLog, matchedNormalizer)
              logger.info(s"mappedData :: ${mappedData}")

              if (mappedData != "" && mappedData.nonEmpty) {
                val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))
                normalizerLogs = normalizerLogs :+ normalizerLogsTemp
              }

            }

          }

        }

      }

    } catch {

      case ex: Exception => {

        logger.error(s"Exception in Normalization ${ex}")

      }

    }

    normalizerLogs
  }

  private[this] def normalizeSingleLog(ingestedLog: IngestedLog): Log = {

    var normalizedLog: Log = null

    val today = Date.from(Instant.parse(new Date().toInstant.toString))
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

    try {

      var matchedNormalizer: Normalizer = null

      try {

        val normalizers = allNormalizers(ingestedLog.ingestorId)

        // Get the matching normalizer
        matchedNormalizer = normalizers.find(n => Pattern.compile(n.filters).matcher(ingestedLog.log).find()).orNull

        logger.info(s"Matched Filter for log ${ingestedLog.id} : ${matchedNormalizer}")

        val mappedData = parse(ingestedLog, matchedNormalizer)

        if (mappedData != "" && mappedData.nonEmpty) {

          val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))

          if (!isValidTimestamp(normalizerLogsTemp.timestamp.get)) {

            logger.warn(s"Invalid timestamp for log ${normalizerLogsTemp.id} timestamp ${normalizerLogsTemp.timestamp}")

            normalizerLogsTemp.copy(timestamp = Some(formatter.format(today)))

          }

          normalizedLog = normalizerLogsTemp

        } else {

          Repository.convertSingleIngestedLogToUnmappedLog(ingestedLog)

        }

      } catch {

        case ex: Exception => {

          ex.printStackTrace()
          logger.error(s"Exception occurred in normalizing log ${ingestedLog.id} ${ex}")

          if (matchedNormalizer != null) {

            logger.info("Exception occurred Received Log : " + ingestedLog.log)
            logger.info("Exception occurred Matched Grok : " + matchedNormalizer.grokPattern)

            val mappedData = parse(ingestedLog, matchedNormalizer)

            if (mappedData != "" && mappedData.nonEmpty) {

              val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))

              if (!isValidTimestamp(normalizerLogsTemp.timestamp.get)) {

                logger.warn(s"Invalid timestamp for log ${normalizerLogsTemp.id} timestamp ${normalizerLogsTemp.timestamp}")

                normalizerLogsTemp.copy(timestamp = Some(formatter.format(today)))

              }

              normalizedLog = normalizerLogsTemp
            }

          } else {

            logger.info(s"Fetching global normalizer for ingestor ${ingestedLog.ingestorId}")
            matchedNormalizer = allNormalizers(ingestedLog.ingestorId).find(_.isGlobal).orNull

            val mappedData = parse(ingestedLog, matchedNormalizer)

            if (mappedData != "" && mappedData.nonEmpty) {

              val normalizerLogsTemp = JsonFormat.fromJsonString[Log](mappedData).copy(id = Some(ingestedLog.id))

              if (!isValidTimestamp(normalizerLogsTemp.timestamp.get)) {

                logger.warn(s"Invalid timestamp for log ${normalizerLogsTemp.id} timestamp ${normalizerLogsTemp.timestamp}")

                normalizerLogsTemp.copy(timestamp = Some(formatter.format(today)))

              }

              normalizedLog = normalizerLogsTemp
            }

          }

        }

      }

    } catch {

      case ex: Exception => {

        logger.error(s"Exception in Normalization ${ex}")

      }

    }

    normalizedLog
  }

  /**
    * Hook into just before normalization worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started normalization worker actor")

    // initialize graalvm context
    initializeContextWithMapper()

    // Set the normalizers
    setNormalizers()
  }

  /**
    * Handles incoming messages to the normalization worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Normalize ingested logs
    case ingestedLogs: IngestedLogs => {

      logger.info(s"Normalizer worker received ingested logs: ${ingestedLogs}")

      var normalized: Seq[Log] = normalize(ingestedLogs)

      logger.info(s"Normalized logs results : ${normalized}")

      if (normalized.size < 1)
        normalized = Repository.convertIngestedLogToUnmappedLog(ingestedLogs)

      sender() ! normalized

    }

    // Normalize ingested log
    case ingestedLog: IngestedLog => {

      logger.info(s"Normalizer worker received ingested log: ${ingestedLog}")

      var normalized: Log = normalizeSingleLog(ingestedLog)

      logger.info(s"Normalized logs results : ${normalized}")

      if (normalized == null)
        normalized = Repository.convertSingleIngestedLogToUnmappedLog(ingestedLog)

      sender() ! normalized

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")

  }

}
