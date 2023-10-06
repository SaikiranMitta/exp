package com.iac.soc.backend.rule

import java.text.SimpleDateFormat
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.{Date, TimeZone, UUID}

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.log.messages.{GetLogByOrganization, LogByOrganization, Organization}
import com.iac.soc.backend.notification.messages.SendEmail
import com.iac.soc.backend.rule.models.{Category, Incident, Rule, Severity, Organization => OrganizationModel}
import com.iac.soc.backend.rules.messages.{RuleCreated, RuleDeleted, RuleSchedule, RuleUpdated}
import com.iac.soc.backend.user.messages._
import com.iac.soc.backend.utility.ASTUtility
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.fusesource.scalate.TemplateEngine
import org.quartz._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * The companion object to the rule worker actor
  */
object Worker {

  /**
    * Creates the configuration for the rule worker actor
    *
    * @param backend       the backend system reference
    * @param log           the log sub-system reference
    * @param user          the user sub-system reference
    * @param notification  the notification sub-system reference
    * @param scheduler     the quartz scheduler for scheduling long running jobs
    * @param organizations the organizations in the system
    * @return the configuration for the rule worker actor
    */
  def props() /*backend: ActorRef, log: ActorRef, user: ActorRef, notification: ActorRef, scheduler: Scheduler , organizations: Vector[OrganizationModel])*/ = Props(new Worker()) /*backend, log, user, notification, scheduler , organizations))*/

  /**
    * The name of the rule worker actor
    *
    * @return the name of the rule worker actor
    */
  def name = "rule-worker"
}

/**
  * The rule worker actor's class
  *
  * @param backend       the backend system reference
  * @param log           the log sub-system reference
  * @param user          the user sub-system reference
  * @param notification  the notification sub-system reference
  * @param scheduler     the quartz scheduler for scheduling long running jobs
  * @param organizations the organizations in the system
  */
class Worker /*backend: ActorRef, log: ActorRef, user: ActorRef, notification: ActorRef, scheduler: Scheduler , organizations: Vector[OrganizationModel])*/ extends Actor with LazyLogging {

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * The execution context to be used for futures operations
    */
  implicit val ec: ExecutionContext = context.dispatcher

  /**
    * The default timeout for the rule worker actor
    */
  implicit val timeout: Timeout = Timeout(500 seconds)

  /**
    * The backend actor ref
    */
  private[this] var backend: ActorRef = _

  /**
    * The log actor ref
    */
  private[this] var log: ActorRef = _

  /**
    * The user actor ref
    */
  private[this] var user: ActorRef = _

  /**
    * The notification actor ref
    */
  private[this] var notification: ActorRef = _

  /**
    * The rule enabled status
    */
  private[this] val ENABLED: String = "enabled"

  /**
    * The id to be used for the job scheduler
    */
  private[this] val jobId: String = UUID.randomUUID().toString

  /**
    * The rule object for the worker
    */
  private[this] var rule: Rule = _

  /**
    * The organization object for the worker
    */
  private[this] var organizations: Vector[OrganizationModel] = _

  /**
    * The status of the quartz job schedule
    */
  private[this] var isJobRunning: Boolean = false

  /**
    * The lower bound value in days for adding timestamp to query using ASt
    */
  private[this] val lower_bound_in_days: String = config.getString("ast.lower-bound-in-days")

  /**
    * The scheduler instance
    */
  private[this] var scheduler: Scheduler = _

  /**
    * Deletes the job schedule if its running
    */
  private[this] def deleteJobSchedule(): Unit = {

    // Delete the job schedule if its running
    if (isJobRunning) {

      logger.info(s"Deleting job schedule for rule with id: ${rule.id}")

      // Delete the job schedule
      val jobKey = JobKey.jobKey(jobId + "_job")
      val triggerKey = TriggerKey.triggerKey(jobId + "_trigger")

      scheduler.pauseTrigger(triggerKey)
      scheduler.unscheduleJob(triggerKey)
      scheduler.deleteJob(jobKey)

      logger.info(s"Deleted job schedule for rule with id: ${rule.id}")

      // Update the status of the job
      isJobRunning = false

    }
    else {
      logger.info(s"Skipping deletion of job schedule for rule with id: ${rule.id} as it wasn't created and running")
    }

  }

  /**
    * Creates, or updates a quartz job
    */
  private[this] def createOrUpdateJobSchedule(): Unit = {

    // Delete the job schedule to reset to default state
    deleteJobSchedule()

    // Skip creation of schedule if rule is not enabled
    if (rule.status != ENABLED) {

      logger.info(s"Skipping job scheduling for rule with id: ${rule.id}, as rule is not enabled")

      self ! RuleDeleted(rule.id)
    }
    else if (!isJobRunning) {

      logger.info(s"Creating/updating job schedule for rule with id: ${rule.id}")
      /*val job = JobBuilder.newJob(classOf[ScheduleJob]).withIdentity(jobId).build()
      val trigger = TriggerBuilder.newTrigger().startNow().withIdentity(jobId).forJob(jobId).withSchedule(CronScheduleBuilder.cronSchedule(rule.frequency)).build()

      scheduler.getContext.put("rule", rule)
      scheduler.getContext.put("user", user)
      scheduler.getContext.put("notification", notification)
      scheduler.getContext.put("organizations", organizations)
      scheduler.getContext.put("lower_bound_in_days", lower_bound_in_days)
      scheduler.getContext.put("log", log)
      scheduler.getContext.put("ec", ec)

      scheduler.scheduleJob(job, trigger)*/

      val job =
        JobBuilder.newJob(classOf[ScheduleJob])
          .withIdentity(jobId + "_job")
          .build()

      job.getJobDataMap.put("rule", rule)
      job.getJobDataMap.put("lower_bound_in_days", lower_bound_in_days)
      job.getJobDataMap.put("organizations", organizations)
      job.getJobDataMap.put("retry_count", 0)

      val trigger =
        TriggerBuilder.newTrigger()
          .withIdentity(jobId + "_trigger")
          .withSchedule(CronScheduleBuilder.cronSchedule(rule.frequency))
          .build()

      scheduler.scheduleJob(job, trigger)

      logger.info(s"Created job schedule for rule with id: ${rule.id}")

      // Update the status of the job
      isJobRunning = true

    }

  }

