package com.iac.soc.backend.pipeline.ingestion.ingestors

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import akka.util.Timeout
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.iac.soc.backend.pipeline.ingestion.S3SetupMessage
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.iac.soc.backend.utility.Camel
import com.typesafe.scalalogging.LazyLogging
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.{RouteBuilder, ThreadPoolProfileBuilder}
import org.apache.camel.impl.SimpleRegistry
import com.iac.soc.backend.Main.config

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * The companion object to the S3 actor
  */
object S3 {

  /**
    * Creates the configuration for the s3 actor
    *
    * @return the configuration for the s3 actor
    */
  def props() = Props(new S3())

  /**
    * The name of the s3 actor
    *
    * @return the name of the s3 actor
    */
  def name = "s3"
}

/**
  * The s3 actor's class
  *
  */
class S3 extends Actor with LazyLogging {

  /**
    *
    * Producer Actor Ref
    */

  var producer: ActorRef = _

  /**
    * Timeout
    *
    */
  implicit val timeout: Timeout = Timeout(500 seconds)

  /**
    * Hook into just before S3 actor is started for any initialization
    */
  override def preStart(): Unit = {

    val producerFuture = context.system.actorSelection("akka://backend/user/backend/pipeline/producer").resolveOne()
    producer = Await.result(producerFuture, 5 seconds)

    logger.info("Started s3 actor")

  }

  /**
    * Handles incoming messages to the S3 actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Send ingested logs for producer
    case message: S3SetupMessage => {

      logger.info(s"Consuming s3 bucket :: ${message.bucket}-${message.bucketPrefix}")

      // Get the ingestor id
      val id = message.id

      // AWS credentials to access S3 buckets
      val accessKey = message.accessKey
      val secretKey = message.secretKey

      // CAMEL Route Configs
      val delay = config.getString("s3.camel.delay")
      val maxMessagesPerPoll = config.getString("s3.camel.maxMessagesPerPoll")
      val concurrentConsumers = config.getString("s3.camel.concurrentConsumers")


      // The bucket details
      val bucket = message.bucket
      val region = message.region
      val prefix = message.bucketPrefix
      //      val skipHeaders = message.skipHeaders
      val isGzipped = message.isGzipped

      // Create the AWS client
      val awsCredentials = new BasicAWSCredentials(accessKey, secretKey)
      val clientConfiguration = new ClientConfiguration()
      val client =
        AmazonS3ClientBuilder
          .standard()
          .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
          .withRegion(region)
          .build()

      val context = Camel.getContext()
      context.getRegistry(classOf[SimpleRegistry]).put("amazonS3Client", client)

      val custom = new ThreadPoolProfileBuilder("customPool").poolSize(20).maxPoolSize(50).build()
      context.getExecutorServiceManager.registerThreadPoolProfile(custom)

      // Create the routes
      context.addRoutes(new RouteBuilder() {

        override def configure(): Unit = {

          try {

            // Common exception handler (all config values must be in application.conf)
            errorHandler(
              deadLetterChannel("log:dead?level=ERROR")
                .maximumRedeliveries(5)
                .retryAttemptedLogLevel(LoggingLevel.INFO)
                .backOffMultiplier(2)
                .useExponentialBackOff()
            )

            if (isGzipped) {

              from(s"aws-s3://$bucket?amazonS3Client=#amazonS3Client&prefix=$prefix&deleteAfterRead=false&delay=${delay}&maxMessagesPerPoll=${maxMessagesPerPoll}")
                .routeId(s"s3-${id}")
                .streamCaching()
                .unmarshal().gzip()
                .split(body().tokenize("\n")).streaming().executorServiceRef("customPool")
                .to(s"seda:log-${id}")
            }
            else {

              from(s"aws-s3://$bucket?amazonS3Client=#amazonS3Client&prefix=$prefix&deleteAfterRead=false&delay=${delay}&maxMessagesPerPoll=${maxMessagesPerPoll}")
                .routeId(s"s3-${id}")
                .streamCaching()
                .split(body().tokenize("\n")).streaming().executorServiceRef("customPool")
                .to(s"seda:log-${id}")
            }

            from(s"seda:log-${id}?concurrentConsumers=${concurrentConsumers}")
              .routeId(s"seda-${id}")
              .process(exchange => {

                val log = exchange.getIn.getBody(classOf[String])

                logger.info(s"S3 data of ${message.bucket}-${message.bucketPrefix} : ${log}")

                producer ! IngestedLog(id = UUID.randomUUID.toString, ingestorId = id, log = log)

              })
            //.to(s"log:out-${id}")

          }
          catch {

            case e: Exception => {

              logger.error(s"Error in S3 ingestor: ${e}")

              logger.info(s"Stopping camel routes due to exception: ${e}")

              context.stopRoute(s"s3-${id}")
              context.stopRoute(s"seda-${id}")
              context.stopRoute(s"log-${id}")
              context.removeRoute(s"s3-${id}")
              context.removeRoute(s"seda-${id}")
              context.removeRoute(s"log-${id}")

              throw e
            }
          }

        }
      })

      // Start the camel system
      // context.start()
    }

  }
}
