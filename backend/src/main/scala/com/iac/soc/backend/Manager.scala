package com.iac.soc.backend

import akka.Done
import akka.actor.{Actor, ActorRef, CoordinatedShutdown, PoisonPill, Props}
import akka.util.Timeout
import com.iac.soc.backend.Main.system
import com.iac.soc.backend.api.gateway.APIGateway
import com.iac.soc.backend.geolocation.{Manager => GeolocationManager}
import com.iac.soc.backend.log.{Manager => LogManager}
import com.iac.soc.backend.notification.{Manager => NotificationManager}
import com.iac.soc.backend.pipeline.{Manager => PipelineManager}
import com.iac.soc.backend.rule.{Manager => RuleManager}
import com.iac.soc.backend.threat.{Manager => ThreatManager}
import com.iac.soc.backend.user.{Manager => UserManager}
import com.iac.soc.backend.utility.Camel
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.config.DBs

import scala.concurrent.duration._

/**
  * The companion object to the backend manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the backend manager actor
    *
    * @return the configuration for the backend manager actor
    */
  def props = Props(new Manager)

  /**
    * The name of the backend manager actor
    *
    * @return the name of the backend manager actor
    */
  def name = "backend"
}

/**
  * The backend manager actor's class
  */
private[backend] class Manager extends Actor with LazyLogging {

  /**
    * The default timeout for the rule worker actor
    */
  implicit val timeout: Timeout = Timeout(500 seconds)

  /**
    * The pipeline sub-system reference
    */
  private[this] var pipeline: ActorRef = _

  /**
    * The rule sub-system reference
    */
  private[this] var rule: ActorRef = _

  /**
    * The log sub-system reference
    */
  private[this] var log: ActorRef = _

  /**
    * The geolocation sub-system reference
    */
  private[this] var geolocation: ActorRef = _

  /**
    * The threat sub-system reference
    */
  private[this] var threat: ActorRef = _

  /**
    * The user sub-system reference
    */
  private[this] var user: ActorRef = _

  /**
    * The notification sub-system reference
    */
  private[this] var notification: ActorRef = _

  /**
    * Sets up the pipeline sub-system
    *
    * @param log         the log sub-system reference
    * @param geolocation the geolocation sub-system reference
    * @param threat      the threat sub-system reference
    * @return the pipeline sub-system reference
    */
  private[this] def setupPipeline(log: ActorRef, geolocation: ActorRef, threat: ActorRef): ActorRef = {

    logger.info("Starting pipeline sub-system")

    // Create the pipeline sub-subsystem
    context.actorOf(
      PipelineManager.props(self, log, geolocation, threat),
      PipelineManager.name
    )
  }

  /**
    * Sets up the rule sub-system
    *
    * @param user         the user sub-system reference
    * @param log          the log sub-system reference
    * @param notification the notification sub-system reference
    * @return the rule sub-system reference
    */
  private[this] def setupRule(user: ActorRef, log: ActorRef, notification: ActorRef): ActorRef = {

    logger.info("Starting rule sub-system")

    // Create the rule sub-subsystem
    context.actorOf(
      RuleManager.props(self, user, log, notification),
      RuleManager.name
    )
  }

  /**
    * Sets up the log sub-system
    *
    * @return the log sub-system reference
    */
  private[this] def setupLog(): ActorRef = {

    logger.info("Starting log sub-system")

    // Create the log sub-system reference
    context.actorOf(
      LogManager.props(self),
      LogManager.name
    )
  }

  /**
    * Sets up the geolocation sub-system
    *
    * @return the geolocation sub-system reference
    */
  private[this] def setupGeolocation(): ActorRef = {

    logger.info("Starting geolocation sub-system")

    // Create up the geolocation sub-system
    context.actorOf(
      GeolocationManager.props(self),
      GeolocationManager.name
    )
  }

  /**
    * Sets up the threat sub-system
    *
    * @return the threat sub-system reference
    */
  private[this] def setupThreat(): ActorRef = {

    logger.info("Starting threat sub-system")

    // Create the threat sub-system
    context.actorOf(
      ThreatManager.props(self),
      ThreatManager.name
    )
  }

  /**
    * Sets up the user sub-system
    *
    * @return the user sub-system reference
    */
  private[this] def setupUser(): ActorRef = {

    logger.info("Starting user sub-system")

    // Create the user sub-system
    context.actorOf(
      UserManager.props(self),
      UserManager.name
    )
  }

  /**
    * Sets up the notification sub-system
    *
    * @return the notification sub-system reference
    */
  private[this] def setupNotification(): ActorRef = {

    logger.info("Starting notification sub-system")

    // Create the notification sub-system
    context.actorOf(
      NotificationManager.props(self),
      NotificationManager.name
    )
  }

  /**
    * Sets up the notification sub-system
    *
    * @return the notification sub-system reference
    */
  private[this] def setupAPI(ruleSubSystem: ActorRef): Unit = {

    logger.info("Starting API sub-system")

    // Create the API sub-system

    implicit val system = context.system

    val api = new APIGateway()

    api.ruleSubSystem = ruleSubSystem

    api.setupAPI(ruleSubSystem)
  }

  /**
    * Hook into just before backend manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    val main_config = ConfigFactory.load()

    val setupApi = main_config.getBoolean("setup.api")

    logger.info("Started backend system")

    logger.info("Starting all sub-systems")

    // Setup all the sub-systems in the right order
    log = setupLog()
    geolocation = setupGeolocation()
    threat = setupThreat()
    user = setupUser()
    notification = setupNotification()
    rule = setupRule(user, log, notification)
    pipeline = setupPipeline(log, geolocation, threat)

    if (setupApi == true) {
      setupAPI(rule)
    }

  }

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "someTaskName") { () =>

    logger.info("Shutting down camel...")

    Camel.shutdown()

    logger.info("Stopped camel...")

    scala.concurrent.Future.successful(Done)

  }

  CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceRequestsDone, "someTaskName") { () =>

    logger.info("Shutting down DB Connections ...")

    DBs.closeAll()

    logger.info("Killing main actor...")

    self ! PoisonPill

    //val consumerFuture = context.system.actorSelection("akka://backend/user/backend/pipeline/manager/consumer-worker").resolveOne()
    //val consumer = Await.result(consumerFuture, 5 seconds)

    //consumer ! PoisonPill

    logger.info("System shutdown completed...")

    scala.concurrent.Future.successful(Done)

  }

  /**
    * Handles incoming messages to the backend manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
