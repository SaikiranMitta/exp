package com.iac.soc.backend.pipeline

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.pipeline.enrichment.{Manager => EnrichmentManager}
import com.iac.soc.backend.pipeline.ingestion.{Manager => IngestionManager}
import com.iac.soc.backend.pipeline.normalization.{Manager => NormalizationManager}
import com.iac.soc.backend.pipeline.producer.{Manager => ProducerManager}
import com.iac.soc.backend.pipeline.log_producer.{Manager => LogProducerManager}
import com.iac.soc.backend.pipeline.consumer.{Manager => ConsumerManager}
import com.iac.soc.backend.pipeline.store.{Manager => LogManager}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to pipeline manager's actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the pipeline manager actor
    *
    * @param backend     the backend system reference
    * @param log         the log sub-system reference
    * @param geolocation the geolocation sub-system reference
    * @param threat      the threat intel sub-system reference
    * @return the configuration for the pipeline manager actor
    */
  def props(backend: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef) = Props(new Manager(backend, log, geolocation, threat))

  /**
    * The name of the pipeline manager actor
    *
    * @return the name of the pipeline manager actor
    */
  def name = "pipeline"
}

/**
  * The pipeline manager actor's class
  *
  * @param backend     the backend system reference
  * @param log         the log sub-system reference
  * @param geolocation the gelocation sub-system reference
  * @param threat      the threat intel sub-system reference
  */
private[backend] class Manager(backend: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef) extends Actor with LazyLogging {

  /**
    * The ingestion component reference
    */
  private[this] var ingestion: ActorRef = _

  /**
    * The normalization component reference
    */
  private[this] var normalization: ActorRef = _

  /**
    * The enrichment component reference
    */
  private[this] var enrichment: ActorRef = _

  /**
    * The producer component reference
    */
  private[this] var producer: ActorRef = _

  /**
    * The producer component reference
    */
  private[this] var consumer: ActorRef = _

  /**
    * The producer component reference
    */
  private[this] var logWriter: ActorRef = _

  /**
    * The producer component reference
    */
  private[this] var logProducer: ActorRef = _


  private[this] def setupIngestion(normalization: ActorRef): ActorRef = {

    logger.info("Starting ingestion component")

    // Start the ingestion sub-system actor
    context.actorOf(
      IngestionManager.props(self, normalization),
      IngestionManager.name
    )
  }

  private[this] def setupNormalization(enrichment: ActorRef): ActorRef = {

    logger.info("Starting normalization component")

    // Start the normalization sub-system actor
    context.actorOf(
      NormalizationManager.props(self, enrichment),
      NormalizationManager.name
    )
  }

  private[this] def setupEnrichment(): ActorRef = {

    logger.info("Starting enrichment component")

    // Start the enrichment sub-system actor
    context.actorOf(
      EnrichmentManager.props(self, log, geolocation, threat),
      EnrichmentManager.name
    )
  }

  private[this] def setupRawLogProducer(): ActorRef = {

    logger.info("Starting raw log producer component")

    // Start the producer sub-system actor
    context.actorOf(
      ProducerManager.props(self),
      ProducerManager.name
    )

  }

  private[this] def setupLogProducer(): ActorRef = {

    logger.info("Starting log producer component")

    // Start the producer sub-system actor
    context.actorOf(
      LogProducerManager.props(self),
      LogProducerManager.name
    )

  }

  private[this] def setupConsumer(normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef): ActorRef = {

    logger.info("Starting consumer component")

    // Start the consumer sub-system actor
    context.actorOf(
      ConsumerManager.props(self, normalization, enrichment, logWriter, logProducer),
      ConsumerManager.name
    )

  }

  private[this] def setupLogWriter(): ActorRef = {

    logger.info("Starting log writer component")

    // Start the consumer sub-system actor
    context.actorOf(
      LogManager.props(),
      LogManager.name
    )

  }

  /**
    * Hook into just before pipeline manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started pipeline sub-system")

    logger.info("Starting all components")

    val main_config = ConfigFactory.load()

    val setupConsumers = main_config.getBoolean("setup.consumers")

    // Start all the components in the right order
    enrichment = setupEnrichment()
    producer = setupRawLogProducer()
    logProducer = setupLogProducer()
    normalization = setupNormalization(enrichment)
    logWriter = setupLogWriter()
    if (setupConsumers == true) {
      consumer = setupConsumer(normalization, enrichment, logWriter, logProducer)
    }
    ingestion = setupIngestion(producer)
  }

  /**
    * Handles incoming messages to the backend manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
