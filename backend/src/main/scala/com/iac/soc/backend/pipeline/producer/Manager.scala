package com.iac.soc.backend.pipeline.producer

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.kafka.ProducerSettings
import akka.routing.FromConfig
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

/**
  * The companion object to the raw log producer manager actor
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
  def name = "producer"

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
    * Sets up the producer workers
    */
  private[this] def setupWorkers(): Unit = {

    logger.info("Setting up raw log producer router and routees")

    // Create the router and routees to produce raw log to kafka

    router =
      context.actorOf(
        FromConfig.props(Worker.props(self /*, producerSettings , kafkaProducer, kafkaTopic*/)),
        name = "router"
      )
  }

  override def supervisorStrategy = OneForOneStrategy() {

    case e => {

      logger.error(s"Producer Worker Supervision from ingested log manager :: ${e}")
      Resume

    }

  }

  /**
    * Hook into just before producer manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started raw log producer sub-system")

    // Setup the producer workers
    setupWorkers()
  }

  /**
    * Handles incoming messages to the producer manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send raw logs to producer workers
    case log: IngestedLog => router forward log

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
