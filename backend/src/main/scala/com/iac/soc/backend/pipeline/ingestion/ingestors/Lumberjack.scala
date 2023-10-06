package com.iac.soc.backend.pipeline.ingestion.ingestors

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.iac.soc.backend.pipeline.models.LumberjackIngestor
import com.iac.soc.backend.utility.Camel
import com.typesafe.scalalogging.LazyLogging
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.jsse.{KeyManagersParameters, KeyStoreParameters, SSLContextParameters}
import com.typesafe.config.ConfigFactory

/**
  * The companion object to the lumberjack actor
  */
object Lumberjack {

  /**
    * Creates the configuration for the lumberjack actor
    *
    * @param ingestion the ingestion sub-system reference
    * @param producer  the producer sub-system reference
    * @param setup     the lumberjack setup required
    * @return the configuration for the lumberjack actor
    */
  def props(ingestion: ActorRef, producer: ActorRef, setup: LumberjackIngestor) = Props(new Lumberjack(ingestion, producer, setup))

  /**
    * The name of the lumberjack actor
    *
    * @return the name of the lumberjack actor
    */
  def name = s"lumberjack-${UUID.randomUUID.toString}"
}

/**
  * The lumberjack actor's class
  *
  * @param ingestion the ingestion sub-system reference
  * @param setup     the lumberjack setup required
  */
class Lumberjack(ingestion: ActorRef, producer: ActorRef, setup: LumberjackIngestor) extends Actor with LazyLogging {

  /**
    * Sets up apache camel based lumberjack server
    */
  private[this] def setupCamelLumberjackServer(): Unit = {

    val main_config = ConfigFactory.load()
    val env = main_config.getString("environment")

    logger.info(s"Environment --- ${env} ")

    val keystorePassword = main_config.getString("lumberjack.keystorePassword")

    // Create the camel context
    val context = Camel.getContext()

    // Create the keystore parameters
    val keyStoreParameters = new KeyStoreParameters()

    // Set the jks file and password
    if (env == "production") {
      keyStoreParameters.setResource("resources/certs/iac-wildcard.jks")
      keyStoreParameters.setPassword(keystorePassword)
    } else if (env == "staging") {
      keyStoreParameters.setResource("resources/certs/iac-wildcard.jks")
      keyStoreParameters.setPassword(keystorePassword)
    } else {
      keyStoreParameters.setResource("/my.jks")
      keyStoreParameters.setPassword(keystorePassword)
    }

    // Create key manager parameters
    val keyManagersParameters = new KeyManagersParameters()

    // Set the keystore and password
    keyManagersParameters.setKeyStore(keyStoreParameters)
    keyManagersParameters.setKeyPassword(keystorePassword)

    // Create the ssl context
    val scp = new SSLContextParameters()

    // Add the keystore to the ssl context
    scp.setKeyManagers(keyManagersParameters)

    val lumberjackComponent = context.getComponent("lumberjack", classOf[org.apache.camel.component.lumberjack.LumberjackComponent])

    lumberjackComponent.setSslContextParameters(scp)

    // Add the camel lumberjack route to the context
    context.addRoutes(new RouteBuilder {

      override def configure(): Unit = {

        // Setup lumberjack on the port specified
        from(s"lumberjack:0.0.0.0:${setup.port}")
          .process((exchange: Exchange) => {

            // Get the lumberjack message
            val message = exchange.getIn.getBody(classOf[String])

            logger.info(s"Lumberjack id: ${setup.id} port:${setup.port} - Received message - ")

            // Publish the message to self

            val excludeSubstringLine = main_config.getBoolean("lumberjack.excludeSubstringLine")

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
    * Hook into just before lumberjack actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started lumberjack actor")

    // Setup the camel lumberjack server
    setupCamelLumberjackServer()

  }

  /**
    * Handles incoming messages to the lumberjack actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send ingested logs for producer
    case log: IngestedLog => producer ! log

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")

  }

}
