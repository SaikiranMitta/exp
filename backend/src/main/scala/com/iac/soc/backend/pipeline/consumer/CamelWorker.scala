package com.iac.soc.backend.pipeline.consumer

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Date, Properties, TimeZone}

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import com.google.protobuf.any.Any
import com.iac.soc.backend.pipeline.messages.IngestedLog
import com.iac.soc.backend.schemas.Log
import com.iac.soc.backend.utility.Camel
import com.typesafe.scalalogging.LazyLogging
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.kafka.{KafkaConstants, KafkaManualCommit}
import org.apache.camel.{AggregationStrategy, Exchange, LoggingLevel}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * The companion object to the camel consumer worker actor
  */
object CamelWorker {

  /**
    * Creates the configuration for the camel consumer worker actor
    *
    * @param manager       the manager sub-system reference
    * @param normalization the normalization sub-system reference
    * @param enrichment    the enrichment sub-system reference
    * @param logWriter     the logWriter sub-system reference
    * @param logProducer   the logProducer sub-system reference
    * @return
    */
  def props(manager: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef) =
    Props(new CamelWorker(manager, normalization, enrichment, logWriter, logProducer))

  /**
    * The name of the camel consumer worker actor
    *
    * @return the name of the camel consumer worker actor
    */
  def name = "camel-consumer-worker"
}

