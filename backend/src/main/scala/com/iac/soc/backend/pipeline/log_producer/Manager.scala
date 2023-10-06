package com.iac.soc.backend.pipeline.log_producer

import akka.Done
import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.kafka.ProducerSettings
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.iac.soc.backend.schemas.Log
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * The companion object to the log producer manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the producer manager actor
    *
    * @return the configuration for the producer manager actor
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the producer manager actor
    *
    * @return the name of the producer manager actor
    */
  def name = "log-producer"

}

/**
  * The producer manager actor's class
  *
  */
private[backend] class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * The producer router reference
    */
  private[this] var router: ActorRef = _

  /**
    * The default timeout for the enrichment worker actor
    */

  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the consumer worker actor
    */
  implicit val timeout: Timeout = Timeout(5 seconds)

  /**
    * Sets up the producer workers
    */
  private[this] def setupWorkers(): Unit = {

    logger.info("Setting up log producer router and routees")

    /*val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")
    val bootstrapServers = producerConfig.getString("bootstrap-servers")
    val kafkaTopic = producerConfig.getString("log-topic")

    val producerSettings =
      ProducerSettings(producerConfig, new StringSerializer, new ByteArraySerializer)
        .withBootstrapServers(bootstrapServers)

    //val kafkaProducer = producerSettings.createKafkaProducer()
    */

    // Create the router and routees to produce raw log to kafka
    router =
      context.actorOf(
        FromConfig.props(Worker.props(self /*, producerSettings, kafkaProducer, kafkaTopic*/)),
        name = "router"
      )
  }

  /**
    * Hook into just before producer manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started log producer sub-system")

    // Setup the producer workers
    setupWorkers()
  }

  override def supervisorStrategy = OneForOneStrategy() {

    case e => {

      logger.error(s"Producer Worker Supervision from log manager :: ${e}")
      Resume

    }

  }

  /**
    * Handles incoming messages to the producer manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send logs to producer workers
    //case log: Endpoint => router ! log

    case log: Log => {

      logger.info(s"Sending received log: ${log} to log producer worker")

      router forward log

      /*val produced: Future[Done] = (router ? log).mapTo[Done]

      sender() ! produced*/
    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }

}
