package com.iac.soc.backend.pipeline.consumer

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.kafka.ConsumerSettings
import akka.routing.FromConfig
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

/**
  * The companion object to the raw log consumer manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the consumer manager actor
    *
    * @return the configuration for the consumer manager actor
    */
  def props(backend: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef) = Props(new Manager(backend, normalization, enrichment, logWriter, logProducer))

  /**
    * The name of the consumer manager actor
    *
    * @return the name of the consumer manager actor
    */
  def name = "consumer"

}

/**
  * The consumer manager actor's class
  *
  */
private[backend] class Manager(backend: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef) extends Actor with LazyLogging {

  /**
    * The consumer router reference
    */
  private[this] var router: ActorRef = _

  /** parallelism = 100
    * Sets up the consumer workers
    */
  private[this] def setupWorkers(): Unit = {

    /*val consumerConfig = context.system.settings.config.getConfig("akka.kafka.consumer")

    val bootstrapServers = consumerConfig.getString("bootstrap-servers")

    val consumerGroup = consumerConfig.getString("raw-log-consumer-group")

    val autoOffsetResetConfig = consumerConfig.getString("auto-offset-reset-config")

    val kafkaTopic = consumerConfig.getString("raw-log-topic")

    logger.info(s" Reading from Topic ${kafkaTopic} with autoOffset property as ${autoOffsetResetConfig}")

    val consumerSettings =
      ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
        .withBootstrapServers(bootstrapServers)
        .withGroupId(consumerGroup)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig)*/

    /*logger.info("Setting up raw log consumer router and routees")

    // Create the router and routees to consume raw log from kafka
    router =
      context.actorOf(
        FromConfig.props(Worker.props(self, normalization, enrichment, logWriter, logProducer /*, consumerSettings, kafkaTopic*/)),
        name = "router"
      )*/

    logger.info("Setting up camel consumer worker")

    // Create camel kafka consumer worker
    context.actorOf(
      CamelWorker.props(self, normalization, enrichment, logWriter, logProducer),
      CamelWorker.name
    )
  }

  override def supervisorStrategy = OneForOneStrategy() {

    case e => {

      logger.error(s"Consumer Worker Supervision from manager :: ${e}")
      Resume

    }

  }

  /**
    * Hook into just before consumer manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started raw log consumer sub-system")

    // Setup the consumer workers
    setupWorkers()

  }

  /**
    * Handles incoming messages to the consumer manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
