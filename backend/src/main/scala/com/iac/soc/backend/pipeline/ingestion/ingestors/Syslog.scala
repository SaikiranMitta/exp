package com.iac.soc.backend.pipeline.ingestion.ingestors

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.iac.soc.backend.pipeline.models.SyslogIngestor
import com.iac.soc.backend.utility.Camel
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext

/**
  * The companion object to the syslog actor
  */
object Syslog {

  /**
    * Creates the configuration for the syslog actor
    *
    * @param ingestion the ingestion sub-system reference
    * @param producer  the producer sub-system reference
    * @param setup     the syslog setup required
    * @return the configuration for the syslog actor
    */
  def props(ingestion: ActorRef, producer: ActorRef, setup: SyslogIngestor) = Props(new Syslog(ingestion, producer, setup))

  /**
    * The name of the syslog actor
    *
    * @return the name of the syslog actor
    */
  def name = s"syslog-${UUID.randomUUID.toString}"
}

/**
  * The syslog actor's class
  *
  * @param ingestion the ingestion sub-system reference
  * @param producer  the producer sub-system reference
  * @param setup     the syslog setup required
  */
class Syslog(ingestion: ActorRef, producer: ActorRef, setup: SyslogIngestor) extends Actor with LazyLogging {

  /**
    * Sets up apache camel based syslog server
    */
  private[this] def setupCamelSyslogServer(): Unit = {

    val main_config = ConfigFactory.load()
    val env = main_config.getString("environment")
    val excludeSubstringLine = main_config.getBoolean("lumberjack.excludeSubstringLine")


    // Create the camel context
    val context = Camel.getContext()

    // Add the camel syslog route to the context
    context.addRoutes(new RouteBuilder {

      override def configure(): Unit = {

        // Setup syslog on the port with the protocol specified
        from(s"netty4:${setup.protocol}://0.0.0.0:${setup.port}?sync=false&allowDefaultCodec=false")
          .process((exchange: Exchange) => {

            // Get the syslog message
            val message = exchange.getIn.getBody(classOf[String])

            logger.info(s"Syslog id: ${setup.id} protocol:${setup.protocol} port:${setup.port} - Received message - ${message}")

            // Publish the message to self
            //producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message)

            if (env == "production") {

              if (excludeSubstringLine) {

                producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message)

              } else {

                producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message.substring(6, message.length - 1))

              }

            } else {

              //self ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message)
              if (excludeSubstringLine) {

                producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message)

              } else {

                producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = 1, log = message.substring(6, message.length - 1))

              }

            }

          })
      }
    })

    // Start the context
    // context.start()
  }

  /**
    * Hook into just before syslog actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started syslog actor")

    // Setup the camel syslog server
    setupCamelSyslogServer()
  }

  /**
    * Handles incoming messages to the syslog actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send ingested logs for normalization
    case log: IngestedLog => producer ! log

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }
}
