package com.iac.soc.backend.log

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.log.messages.{GetLogByOrganization, LogByOrganization}
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to the log manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the log manager actor
    *
    * @param backend the backend sub-system reference
    * @return the configuration for the log manager actor
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the log manager actor
    *
    * @return the name of the log manager actor
    */
  def name = "log"
}

/**
  * The log manager actor's class
  *
  * @param backend the backend sub-system reference
  */
private[backend] class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * Hook into just before log manager actor is started for any initialization
    */
  override def preStart(): Unit = {
    logger.info("Started log sub-system")
  }

  /**
    * Handles incoming messages to the log manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Insert logs to datawarehouse
    /*case log: Firewall => Repository.insertFirewallLog(log)
    case log: AD => Repository.insertADLog(log)
    case log: Endpoint => Repository.insertEndpointLog(log)*/

    // Handle requests for getting log by organization
    case GetLogByOrganization(query, organization) => {

      logger.info(s"Received request to fetch log by query: ${query} , for organization: ${organization}")

      // Get the results from the repository
      val results = Repository.getLogByOrganization(query, organization.get.id, organization.get.name)

      // Send the details of the results to the requester
      sender() ! LogByOrganization(query = query, organization = organization, results = results)
    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
