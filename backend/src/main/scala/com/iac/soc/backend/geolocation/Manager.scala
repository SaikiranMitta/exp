package com.iac.soc.backend.geolocation

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.geolocation.messages.{GetIpGeolocation, IpGeolocation}
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to the geolocation manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the geolocation manager actor
    *
    * @param backend the backend system reference
    * @return the configuration for the geolocation manager actor
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the geolocation manager actor
    *
    * @return the name of the geolocation manager actor
    */
  def name = "geolocation"
}

/**
  * The geolocation manager actor's class
  *
  * @param backend the backend sub-system reference
  */
private[backend] class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * Hook into just before geolocation manager actor is started for any initialization
    */
  override def preStart(): Unit = {
    logger.info("Started geolocation sub-system")
  }

  /**
    * Handles incoming messages to the geolocation manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Handle requests for getting ip geolocation details
    case GetIpGeolocation(ip) => {

      logger.info(s"Received request to fetch ip geolocation for ip: ${ip}")

      // Get the ip geolocation details from the repository
      val model = Repository.getIpGeolocation(ip)

      // Send the ip geolocation details to the requester
      sender() ! IpGeolocation(model.ip, model.latitude, model.longitude, model.city, model.country)
    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