  /**
    * Hook into just before rule worker actor is started for any initialization
    */
  override def preStart(): Unit = {

    logger.info("Starting Rule worker")

    val userFuture = context.system.actorSelection("akka://backend/user/backend/user").resolveOne()
    user = Await.result(userFuture, 5 seconds)

    // Get the organizations from the user sub-system
    val future = user ? GetOrganizations

    // Get the result of the ask query
    val organizationsMessage = Await.result(future, 5 seconds).asInstanceOf[Organizations]
    organizations = organizationsMessage.value.map(message => OrganizationModel(message.id, message.name)).toVector

    val logFuture = context.system.actorSelection("akka://backend/user/backend/log").resolveOne()
    log = Await.result(logFuture, 5 seconds)

    val notificationFuture = context.system.actorSelection("akka://backend/user/backend/notification").resolveOne()
    notification = Await.result(notificationFuture, 5 seconds)

    //Setting the Scheduler
    scheduler = Scheduler.getScheduler(user, notification, log, context.dispatcher)

    logger.info("Started rule worker")

  }

  /**
    * Handles incoming messages to the rule worker actor
    *
    * @return the message handling loop
    */
  override def receive: Receive = {

    // Handle creation of rule
    case message: RuleCreated => {

      logger.info(s"Received creation of rule with id: ${message.id} in worker")

      // Set the rule for the worker
      rule = Rule(
        id = message.id,
        name = message.name,
        isGlobal = message.isGlobal,
        organizations = message.organizations.map(org => OrganizationModel(id = org.id, name = org.name)).toVector,
        query = message.query,
        status = message.status,
        categories = message.categories.map(cat => Category(id = cat.id, name = cat.name)).toVector,
        severity = Severity(id = message.severity.get.id, name = message.severity.get.name),
        frequency = message.frequency
      )

      // Create, or update the job schedule
      createOrUpdateJobSchedule()
    }

    // Handle updation of rule
    case message: RuleUpdated => {

      logger.info(s"Received updation of rule with id: ${message.id} in worker")

      // Set the rule for the worker
      rule = Rule(
        id = message.id,
        name = message.name,
        isGlobal = message.isGlobal,
        organizations = message.organizations.map(org => OrganizationModel(id = org.id, name = org.name)).toVector,
        query = message.query,
        status = message.status,
        categories = message.categories.map(cat => Category(id = cat.id, name = cat.name)).toVector,
        severity = Severity(id = message.severity.get.id, name = message.severity.get.name),
        frequency = message.frequency
      )

      // Create, or update the job schedule
      createOrUpdateJobSchedule()
    }

    // Handle deletion of the rule
    case message: RuleDeleted => {

      logger.info(s"Received deletion of rule with id: ${message.id} in worker with job id ${jobId}")

      // Delete the job schedule
      deleteJobSchedule()

      logger.info(s"Killing rule actor for rule with id: ${message.id} in worker")

      // Kill the actor
      self ! PoisonPill
    }

    // Handle the evaluation of the rule
    case RuleSchedule => {

      logger.info(s"Running Scheduler for rule : ${rule.id}")

      // Get the organizations to execute the rule by
      val organizationsToExecuteRuleAgainst = {
        if (rule.isGlobal) organizations
        else rule.organizations
      }

      // Get the futuon and try to dynamically use a res for all execution against each organization
      val futures = Future.sequence(

        organizationsToExecuteRuleAgainst.map(org => {

          val currentDate = Date.from(Instant.parse(new Date().toInstant.toString))
          val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
          formatter.setTimeZone(TimeZone.getTimeZone("UTC"))

          // Add org timestamp and log date to the query
          val query = ASTUtility.addOrganizations(org.name, rule.query)
          val timestamp_query = ASTUtility.addTimestampsToQuery(query, lower_bound_in_days, formatter.format(currentDate))
          val log_date_query = ASTUtility.addLogDate(timestamp_query)

          logger.info(s"Query for incident with rule ${rule.id} is : ${log_date_query}")

          log ? GetLogByOrganization(query = log_date_query, organization = Some(Organization(id = org.id, name = org.name)))

        })

      )

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
          Repository.createIncident(incident)

          // Get the users of the organization
          val organizationUsers = Await.result(user ? GetUsersByOrganization(res.organization.get.id), 5 minutes).asInstanceOf[Users].value.toSet

          // Combine the soc and organization users
          val users = socUsers ++ organizationUsers

          // Create the send email message
          users.map(user => {

            // Create scalate template and generate the notification message
            val engine = new TemplateEngine()
            val output = engine.layout("incident_notification.mustache", Map("user" -> user.name, "rule" -> rule.name, "organization" -> res.organization.get.name))

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

    // Unhandled logs
    case message: Any => logger.warn(s"Unhandled message - ${
      message
    }")
  }
}
