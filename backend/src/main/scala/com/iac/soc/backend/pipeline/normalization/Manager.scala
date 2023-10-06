package com.iac.soc.backend.pipeline.normalization

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.routing.FromConfig
import akka.util.Timeout
import com.iac.soc.backend.pipeline.messages._
import com.typesafe.scalalogging.LazyLogging
import org.graalvm.polyglot.Context

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * The companion object to the normalization manager actor
  */
private[backend] object Manager {

  /**
    * Creates the configuration for the normalization manager actor
    *
    * @param backend    the backend system reference
    * @param enrichment the enrichment sub-system reference
    * @return the configuration for the normalization manager actor
    */
  def props(backend: ActorRef, enrichment: ActorRef) = Props(new Manager(backend, enrichment))

  /**
    * The name of the normalization manager actor
    *
    * @return the name of the normalization manager actor
    */
  def name = "normalization"

}

/**
  * The normalization manager actor's class
  *
  * @param backend    the backend system reference
  * @param enrichment the enrichment sub-system reference
  */
private[backend] class Manager(backend: ActorRef, enrichment: ActorRef) extends Actor with LazyLogging {

  /**
    *
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The normalization router reference
    */
  private[this] var router: ActorRef = _

  /**
    * The default timeout for the consumer worker actor
    */
  implicit val timeout: Timeout = Timeout(60 seconds)

  /**
    * Sets up the system defined normalizers
    */
  private[this] def setupNormalizers(): Unit = {

    logger.info("Setting up system defined normalizers")

  }


  def initializeContextWithMapper(): Context = {

    val context: Context = Context.newBuilder("js").allowIO(true).build

    //Include lodash
    context.eval("js", "load('classpath:normalizer_scripts/lodash.min.js');")

    //Include node-json-transform
    context.eval("js", "load('classpath:normalizer_scripts/node-json-transform.js');")

    //Include csvtojson
    context.eval("js", "load('classpath:normalizer_scripts/csvtojson.min.js');")

    //Include moment
    context.eval("js", "load('classpath:normalizer_scripts/moment.min.js');")

    context
  }

  /**
    * Sets up the normalization workers
    */
  private[this] def setupWorkers(): Unit = {

    logger.info("Setting up normalization workers")

    val allNormalizers = Repository.getNormalizersByIngestorIds()

    val graalvmContext = initializeContextWithMapper()

    // Create the router and routees for doing the normalization
    router =
      context.actorOf(
        FromConfig.props(Worker.props(self, enrichment /*, graalvmContext*/ , allNormalizers)),
        name = "router"
      )

  }

  /**
    * Hook into just before normalization manager actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started normalization sub-system")

    // Set up the system defined normalizers
    setupNormalizers()

    // Set up the normalization workers
    setupWorkers()
  }

  override def supervisorStrategy = OneForOneStrategy() {

    case e => {
      e.printStackTrace()
      logger.error(s"Normalization Worker Supervision from manager :: ${e.toString}")
      Restart

    }

  }

  /**
    * Handles incoming messages to the normalization manager actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send all the normalizers defined in the system
    /*case GetNormalizers => {

      // Convert the normalizers to protobuf message
      val message =
        NormalizersGroupedByIngestorId(
          normalizers
            .map(ingestors => {
              (ingestors._1, Normalizers(ingestors._2.map(n => Normalizer(n.id, n.schema, n.ingestorId, n.pattern, n.processor))))
            })
        )

      // Send the message to the requester
      sender() ! message
    }*/

    case GetNormalizersByIngestorIds(ingestorIds) => {

      logger.info(s"Fetching normalizers for ingestors ${ingestorIds}")

      val normalizerList: Map[Int, List[Normalizer]] = Repository.getNormalizersByIngestorIds(ingestorIds)

      sender() ! normalizerList

    }

    // Send the ingested logs to normalizer workers
    case ingestedLogs: IngestedLogs => {

      logger.info(s"Sending received ingestion logs to normalization worker")

      router forward ingestedLogs

    }

    // Send the ingested log to normalizer workers
    case ingestedLog: IngestedLog => {

      logger.info(s"Sending received ingestion log to normalization worker")

      router forward ingestedLog

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
