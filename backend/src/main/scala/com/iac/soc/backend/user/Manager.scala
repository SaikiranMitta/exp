package com.iac.soc.backend.user

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.user.messages._
import com.typesafe.scalalogging.LazyLogging

/**
  * The companion object to the user manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the user manager actor
    *
    * @param backend the backend system reference
    * @return the configuration for the user manager actor
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the user manager actor
    *
    * @return the name of the user manager actor
    */
  def name = "user"
}

/**
  * The user manager actor's class
  *
  * @param backend the backend system reference
  */
class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * Hook into just before user manager actor is started for any initialization
    */
  override def preStart(): Unit = {
    logger.info("Started user sub-system")
  }

  /**
    * Handles incoming messages to the user manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Handle requests for getting organizations
    case GetOrganizations => {

      logger.info("Received request to fetch organizations")

      // Get the organizations from the repository
      val organizationsModel = Repository.getOrganizations()

      // Create the organizations message
      val organizations = Organizations(value = organizationsModel.map(model => Organization(id = model.id, name = model.name)))

      // Send the organizations to the requester
      sender() ! organizations
    }

    // Handle requests for getting users by organization id
    case GetUsersByOrganization(id) => {

      logger.info(s"Received request to fetch users by organization id: ${id}")

      // Get the users from the repository
      val usersModel = Repository.getUsersByOrganization(id)

      // Create the users message
      val users = Users(value = usersModel.map(model => User(id = model.id, name = model.name, email = model.email)))

      // Send the users to the requester
      sender() ! users
    }

    // Handle requests for getting soc users
    case GetSocUsers => {

      logger.info("Received request to fetch soc users")

      // Get the users from the repository
      val usersModel = Repository.getSocUsers()

      // Create the users message
      val users = Users(value = usersModel.map(model => User(id = model.id, name = model.name, email = model.email)))

      // Send the users to the requester
      sender() ! users
    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}