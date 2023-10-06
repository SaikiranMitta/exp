package com.iac.soc.backend.pipeline.log_producer

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Date, TimeZone}

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{Producer => AkkaKafkaProducer}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.google.protobuf.any.Any
import com.iac.soc.backend.schemas.Log
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The companion object to the producer worker actor
  */
object Worker {

  /**
    * Creates the configuration for the producer worker actor
    *
    * @param manager the manager sub-system reference
    * @return the configuration for the producer worker actor
    */

  def props(manager: ActorRef /*, producerSettings: ProducerSettings[String, Array[Byte]] , kafkaProducer: Producer[String, Array[Byte]], topic: String*/) = Props(new Worker(manager /*, producerSettings , kafkaProducer, topic*/))

  /**
    * The name of the producer worker actor
    *
    * @return the name of the producer worker actor
    */

  def name = "worker"

}

class Worker(manager: ActorRef /*, producerSettings: ProducerSettings[String, Array[Byte]] , kafkaProducer: Producer[String, Array[Byte]], topic: String*/) extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * Prodcuer instance
    */
  implicit var kafkaProducer: Producer[String, Array[Byte]] = _

  /**
    * Producer settings
    */
  implicit var producerSettings: ProducerSettings[String, Array[Byte]] = _

  /**
    * The default timeout for the producer worker actor
    */
  implicit val timeout: Timeout = Timeout(5 seconds)

  private[this] def publishToKafka(log: Log): Future[Done] = {

    val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")

    val kafkaTopic = producerConfig.getString("log-topic")

    try {

      val decider: Supervision.Decider = {

        case ex: Exception => {
          logger.error(s"Logs Production ${ex.toString}")
          ex.printStackTrace()
          logger.info("Resuming Logs Production Supervision ")
          Supervision.resume
        }

      }

      implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

      val todayDate = Date.from(Instant.parse(new Date().toInstant.toString))
      val todayDateFormatter = new SimpleDateFormat("yyyy-MM-dd")
      todayDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))

      val todayTopic = kafkaTopic + "-" + todayDateFormatter.format(todayDate)

      logger.info(s"Publishing log ${log.id} to kafka topic : ${todayTopic} ")

      if (log.timestamp != None && log.timestamp.get != "" && log.timestamp.get != null && log.organization != None && log.organization.get != null && log.organization.get != "") {

        val done: Future[Done] = Source(Vector(log))
          .map(value => new ProducerRecord(todayTopic, value.id.get, value.toByteArray))
          .runWith(AkkaKafkaProducer.plainSink(producerSettings, kafkaProducer))

      }

    } catch {

      case ex: Exception => {

        ex.printStackTrace()
        logger.error(s"Failed to Produce log : ${log.id} to kafka topic ${kafkaTopic}")

      }

    }

    Future.successful(Done)

  }

  /**
    * Hook into just before producer worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started log producer worker actor")

    val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")
    val bootstrapServers = producerConfig.getString("bootstrap-servers")

    producerSettings =
      ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
        .withBootstrapServers(bootstrapServers)

    kafkaProducer = producerSettings.createKafkaProducer()

  }

  /**
    * Handles incoming messages to the producer worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Produce to kafka
    case log: Log => {

      logger.info(s"Received log: ${log} ")

      val produced = publishToKafka(log).mapTo[Done]

      sender() ! produced
    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
