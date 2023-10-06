package com.iac.soc.backend.pipeline.ingestion

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.iac.soc.backend.pipeline.Repository
import com.iac.soc.backend.pipeline.ingestion.ingestors.{Lumberjack, S3, Syslog}
import com.iac.soc.backend.pipeline.models.{LumberjackIngestor, S3Ingestor, SyslogIngestor}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * The companion object to the ingestion manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the ingestion manager actor
    *
    * @param backend  the backend system reference
    * @param producer the producer sub-system reference
    * @return the configuration for the ingestion manager actor
    */
  def props(backend: ActorRef, producer: ActorRef) = Props(new Manager(backend, producer))

  /**
    * The name of the ingestion manager actor
    *
    * @return the name of the ingestion manager actor
    */
  def name = "ingestion"

  /**
    * Extracts the shard worker id based on the message
    */
  val extractEntityId: ShardRegion.ExtractEntityId = {

    // Extract the shard worker id from the rule id
    case message: S3SetupMessage => (message.id.toString, message)
  }

  /**
    * Extracts the shard region for the worker based on the message
    */
  val extractShardId: ShardRegion.ExtractShardId = {

    // Extract the entity id by hashing the rule id
    case message: S3SetupMessage => (message.id % 10).toString

    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % 10).toString
  }
}

/**
  * The ingestion manager actor's class
  *
  * @param backend  the backend system reference
  * @param producer the producer sub-system reference
  */
private[backend] class Manager(backend: ActorRef, producer: ActorRef) extends Actor with LazyLogging {

  /**
    * The rule workers shard proxy reference
    */
  private[this] var s3ShardProxy: ActorRef = _

  /**
    * Sets the worker shard proxy
    */
  private[this] def setupShardProxy(): Unit = {

    logger.info("Creating the rule workers's shard proxy")

    // Create and set the worker shard proxy
    s3ShardProxy =
      ClusterSharding(context.system).start(
        S3.name,
        S3.props(),
        ClusterShardingSettings(context.system).withRole("worker"),
        Manager.extractEntityId,
        Manager.extractShardId
      )
  }

  /**
    * Sets up all the ingestors
    */
  private[this] def setupIngestors(): Unit = {

    logger.info("Setting up ingestors")

    // Get all the ingestors defined in the system
    val ingestors = Repository.getIngestors

    logger.info(s"Found ${ingestors.length} ingestor(s)")

    // Setup the all the ingestors
    ingestors.foreach {

      case setup: LumberjackIngestor => context.actorOf(Lumberjack.props(self, producer, setup))

      case setup: SyslogIngestor => context.actorOf(Syslog.props(self, producer, setup))

      case setup: S3Ingestor => {

        val message = S3SetupMessage(
          id = setup.id,
          accessKey = setup.accessKey,
          secretKey = setup.secretKey,
          bucket = setup.bucket,
          region = setup.region,
          bucketPrefix = setup.bucketPrefix,
          skipHeaders = setup.skipHeaders,
          isGzipped = setup.isGzipped
        )

        // Create the message
        s3ShardProxy forward message
      }

    }

  }


  /**
    * Hook into just before ingestion manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started ingestion sub-system")

    // Setup shard proxy for S3 workers
    setupShardProxy()

    // Setup all the ingestors
    setupIngestors()
  }

  /**
    * Restart s3 sharding on member exit
    *
    * @param member - Node name
    */

  def restartShardProxy(member: String): Unit = {

    implicit val ec = context.system.dispatcher

    val delayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {

      logger.info("Restarting S3 shard cluster")

      // Get all the ingestors defined in the system
      val ingestors = Repository.getIngestors

      logger.info(s"Found ${ingestors.length} ingestor(s) while restarting shards")

      // Setup only s3 ingestors
      ingestors.foreach {

        case setup: S3Ingestor => {

          val message = S3SetupMessage(
            id = setup.id,
            accessKey = setup.accessKey,
            secretKey = setup.secretKey,
            bucket = setup.bucket,
            region = setup.region,
            bucketPrefix = setup.bucketPrefix,
            skipHeaders = setup.skipHeaders,
            isGzipped = setup.isGzipped
          )

          // Create the message
          s3ShardProxy forward message
        }
        case _ => {

          logger.info("Ignore other ingestors")

        }

      }

      logger.info(s"S3 shards restarted successfully on failure of node ${member}")

    })
  }

  /**
    * Handles incoming messages to the ingestion manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    case message: RestartS3Sharding => {

      restartShardProxy(message.nodeName)

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")


  }
}
