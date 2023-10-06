package com.iac.soc.backend.rule

import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{Date, TimeZone}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.Main.config
import com.iac.soc.backend.user.messages.User
import com.iac.soc.backend.log.messages.{GetLogByOrganization, LogByOrganization, Organization}
import com.iac.soc.backend.notification.messages.SendEmail
import com.iac.soc.backend.rule.models.{Incident, Rule, Organization => OrganizationModel}
import com.iac.soc.backend.user.messages.{GetSocUsers, GetUsersByOrganization, Users}
import com.iac.soc.backend.utility.ASTUtility
import com.typesafe.scalalogging.LazyLogging
import org.fusesource.scalate.TemplateEngine
import org.quartz.{Job, JobExecutionContext, JobExecutionException}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import com.typesafe.config.{Config, ConfigFactory}

class ScheduleJob extends Job with LazyLogging {

  implicit val timeout: Timeout = Timeout(500 seconds)

  override def execute(jobExecutionContext: JobExecutionContext) = {

    val schedulerContext = jobExecutionContext.getScheduler.getContext
    val jobDataMap = jobExecutionContext.getJobDetail.getJobDataMap

    val user = schedulerContext.get("user").asInstanceOf[ActorRef]
    val notification = schedulerContext.get("notification").asInstanceOf[ActorRef]
    val ec = schedulerContext.get("ec").asInstanceOf[ExecutionContext]
    val log = schedulerContext.get("log").asInstanceOf[ActorRef]

    val rule = jobDataMap.get("rule").asInstanceOf[Rule]
    val organizations = jobDataMap.get("organizations").asInstanceOf[Vector[OrganizationModel]]
    val lower_bound_in_days = jobDataMap.get("lower_bound_in_days").toString
    var retryCount = jobDataMap.getInt("retry_count")

    logger.info("Event ==============================")

    val configRetryCount = config.getInt("rules.max_retry_count")
    val configRetrySleepTime = config.getInt("rules.retry_thread_sleep_time")
    val blockAlerts = config.getBoolean("block-alerts")


    // allow retries if retry count is valid as per configuration
    if (retryCount > configRetryCount) {
      val e = new JobExecutionException(s"Retries exceeded for ${rule}")
      throw e
    }

    try {
      logger.info(s"Event ==============================${rule}")

      logger.info("=====>>>JobExecuted")

      logger.info(s"Running Scheduler for rule : ${rule.id}")

      // Get the organizations to execute the rule by
      val organizationsToExecuteRuleAgainst = {
        if (rule.isGlobal) organizations
        else rule.organizations
      }

      // Get the futures for all execution against each organization
      val futures = Future.sequence(

        organizationsToExecuteRuleAgainst.map(org => {

          val currentDate = Date.from(Instant.parse(new Date().toInstant.toString))
          val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

          // Add org timestamp and log date to the query
          val query = ASTUtility.addOrganizations(org.name, rule.query)
          val timestamp_query = ASTUtility.addTimestampsToQuery(query, lower_bound_in_days, formatter.format(currentDate))
          val log_date_query = ASTUtility.addLogDate(timestamp_query)

          logger.info("Remove now from the query and add explicit date time")
          val remove_now_query = log_date_query.replace("localtimestamp", s"date_parse('${formatter.format(currentDate)}', '%Y-%m-%dT%T.%fZ')")

          logger.info(s"Query for incident with rule ${rule.id} is : ${remove_now_query}")

          log ? GetLogByOrganization(remove_now_query, Some(Organization(id = org.id, name = org.name)))

        })

      )(implicitly, ec)

      // Get the results
      val results = Await.result(futures, 5 minutes).asInstanceOf[Vector[LogByOrganization]]

      // Get the soc users
      val socUsers = Await.result(user ? GetSocUsers, 5 minutes).asInstanceOf[Users].value.toSet

      // Filter out empty results, create incident, fetch the users for the organization and send emails

      results
        .filter(res => res.results.nonEmpty)
        .flatMap(res => {

          // Create the incident
          val incident =
            Incident(
              id = null,
              ruleId = rule.id,
              organization = OrganizationModel(id = res.organization.get.id, name = res.organization.get.name),
              query = res.query,
              createdOn = ZonedDateTime.now(ZoneId.of("UTC"))
            )

          // Write the incident to the repository
          val incidentId: Long = Repository.createIncident(incident)

          // Get the users of the organization
          val organizationUsers = Await.result(user ? GetUsersByOrganization(res.organization.get.id), 5 minutes).asInstanceOf[Users].value.toSet

          // Combine the soc and organization users
          var users = socUsers ++ organizationUsers
          if (blockAlerts) {
            users = Set(
              User(1, "SOC", "soc@iac.com"),
              User(2, "Prashant", "prashant@cuelogic.com"),
              User(3, "Shantanu", "shantanu.wagholikar@cuelogic.com"),
              User(4, "Shruti", "shruti.das@cuelogic.com")
            )
          }

          logger.info(s"SEND EMAIL TO: ${users}")

          val utcZoneId = ZoneId.of("UTC")
          val zonedDateTime = ZonedDateTime.now
          val utcDateTime = zonedDateTime.withZoneSameInstant(utcZoneId)

          val config: Config = ConfigFactory.load()
          val incidentUrl = config.getString("client.url") + s"/analyze/logs?incident_id=${incidentId}&rule_id=${rule.id}"

          // Create the send email message
          users.map(user => {

            // Create scalate template and generate the notification message
            val engine = new TemplateEngine()
            val output = engine.layout("incident_notification.mustache", Map("user" -> user.name, "rule" -> rule.name, "organization" -> res.organization.get.name, "trigger_time" -> utcDateTime, "incident_url" -> incidentUrl))
            Thread.sleep(5000)

            // Create the send email message
            SendEmail(
              user.email,
              s"Alert - ${rule.name} triggered for Organization - ${res.organization.get.name}",
              output
            )
          })
        })
        .foreach(sendEmail => {

          // Send the email message to the notification sub-system
          notification ! sendEmail
        })
    }
    catch {
      case e: Exception => {
        retryCount += 1
        jobDataMap.put("retry_count", retryCount)
        val retryJobExeption = new JobExecutionException(e)

        // Sleep for 10 secs
        Thread.sleep(configRetrySleepTime)

        // Execute failed Job immediately
        retryJobExeption.setRefireImmediately(true)
        throw retryJobExeption
      }
    }



  }

}
