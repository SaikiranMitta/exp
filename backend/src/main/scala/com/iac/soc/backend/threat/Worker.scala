package com.iac.soc.backend.threat

import java.util.Base64

import akka.actor.{Actor, Props}
import akka.util.Timeout
import com.iac.soc.backend.threat.messages.{GetIndicatorDetails, SetupTrustar}
import com.iac.soc.backend.threat.models.{IndicatorSearchResponse, TokenResponse}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.camel.{Exchange, LoggingLevel}
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.http.common.HttpOperationFailedException
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.model.dataformat.JsonLibrary
import org.apache.camel.support.ExchangeHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * The companion object to the threat worker actor
  */
object Worker {

  /**
    * Creates the configuration for the threat worker actor
    *
    * @return the configuration for the threat worker actor
    */
  def props() = Props(new Worker())

  /**
    * The name of the threat worker actor
    *
    * @return the name of the threat worker actor
    */
  def name = "threat-worker"
}

/**
  * The threat worker actor's class
  *
  */
class Worker() extends Actor with LazyLogging {

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the threat worker actor
    */
  implicit val timeout: Timeout = Timeout(5 seconds)

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    *
    */

  private[this] def setupTrustar(trustar: SetupTrustar): Unit = {

    logger.info(s"Received request to set Trustar ")

    val context = new DefaultCamelContext()

    val basic_auth_token = Base64.getEncoder.encodeToString(s"${trustar.username}:${trustar.password}".getBytes())

    context.addRoutes(new RouteBuilder() {

      val template = context.createProducerTemplate()

      override def configure(): Unit = {

        // Common exception handler (all config values must be in application.conf)
        errorHandler(
          deadLetterChannel("log:dead?level=ERROR")
            .maximumRedeliveries(5)
            .retryAttemptedLogLevel(LoggingLevel.INFO)
            .backOffMultiplier(2)
            .useExponentialBackOff()
            .onRedelivery(exchange => {

              log.info("Reset token before retry")
              exchange.getIn.setHeader("trustar.method", "getAccessToken")
            })
        )

        // Handle scenario where username/password is incorrect
        // Stop everything, as there is no point in continuing/retrying
        onException(classOf[HttpOperationFailedException])
          .onWhen(simple("${exception.getStatusCode} == 401"))
          .handled(true)
          .process(exchange => {

            log.error("Trustar username/paassword in wrong!")
            context.suspendRoute("schedulerRoute")
          })

        // Set up the scheduler route
        //from("timer://foo?repeatCount=1")
        from("quartz2://com.iac.soc.backend/trustar?cron=0 */3 * ? * *")
          .routeId("schedulerRoute")
          .log(LoggingLevel.INFO, "Trustar scheduler has been triggered")
          .process(exchange => {

            // Define the window start and end times
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (3 * 60 * 1000) // 3 minutes interval

            log.info("Setting initial headers after scheduler was triggered")

            // Set the headers on the message to prepare for making the API calls
            exchange.getIn.setHeader("trustar.from", startTime)
            exchange.getIn.setHeader("trustar.to", endTime)
            exchange.getIn.setHeader("trustar.pageNumber", 0)
            exchange.getIn.setHeader("trustar.pageSize", 1)
            exchange.getIn.setHeader("trustar.method", "getAccessToken")
          })
          .to("seda:api")

        // Set up the API route for any API call (which can be used as common throttle point)
        // ref: https://support.trustar.co/article/m5kl5anpiz-api-rate-limit-quota
        from("seda:api")
          .throttle(15).timePeriodMillis(60 * 1000) // 15 calls/minute
          .choice()
          .when(header("trustar.method").isEqualTo("getAccessToken"))
          .to("direct:get-access-token")
          .when(header("trustar.method").isEqualTo("indicatorSearch"))
          .to("direct:indicator-search")

        // Set up route for getting access token
        from("direct:get-access-token")
          .process(exchange => {

            log.info("Setting headers for getting access token")

            // Setting grant type for fetching access token
            // ref: https://docs.trustar.co/api/index.html#authentication
            exchange.getIn.setHeader("Authorization", s"Basic $basic_auth_token")
            exchange.getIn.setHeader("Content-Type", "application/x-www-form-urlencoded")
            exchange.getIn.setBody("grant_type=client_credentials")
          })
          .log(LoggingLevel.INFO, "Going to fetch access token")
          .to(s"https4://api.trustar.co/oauth/token")
          .log(LoggingLevel.INFO, "Got access token")
          .unmarshal().json(JsonLibrary.Jackson, classOf[TokenResponse])
          .process(exchange => {

            // Get the access token
            val accessToken = exchange.getIn.getBody(classOf[TokenResponse]).access_token

            log.info("Setting access token in header")
            exchange.getIn.setHeader("Authorization", s"Bearer $accessToken")
            exchange.getIn.setHeader("trustar.method", "indicatorSearch")
          })
          .to("seda:api")

        // Set up the route for performing indicator search
        from("direct:indicator-search")
          .process(exchange => {

            // Get the page number, size, search period
            val pageNumber = exchange.getIn.getHeader("trustar.pageNumber")
            val pageSize = exchange.getIn.getHeader("trustar.pageSize")
            val from = exchange.getIn.getHeader("trustar.from")
            val to = exchange.getIn.getHeader("trustar.to")

            log.info(s"Going to perform indicator search with params - pageNumber:$pageNumber, pageSize:$pageSize, from:$from, to:$to")

            exchange.getIn.setHeader(Exchange.HTTP_RAW_QUERY, s"pageNumber=$pageNumber&pageSize=$pageSize=$pageSize&from=$from&to=$to")
            exchange.getIn.setBody(null)
          })
          .log(LoggingLevel.INFO, "Going to perform indicator search")
          .to("https4://api.trustar.co/api/1.3/indicators/search")
          .log(LoggingLevel.INFO, "Indicator search performed successfully")
          .unmarshal().json(JsonLibrary.Jackson, classOf[IndicatorSearchResponse])
          .process(exchange => {

            // Get the indicator search response
            val response = exchange.getIn.getBody(classOf[IndicatorSearchResponse])

           /* log.info(s"-----------------Page number: ${response.pageNumber}")
            log.info(s"-----------------Page size: ${response.pageSize}")
            log.info(s"-----------------Total items: ${response.totalElements}")
            log.info(s"-----------------Total pages: ${response.totalPages}")

            response.items.foreach(indicator => {
              log.info("-----------------------------------Indicator-----------------------------------")
              log.info(s"guid: ${indicator.guid}")
              log.info(s"type: ${indicator.indicatorType}")
              log.info(s"value: ${indicator.value}")
              log.info(s"priority: ${indicator.priorityLevel}")
            })*/

            Repository.bulkInsertThreatIndicator(response.items)

            // If there are more pages than first request, create exchanges to perform the API calls and set page numbers appropriately
            if (!response.hasNext) {
              log.info("Reached end of pages")
            }
            else if (response.pageNumber == 0) {

              log.info("There are more pages than first request, setting up exchanges to fetch remaining pages")

              // Get the remaining pages to be fetched
              val pagesRange = 1 until response.totalPages

              // Fetch the next set of pages
              pagesRange.foreach(pageNumber => {

                // Create the new exchange to fetch the page
                val newExchange = ExchangeHelper.copyExchangeAndSetCamelContext(exchange, exchange.getContext)

                // Set the headers for the fetch
                newExchange.getIn.setHeader("trustar.pageNumber", pageNumber)
                exchange.getIn.setHeader("trustar.method", "getAccessToken")

                // Send the exchange to the API route to start fetching
                exchange.getContext.createProducerTemplate().send("seda:api", newExchange)
              })

            }

          })

      }

    })

    context.start()

  }

  /**
    * Hook into just before threat worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Started threat worker")

  }

  /**
    * Handles incoming messages to the threat worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    case message: SetupTrustar => setupTrustar(message)


    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${message}")
  }

}
