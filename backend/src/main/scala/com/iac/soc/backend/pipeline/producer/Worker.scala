package com.iac.soc.backend.pipeline.producer

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{Producer => AkkaKafkaProducer}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import com.google.protobuf.any.Any
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{Producer, ProducerRecord}
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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

  private[this] def publishToKafka(log: IngestedLog): Unit = {

    try {

      //logger.info(s"Publishing log ${log.id} to kafka: ")

      val decider: Supervision.Decider = {

        case ex: Exception => {
          logger.error(s"Raw Logs Production Failed ${ex.toString}")
          ex.printStackTrace()
          logger.info("Resuming Raw Logs Production using Supervision ")
          Supervision.resume
        }

      }

      implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system).withSupervisionStrategy(decider))

      val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")

      val kafkaTopic = producerConfig.getString("raw-log-topic")

      val done: Future[Done] = Source(Vector(log))
        .map(value => new ProducerRecord(kafkaTopic, value.id, Any.pack(value).toByteArray))
        .runWith(AkkaKafkaProducer.plainSink(producerSettings, kafkaProducer))

      done.onComplete {

        case Failure(e) =>
          logger.error(s"Failed to publish log ${log.id} to kafka topic ${kafkaTopic}")

        case Success(_) =>
          //logger.info(s"Successfully published log ${log.id} to kafka topic ${kafkaTopic}")

      }

    } catch {

      case ex: Exception => {

        ex.printStackTrace()
        logger.error(s"Exception in producing ingested log : ${ex}")

      }

    }

  }

  /**
    * Hook into just before producer worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")

    val bootstrapServers = producerConfig.getString("bootstrap-servers")

    producerSettings =
      ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
        .withBootstrapServers(bootstrapServers)

    kafkaProducer = producerSettings.createKafkaProducer()

    logger.info("Started producer worker actor")
  }

  /**
    * Handles incoming messages to the producer worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Produce to kafka
    case log: IngestedLog => publishToKafka(log)

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
