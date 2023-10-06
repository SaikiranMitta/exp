package com.iac.soc.backend.threat

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.iac.soc.backend.threat.messages._
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.config.{Config, ConfigFactory}

/**
  * The companion object to the threat manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the threat manager actor
    *
    * @param backend the backend system reference
    * @return the configuration for the threat manager actor
    */
  def props(backend: ActorRef) = Props(new Manager(backend))

  /**
    * The name of the threat manager actor
    *
    * @return the name of the threat manager actor
    */
  def name = "threat"

  /**
    * Extracts the shard worker id based on the message
    */
  val extractEntityId: ShardRegion.ExtractEntityId = {

    // Extract the shard worker id from the settings id
    case settings: SetupTrustar => (settings.id.toString, settings)
  }

  /**
    * Extracts the shard region for the worker based on the message
    */
  val extractShardId: ShardRegion.ExtractShardId = {

    // Extract the entity id by hashing the settings id
    case settings: SetupTrustar => (settings.id % 10).toString

    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % 10).toString

  }

}

/**
  * The threat manager actor's class
  *
  * @param backend the backend system reference
  */
class Manager(backend: ActorRef) extends Actor with LazyLogging {

  /**
    * The threat workers shard proxy reference
    */

  private[this] var shardProxy: ActorRef = _

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * Sets the worker shard proxy
    */
  private[this] def setupShardProxy(): Unit = {

    logger.info("Creating the rule workers's shard proxy")

    // Create and set the worker shard proxy
    shardProxy =
      ClusterSharding(context.system).start(
        Worker.name,
        Worker.props(),
        ClusterShardingSettings(context.system).withRole("worker"),
        Manager.extractEntityId,
        Manager.extractShardId
      )
  }

  /**
    * Hook into just before threat manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    val main_config = ConfigFactory.load()

    val setupTruStar = main_config.getBoolean("setup.trustar")

    if (setupTruStar){
      logger.info("Setup shard proxy")

      setupShardProxy()

      logger.info("Setting Up Trustar")

      self ! SetupTrustar(1, config.getString("trustar.username"), config.getString("trustar.password"))

      logger.info("Started threat sub-system")
    }
  }

  /**
    * Handles incoming messages to the threat manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Handle requests for getting ip blacklist status
    case GetIpBlacklistStatus(ip) => {

      logger.info(s"Received request to fetch ip blacklist status for ip: ${ip}")

      // Get the ip blacklist status from the repository
      val model = Repository.getIpBlacklistStatus(ip)

      // Send the ip blacklist status to the requester
      sender() ! IpBlacklistStatus(model.ip, model.blacklisted)
    }

    // Handle requests for getting hash blacklist status
    case GetHashBlacklistStatus(hash) => {

      logger.info(s"Received request to fetch hash blacklist status for hash: ${hash}")

      // Get the hash blacklist status from the repository
      val model = Repository.getHashBlacklistStatus(hash)

      // Send the ip blacklist status to the requester
      sender() ! HashBlacklistStatus(model.hash, model.blacklisted)

    }

    case indicatorSearch: GetIndicatorDetails => sender() ! Repository.getThreatIndicators(indicatorSearch.indicators)

    case settings: SetupTrustar => shardProxy forward settings

    case settings: RestartThreatSharding => {

      self ! SetupTrustar(1, config.getString("trustar.username"), config.getString("trustar.password"))

    }
  }

}