class CamelWorker(manager: ActorRef, normalization: ActorRef, enrichment: ActorRef, logWriter: ActorRef, logProducer: ActorRef) extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the camel consumer worker actor
    */
  implicit val timeout: Timeout = Timeout(300 seconds)

  /**
    * The config section for kafka producer
    */
  val producerConfig = context.system.settings.config.getConfig("akka.kafka.producer")

  /**
    * The config section for kafka consumer
    */
  val consumerConfig = context.system.settings.config.getConfig("akka.kafka.consumer")

  /**
    * The final kafka topic to publish the completed logs to
    */
  val FINAL_KAFKA_TOPIC = producerConfig.getString("log-topic")

  /**
    * Creates a kafka producer for publishing logs to final topic
    *
    * @return the newly created kafka producer
    */
  private[this] def createProducer(): KafkaProducer[String, Array[Byte]] = {

    // The properties object to configure kafka producer
    val props = new Properties()

    // Add properties
    props.put("bootstrap.servers", producerConfig.getString("bootstrap-servers"))
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer")

    // Create the kafka producer with the specified properties
    val producer = new KafkaProducer[String, Array[Byte]](props)

    // Return the newly created producer
    producer
  }

  /**
    * Sets up the kafka consumer
    *
    * @param producer the kafka producer to send messages to final topic
    */
  private[this] def setupConsumers(producer: KafkaProducer[String, Array[Byte]]): Unit = {

    // Get the application camel context
    val context = Camel.getContext()

    // Add the routes for consuming from kafka
    context.addRoutes(new RouteBuilder() {

      override def configure(): Unit = {

        try {

          // Common error handler
          errorHandler(
            deadLetterChannel("log:dead?level=ERROR")
              .maximumRedeliveries(5)
              .retryAttemptedLogLevel(LoggingLevel.ERROR)
          )

          // Create the kafka uri for route
          val kafkaUri =
            s"kafka:${consumerConfig.getString("raw-log-topic")}?" +
              s"brokers=${consumerConfig.getString("bootstrap-servers")}&" +
              s"groupId=${consumerConfig.getString("raw-log-consumer-group")}&" +
              s"consumersCount=${consumerConfig.getInt("camel.number-of-consumers")}&" +
              s"maxPollRecords=${consumerConfig.getInt("camel.max-poll-records")}&" +
              s"autoOffsetReset=${consumerConfig.getString("camel.auto-offset-reset")}&" +
              s"heartbeatIntervalMs=${consumerConfig.getInt("camel.heartbeat-interval-ms")}&" +
              s"sessionTimeoutMs=${consumerConfig.getInt("camel.session-timeout-ms")}&" +
              s"maxPollIntervalMs=${consumerConfig.getInt("camel.max-poll-interval-ms")}&" +
              "autoCommitEnable=false&" +
              "allowManualCommit=true&" +
              "valueDeserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer"

          logger.info(s"Creating camel kafka consumer route for uri: ${kafkaUri}")

          // Create the kafka camel route
          from(kafkaUri)
            .routeId("camel-consumer-worker-kafka-route")
            .process(exchange => {

              // Get the value of the item (key is not required, as the key is already present in value's id)
              val value = Any.parseFrom(exchange.getIn.getBody(classOf[Array[Byte]])).unpack(IngestedLog)

              // Convert the current message to ingested log (using vector for grouping items)
              exchange.getIn.setBody(Vector(value))
            })
            .aggregate(header(KafkaConstants.PARTITION), new AggregationStrategy {

              override def aggregate(oldExchange: Exchange, newExchange: Exchange): Exchange = {

                if (oldExchange == null) return newExchange

                // Get the items from old and new exchanges
                val oldItem = oldExchange.getIn.getBody(classOf[Vector[IngestedLog]])
                val newItem = newExchange.getIn.getBody(classOf[Vector[IngestedLog]])

                // Get whether the items are last in batch
                val isLastInOld = oldExchange.getIn.getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, classOf[Boolean])
                val isLastInNew = newExchange.getIn.getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, classOf[Boolean])

                // Aggregate the items to new exchange's body
                newExchange.getIn.setBody(oldItem ++ newItem)

                // Set the item last header if either old or new item is last in batch
                // (this is needed for offset commits downstream)
                if (isLastInOld || isLastInNew) newExchange.getIn.setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, true)

                // Return the new exchange
                newExchange
              }
            })
            .parallelProcessing(false)
            .completionSize(consumerConfig.getInt("camel.aggregation-group-size"))
            .process(exchange => {

              // Set the batch processing start time
              // This is used downstream to calculate completion time for each phase
              exchange.getIn.setHeader("backend.camel.batchStartTime", System.currentTimeMillis())
            })
            .process(exchange => {

              logger.info("Going to do normalizations")

              // Get the logs
              val logs = exchange.getIn.getBody(classOf[Vector[IngestedLog]])

              // Get the futures which holds the results of normalization of ingested logs
              val futures = logs.map(log => {
                normalization ? log
              })

              // Create a sequence from the normalization futures
              val normalizationSequence = Future.sequence(futures)

              // Get the results of the normalizations
              val results = Await.result(normalizationSequence, 5 minutes).asInstanceOf[Vector[Log]]

              // Overwrite the exchange's body with the results of normalizations
              exchange.getIn.setBody(results)
            })
            .process(exchange => {

              // Set the current time which indicates when normalization was completed
              exchange.getIn.setHeader("backend.camel.normalizationCompletedTime", System.currentTimeMillis())
            })
            .process(exchange => {

              logger.info("Going to do insertions to kudu")

              // Get the logs
              val logs = exchange.getIn.getBody(classOf[Vector[Log]])

              // Get the futures which holds the results of insertion of logs
              val futures = logs.map(log => {
                logWriter ? log
              })

              // Create a sequence from the insertion futures
              val insertionSequence = Future.sequence(futures)

              // Get the results of the insertions
              val results = Await.result(insertionSequence, 5 minutes).asInstanceOf[Vector[Log]]

              // Overwrite the exchange's body with the results of the insertions to kudu
              exchange.getIn.setBody(results)
            })
            .process(exchange => {

              // Set the current time which indicates when insertion to kudu was completed
              exchange.getIn.setHeader("backend.camel.insertionCompletedTime", System.currentTimeMillis())
            })
            .process(exchange => {

              logger.info("Going to publish logs to final kafka topic")

              val todayDate = Date.from(Instant.parse(new Date().toInstant.toString))
              val todayDateFormatter = new SimpleDateFormat("yyyy-MM-dd")
              todayDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))

              val todayTopic = FINAL_KAFKA_TOPIC + "-" + todayDateFormatter.format(todayDate)

              // Get the final logs
              val logs = exchange.getIn.getBody(classOf[Vector[Log]])

              // Publish the logs
              logs.foreach(log => {

                logger.info(s"Publishing processed log with id: ${log.id.get} to final kafka topic")
                producer.send(new ProducerRecord[String, Array[Byte]](todayTopic, log.id.get, log.toByteArray))
              })
            })
            .process(exchange => {

              // Set the current time which indicates when publishing to final topic was completed
              exchange.getIn.setHeader("backend.camel.finalTopicPublishedTime", System.currentTimeMillis())
            })
            .process(exchange => {

              logger.info("Going to do commit offsets for batch")

              // Check if the item is last in the batch
              val isLastItemInBatch = exchange.getIn.getHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT, classOf[Boolean])

              // If it's the last item in the batch, commit the offset. Else, skip
              if (isLastItemInBatch) {

                // Get the manual commit mechanism
                val manual = exchange.getIn.getHeader(KafkaConstants.MANUAL_COMMIT, classOf[KafkaManualCommit])

                if (manual != null) {

                  logger.info("Committing offset for batch")
                  manual.commitSync()
                }
              }
            })
            .process(exchange => {

              // Set the current time which indicates when sync was completed
              exchange.getIn.setHeader("backend.camel.syncCompletedTime", System.currentTimeMillis())
            })
            .process(exchange => {

              // Get the times for each phase
              val batchStartTime = exchange.getIn.getHeader("backend.camel.batchStartTime", classOf[Long])
              val normalizationCompletedTime = exchange.getIn.getHeader("backend.camel.normalizationCompletedTime", classOf[Long])
              val insertionCompletedTime = exchange.getIn.getHeader("backend.camel.insertionCompletedTime", classOf[Long])
              val finalTopicPublishedTime = exchange.getIn.getHeader("backend.camel.finalTopicPublishedTime", classOf[Long])
              val syncCompletedTime = exchange.getIn.getHeader("backend.camel.syncCompletedTime", classOf[Long])

              // Publish the batch processing stats
              logger.info("Batch processing stats:\n"
                + s"Batch was started at: ${batchStartTime}\n"
                + s"Normalization was completed in: ${normalizationCompletedTime - batchStartTime} ms\n"
                + s"Insertion to kudu was completed in: ${insertionCompletedTime - normalizationCompletedTime} ms\n"
                + s"Publishing to final topic was completed in: ${finalTopicPublishedTime - insertionCompletedTime} ms\n"
                + s"Offset sync was completed in: ${syncCompletedTime - finalTopicPublishedTime} ms\n"
              )
            })

        }
        catch {

          case e: Exception => {

            logger.error(s"Error in starting camel consumer worker route: ${e}")
            logger.info(s"Stopping camel consumer worker route due to exception: ${e}")

            context.stopRoute("camel-consumer-worker-kafka-route")
            context.removeRoute("camel-consumer-worker-kafka-route")

            throw e
          }
        }
      }
    })
  }

  /**
    * Hook into just before camel consumer worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started camel consumer worker actor")

    // Create the kafka producer
    val producer = createProducer()

    // Setup the consumers
    setupConsumers(producer)

    context.watch(normalization)
  }

  /**
    * Handles incoming messages to the camel consumer worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    case Terminated(actorRef) => logger.error("CamelWorker watch result for normalization actor ", actorRef)

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")

  }
}
