package com.iac.soc.backend.rule

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.rule.models.{Rule, Organization => OrganizationModel}
import com.iac.soc.backend.rules.messages._
import com.iac.soc.backend.user.messages.{GetOrganizations, Organizations}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.Set
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * The companion object to the rule manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the rule manager actor
    *
    * @param backend      the backend system reference
    * @param user         the user sub-system reference
    * @param log          the log sub-system reference
    * @param notification the notification sub-system reference
    * @return the configuration for the rule manager actor
    */
  def props(backend: ActorRef, user: ActorRef, log: ActorRef, notification: ActorRef) = Props(new Manager(backend, user, log, notification))

  /**
    * The name of the rule manager actor
    *
    * @return the name of the rule manager actor
    */
  def name = "rule-manager"

  /**
    * Extracts the shard worker id based on the message
    */
  val extractEntityId: ShardRegion.ExtractEntityId = {

    // Extract the shard worker id from the rule id
    case message: RuleCreated => (message.id.toString, message)
    case message: RuleUpdated => (message.id.toString, message)
    case message: RuleDeleted => (message.id.toString, message)
  }

  /**
    * Extracts the shard region for the worker based on the message
    */
  val extractShardId: ShardRegion.ExtractShardId = {

    // Extract the entity id by hashing the rule id
    case message: RuleCreated => (message.id % 10).toString
    case message: RuleUpdated => (message.id % 10).toString
    case message: RuleDeleted => (message.id % 10).toString

    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % 10).toString
  }
}

/**
  * The rule manager actor's class
  *
  * @param backend      the backend system reference
  * @param user         the user sub-system reference
  * @param log          the log sub-system reference
  * @param notification the notification sub-system reference
  */
class Manager(backend: ActorRef, user: ActorRef, log: ActorRef, notification: ActorRef) extends Actor with LazyLogging {

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val timeout: Timeout = Timeout(60 seconds)

  /**
    * The quartz scheduler for long running jobs
    */
  //private[this] val scheduler: QuartzScheduler = new StdSchedulerFactory().getScheduler

  /**
    * The rules within the system
    */
  private[this] var rules: Vector[Rule] = _

  /**
    * The organizations within the system
    */
  private[this] var organizations: Vector[OrganizationModel] = _

  /**
    * The rule workers shard proxy reference
    */
  private[this] var shardProxy: ActorRef = _

  /**
    * Sets the organizations property
    */
  private[this] def setupOrganizations(): Unit = {

    logger.info("Getting organizations defined in the system")

    // Get the organizations from the user sub-system
    val future = user ? GetOrganizations

    // Get the result of the ask query
    val organizationsMessage = Await.result(future, 5 minutes).asInstanceOf[Organizations]

    // Transform to the organizations message in rules
    //organizations = organizationsMessage.value.map(message => OrganizationModel(message.id, message.name)).toVector
  }

  /**
    * Sets the rules property
    */
  private[this] def setupRules(): Unit = {

    logger.info("Getting rules defined in the system")

    // Get the rules defined in the system
    rules = Repository.getRules
  }

  /**
    * Sets the worker shard proxy
    */
  private[this] def setupShardProxy(): Unit = {

    logger.info("Creating the rule workers's shard proxy")

    // Create and set the worker shard proxy
    shardProxy =
      ClusterSharding(context.system).start(
        Worker.name,
        Worker.props() /*backend, log, user, notification, scheduler, organizations)*/ ,
        ClusterShardingSettings(context.system).withRole("worker"),
        Manager.extractEntityId,
        Manager.extractShardId
      )
  }

  /**
    * Sets the worker shards
    */
  private[this] def setupWorkers(): Unit = {

    implicit val ec = context.system.dispatcher

    val delayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {

      logger.info("Clearing quartz scheduler before assigning rules to workers")

      Scheduler.clearScheduler()

      logger.info("Creating the rule workers shard")

      // Create the worker shards
      rules.foreach(rule => {

        // Send rule created message for initialization of the shard workers
        shardProxy forward RuleCreated(
          id = rule.id,
          name = rule.name,
          isGlobal = rule.isGlobal,
          organizations = rule.organizations.map(org => Organization(id = org.id, name = org.name)),
          query = rule.query,
          status = rule.status,
          categories = rule.categories.map(cat => Category(id = cat.id, name = cat.name)),
          severity = Some(Severity(id = rule.severity.id, name = rule.severity.name)),
          frequency = rule.frequency
        )
      })
    })
  }

  /**
    * Hook into just before rule manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started rule sub-system")

    // Set the organizations and rules
    setupOrganizations()
    setupRules()

    // Set the execution context from the actor system
    /*scheduler.getContext.put("user", user)
    scheduler.getContext.put("notification", notification)
    scheduler.getContext.put("log", log)
    scheduler.getContext.put("ec", context.dispatcher)

    // Start the scheduler
    scheduler.start()
    */

    // Set the shard proxy and workers
    setupShardProxy()
    setupWorkers()
  }

  // TODO: What about cluster rebalancing?
  /*override def preRestart(reason: Throwable, message: Option[Any]): Unit = {

    logger.info("Going to restart rule sub-system")

    scheduler.shutdown(false)

    // Default restart behavior (stops all child actors)
    super.preRestart(reason, message)
  }*/

  /**
    * Handles incoming messages to the rule manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Forward rule created, update and deleted messages to worker shards
    // TODO: Should this be an ask pattern, instead of tell?
    case message: RuleCreated => {

      //rules = rules += Rule(message.id, message.name, message.isGlobal, message.organizations.map(org => OrganizationModel(id = org.id, name = org.name)).toVector, message.query, message.status, message.categories.toVector, message.severity, message.frequency)
      shardProxy forward message

    }
    case message: RuleUpdated => shardProxy forward message
    case message: RuleDeleted => shardProxy forward message

    case message: RestartShardOnNodeExit => {

      rules = Repository.getRules

      logger.info(s"Reallocating shards as the member ${message.nodeName} is down")

      setupWorkers()

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}