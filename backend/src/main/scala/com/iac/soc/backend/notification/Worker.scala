package com.iac.soc.backend.notification

import akka.actor.{Actor, Props}
import com.iac.soc.backend.notification.messages.SendEmail
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to the notification worker actor
  */
private[notification] object Worker {

  /**
    * Creates the configuration for the notification worker actor
    */
  def props = Props(new Worker())

  /**
    * The name of the notification worker actor
    *
    * @return the name of the notification worker actor
    */
  def name = "worker"
}

private[notification] class Worker() extends Actor with LazyLogging {

  /**
    * Hook into just before notification worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started notification worker actor")
  }

  /**
    * Handles incoming messages to the notification worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Handle the send email message
    case SendEmail(to, subject, body) => MailAgent.sendEmail(to, subject, body)

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
