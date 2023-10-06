package com.iac.soc.backend.pipeline.store

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import com.iac.soc.backend.schemas.{Log, Logs}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * The companion object to the enrichment manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the enrichment manager actor
    *
    * @return the configuration for the enrichment manager actor
    */
  def props() = Props(new Manager())

  /**
    * The name of the enrichment manager actor
    *
    * @return the name of the enrichment manager actor
    */
  def name = "store-manager"
}

/**
  * The enrichment manager actor's class
  *
  */
private[backend] class Manager() extends Actor with LazyLogging {

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

    logger.info("Setting up log router and routees")

    // Create the router and routees for doing the enrichment
    router =
      context.actorOf(
        FromConfig.props(Worker.props()),
        name = "router"
      )
  }

  /**
    * Hook into just before enrichment manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started log insertion sub-system")

    // Setup the log writer workers
    setupWorkers()
  }

  /**
    * Handles incoming messages to the enrichment manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send the logs to writer workers
    case logs: Logs => {

      logger.info(s"Sending received enriched logs to log writer worker")

      router forward logs

    }

    // Send the log to writer workers
    case log: Log => {

      logger.info(s"Sending received enriched logs to log writer worker")

      router forward log

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")

  }

}
