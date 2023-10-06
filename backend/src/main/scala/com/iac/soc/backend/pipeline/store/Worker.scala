package com.iac.soc.backend.pipeline.store

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.geolocation.messages.{GetIpGeolocation, IpGeolocation}
import com.iac.soc.backend.pipeline.messages.IngestedLogs
import com.iac.soc.backend.schemas._
import com.iac.soc.backend.threat.messages.{GetHashBlacklistStatus, GetIpBlacklistStatus, HashBlacklistStatus, IpBlacklistStatus}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * The companion object to the enrichment worker actor
  */
object Worker {

  /**
    * Creates the configuration for the enrichment worker actor
    *
    * @return the configuration for the enrichment worker actor
    */
  def props() = Props(new Worker())

  /**
    * The name of the enrichment worker actor
    *
    * @return the name of the enrichment worker actor
    */
  def name = "store-worker"
}

class Worker extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val timeout: Timeout = Timeout(5 seconds)

  /**
    * Repository Object
    */
  var kudu: Repository = _

  /**
    * Hook into just before enrichment worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    kudu = new Repository

    logger.info("Started log insertion worker actor")
  }

  private[this] def insert(logs: Logs): Seq[Log] = {

    try {

      kudu.insertLogs(logs)

    } catch {

      case exception: Exception => {

        exception.printStackTrace()
        logger.error("Exception in inserting log to kudu : ")

        //Seq.empty[Log]

        logs.value
      }

    }
  }


  /**
    * Handles incoming messages to the enrichment worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // insert the enriched logs

    // Normalize ingested logs
    case logs: Logs => {

      try {

        logger.info(s"Insert worker received enriched log ")

        val insertedLogs: Seq[Log] = insert(logs)

        if (insertedLogs.size > 0) {

          logger.info(s"{if} Inserted logs in kudu are ${insertedLogs.map(_.id)}")
          sender() ! insertedLogs

        }
        else {

          logger.info(s"{else} Inserted logs in kudu are ${insertedLogs.map(_.id)}")
          //sender() ! Seq.empty[Log]

          sender() ! logs.value

        }
      } catch {

        case ex: Exception => {

          logger.info("Failed to insert logs")

          //sender() ! Seq.empty[Log]

          sender() ! logs.value
        }

      }

    }

    case log: Log => {

      logger.info(s"Insert received enriched log")

      val insertedLog: Log = kudu.insertLog(log)

      logger.info(s"{if} Inserted log in kudu is ${insertedLog.id}")
      sender() ! insertedLog

    }

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")

  }

}
