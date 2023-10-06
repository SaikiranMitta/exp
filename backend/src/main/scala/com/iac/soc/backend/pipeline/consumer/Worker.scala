package com.iac.soc.backend.pipeline.consumer

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Date, TimeZone}

import akka.actor.{Actor, ActorRef, Props}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerMessage, ConsumerSettings, Subscriptions}
import akka.pattern.ask
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.util.Timeout
import com.google.protobuf.any.Any
import com.iac.soc.backend.pipeline.messages.{IngestedLog, IngestedLogs}
import com.iac.soc.backend.schemas.{Log, Logs}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * The companion object to the consumer worker actor
  */
object Worker {

  /**
    * Creates the configuration for the consumer worker actor
    *
    * @param manager the manager sub-system reference
    * @return the configuration for the consumer worker actor
    */

  def props(manager: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef /*, consumerSettings: ConsumerSettings[String, Array[Byte]], topic: String*/) = Props(new Worker(manager, normalization, enrichment, logWriter,
    logProducer /*, consumerSettings, topic*/))

  /**
    * The name of the consumer worker actor
    *
    * @return the name of the consumer worker actor
    */

  def name = "consumer-worker"

}

class Worker(manager: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef /*,consumerSettings: ConsumerSettings[String, Array[Byte]], topic: String*/) extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the consumer worker actor
    */
  implicit val timeout: Timeout = Timeout(300 seconds)

  /**
    * The consumer settings
    */
  implicit var consumerSettings: ConsumerSettings[String, Array[Byte]] = _

  /**
    * Execute Normalizers, Enrichment and Kudu Insertion
    *
    * @param record
    * @return
    */
  private[this] def pipelineExecutor(record: Seq[IngestedLog]): Seq[Log] = {

    try {

      logger.info("Received a batch of logs for processing")

      val results: Future[Seq[Log]] = for {

        normalized <- (normalization ? IngestedLogs(record)).mapTo[Seq[Log]]

        //enriched <- (enrichment ? Logs(convertIngestedLogToUnmappedLog(IngestedLogs(record)).map(logs => logs))).mapTo[Seq[Log]]

        // enriched <- (enrichment ? Logs(normalized)).mapTo[Seq[Log]]

        inserted <- (logWriter ? Logs(normalized)).mapTo[Seq[Log]]

      } yield inserted

      /*results.onComplete {
        case Success(result) => result
        case Failure(ex) => {
          ex.printStackTrace()
          logger.error(s"Failed to pass log through pipeline ${ex}")
        }
      }*/

      Await.result(results, 5 minutes)

    } catch {

      case ex: Exception => {

        ex.printStackTrace()

        logger.error(s"Failed to pass log ${record.map(_.id)} through pipeline ${ex.toString}")

        convertIngestedLogToUnmappedLog(IngestedLogs(record))

      }

    }

  }

  def convertIngestedLogToUnmappedLog(ingestedLogs: IngestedLogs): Seq[Log] = {

    val today = Date.from(Instant.parse(new Date().toInstant.toString))
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
    formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

    val normalized = ingestedLogs.value.map(ingestedLog => {

      Log(id = Some(ingestedLog.id), timestamp = Some(formatter.format(today)), sourceip = Some("14.141.151.78"), destinationip = Some("52.230.222.68"), destinationhost = Some("LAV-DHCP.iac.corp"), sourcehost = Some("NYV-DHCP"), signature = Some(""), resource = Some(""), sha1 = Some(""), organization = Some("Unknown"), `type` = Some("Unmapped"), message = Some(ingestedLog.log), date = Some(dateFormatter.format(today)))

      //Log(id = Some(ingestedLog.id), timestamp = Some("2019/07/02 05:25:12"), organization = Some("Unknown"), `type` = Some("Unmapped"), message = Some(ingestedLog.log), date = Some(dateFormatter.format(today)))

    })

    normalized

  }

  /**
    * Produce Data to kafka topic
    *
    * @param committableOffsets
    * @param records
    * @return
    */
  private[this] def produceToKafka(committableOffsets: Seq[ConsumerMessage.CommittableMessage[String, Array[Byte]]], records: Seq[Log]): Seq[ConsumerMessage.CommittableOffset] = {

    try {

      logger.info(s"Producing logs ${committableOffsets.map(_.record.key())} to log topic :: ")

      if (records != null && records.size > 0) {

        records.map { record =>

          logger.info(s"Sending log ${record.id} to kafka log topic")
          val produced = logProducer ? record

          Await.result(produced, 5 minutes)
          logger.info(s"Successfully published log ${record.id} to kafka log topic")

        }
        logger.info(s"Commit offsets :: ${committableOffsets.map(_.record.key())}")

        committableOffsets.map(_.committableOffset)

      } else {

        logger.info("Empty vector, logs not produced to kafka topic ")

        committableOffsets.map(_.committableOffset)
      }

    } catch {

      case ex: Exception => {
        ex.printStackTrace()
        logger.error(s"Failed to produce log ${records.map(_.id)} to log topic ${ex.toString}")
        Seq.empty[ConsumerMessage.CommittableOffset]

      }

    }

  }

  private[this] def setupConsumer(consumerSettings: ConsumerSettings[String, Array[Byte]]): Unit = {

    val consumerConfig = context.system.settings.config.getConfig("akka.kafka.consumer")
    val kafkaTopic = consumerConfig.getString("raw-log-topic")

    logger.info(s"Consume log from kafka topic ${kafkaTopic} ")

    val decider: Supervision.Decider = {

      case ex: Exception => {
        logger.error(s"Stream Failed ${ex.toString}")
        ex.printStackTrace()
        logger.info("Resuming stream using Supervision ")
        Supervision.resume
      }

    }

    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

    val stage_1_group_count = consumerConfig.getInt("stage-1-group-count")
    val stage_1_parallelism = consumerConfig.getInt("stage-1-parallelism")

    logger.info(s"Consumer stage 1 group count : ${stage_1_group_count}")
    logger.info(s"Consumer stage 1 parallelism : ${stage_1_parallelism}")

    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(kafkaTopic))
      .grouped(stage_1_group_count)
      .mapAsync(stage_1_parallelism) { records =>

        logger.info("Starting Consumer Pipeline")
        val results = pipelineExecutor(records.map { log => Any.parseFrom(log.record.value()).unpack(IngestedLog) })

        logger.info(s"Pipeline completed for log ${results.map(_.id)}")

        if (results != null && results.size > 0)
          Future(records, results.map(logs => logs))
        else
          Future(records, null)

      }
      .map(records => {

        logger.info(s"Sending batch to log producer: ")

        if (records._2 != null)
          produceToKafka(records._1, records._2)
        else
          produceToKafka(records._1, null)

      })
      .map(_.last.commitScaladsl())
      .runWith(Sink.ignore)
  }

  /**
    * Hook into just before consumer worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started consumer worker actor")

    val consumerConfig = context.system.settings.config.getConfig("akka.kafka.consumer")
    val bootstrapServers = consumerConfig.getString("bootstrap-servers")
    val consumerGroup = consumerConfig.getString("raw-log-consumer-group")
    val autoOffsetResetConfig = consumerConfig.getString("auto-offset-reset-config")

    val consumerSettings =
      ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId(consumerGroup)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)
        .withProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Integer.MAX_VALUE.toString)
        .withProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "10000")
        .withProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "60000")

    setupConsumer(consumerSettings)

  }

  /**
    * Handles incoming messages to the consumer worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
