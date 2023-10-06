package com.iac.soc.backend.pipeline.enrichment

import java.io.File

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.iac.soc.backend.schemas.{Log, Logs}
import com.maxmind.db.Reader
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * The companion object to the enrichment manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the enrichment manager actor
    *
    * @param backend     the backend system reference
    * @param log         the log sub-system reference
    * @param geolocation the geolocation sub-system reference
    * @param threat      the threat sub-system reference
    * @return the configuration for the enrichment manager actor
    */
  def props(backend: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef) = Props(new Manager(backend, log, geolocation, threat))

  /**
    * The name of the enrichment manager actor
    *
    * @return the name of the enrichment manager actor
    */
  def name = "enrichment"
}

/**
  * The enrichment manager actor's class
  *
  * @param backend     the backend system reference
  * @param log         the log sub-system reference
  * @param geolocation the geolocation sub-system reference
  * @param threat      the threat sub-system reference
  */
private[backend] class Manager(backend: ActorRef, log: ActorRef, geolocation: ActorRef, threat: ActorRef) extends Actor with LazyLogging {

  /**
    * The enrichment router reference
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
    * Sets up the enrichment workers
    */
  private[this] def setupWorkers(): Unit = {

    logger.info("Setting up enrichment router and routees")

    // Create the router and routees for doing the enrichment
    router =
      context.actorOf(
        FromConfig.props(Worker.props(self, log, geolocation, threat /*, database*/)),
        name = "router"
      )
  }

  /**
    * Hook into just before enrichment manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started enrichment sub-system")

    // Setup the enrichment workers
    setupWorkers()
  }

  /**
    * Handles incoming messages to the enrichment manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send the logs to enrichment workers
    case logs: Logs => {

      logger.info(s"Sending received normalized log: ${logs} to enrichment worker")

      router forward logs

    }

    // Send the log to enrichment workers
    case log: Log => {

      logger.info(s"Sending received normalized log: ${log} to enrichment worker")

      router forward log

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
