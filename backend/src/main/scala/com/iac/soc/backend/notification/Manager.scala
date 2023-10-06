package com.iac.soc.backend.notification

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.FromConfig
import com.iac.soc.backend.notification.messages.SendEmail
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to the notification manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the notification manager actor
    *
    * @param backend the backend system referencer
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the notification manager actor
    *
    * @return the name of the notification manager actor
    */
  def name = "notification"
}

private[backend] class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * The enrichment router reference
    */
  private[this] var router: ActorRef = _

  /**
    * Sets up the enrichment workers
    */
  private[this] def setupWorkers(): Unit = {

    logger.info("Setting up enrichment router and routees")

    // Create the router and routees for doing the enrichment
    router =
      context.actorOf(
        FromConfig.props(Worker.props),
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

    // Handle send email message
    case email: SendEmail => router ! email

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
