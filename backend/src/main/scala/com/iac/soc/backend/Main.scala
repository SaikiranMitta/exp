package com.iac.soc.backend

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.iac.soc.backend.api.common.Datasource.getConnection
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

/**
  * The application entry point
  */
object Main extends App with LazyLogging {

  getConnection()

  // Load the default application configuration file
  val config = ConfigFactory.load()

  // Read the name of the actor system from configuration
  val actorSystemName = config.getString("actor-system.name")

  // Create the actor system for this node
  val system = ActorSystem(actorSystemName, config)

  logger.info(s"Starting node with roles: ${Cluster(system).selfRoles} at address: ${Cluster(system).selfAddress}")

  // Hook into event when the node has joined the cluster and is marked as ready
  Cluster(system).registerOnMemberUp {

    logger.info(s"Started node with roles: ${Cluster(system).selfRoles} at address: ${Cluster(system).selfAddress}")

    // Start the core backend actor, which will the parent of all systems in the node
    system.actorOf(Manager.props, Manager.name)

    logger.info("Starting Cluster Domain Event Listener")

    system.actorOf(Props(new ClusterDomainEventListener), "cluster-listener")

  }

  val env = config.getString("environment")

  logger.info(s"ENV :: ${env}")

  if (env == "production") {
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
  }

}