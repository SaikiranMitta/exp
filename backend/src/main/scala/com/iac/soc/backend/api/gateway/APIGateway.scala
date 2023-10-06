package com.iac.soc.backend.api.gateway

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.iac.soc.backend.api.category.CategoryActor
import com.iac.soc.backend.api.gateway.routes.Routes
import com.iac.soc.backend.api.incident.{Manager => IncidentActor}
import com.iac.soc.backend.api.log.{Manager => LogActor}
import com.iac.soc.backend.api.organization.{Manager => OrganizationActor}
import com.iac.soc.backend.api.report.{Manager => ReportActor}
import com.iac.soc.backend.api.rule.{Manager => RuleActor}
import com.iac.soc.backend.api.logsources.{Manager => LogsourcesActor}
import com.iac.soc.backend.api.normalizer.{Manager => NormalizerActor}
import com.iac.soc.backend.api.dashboard.{Manager => DashboardActor}
import com.iac.soc.backend.api.threat.{Manager => ThreatActor}
import com.iac.soc.backend.api.user.Manager
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class APIGateway()(implicit val system: ActorSystem) extends CorsSupport with LazyLogging with Routes {

  val userActor: ActorRef = system.actorOf(Props[Manager].withDispatcher("api-dispatcher"), "UserActor")

  val main_config = ConfigFactory.load()

  val ruleActor: ActorRef = system.actorOf(Props[RuleActor].withDispatcher("api-dispatcher"), "RuleActor")

  val reportActor: ActorRef = system.actorOf(Props[ReportActor].withDispatcher("api-dispatcher"), "ReportActor")

  val organizationActor: ActorRef = system.actorOf(Props[OrganizationActor].withDispatcher("api-dispatcher"), "OrganizationActor")

  val categoryActor: ActorRef = system.actorOf(Props[CategoryActor].withDispatcher("api-dispatcher"), "CategoryActor")

  val incidentActor: ActorRef = system.actorOf(Props[IncidentActor].withDispatcher("api-dispatcher"), "IncidentActor")

  val logActor: ActorRef = system.actorOf(Props[LogActor].withDispatcher("api-dispatcher"), "LogActor")

  val logsourcesActor: ActorRef = system.actorOf(Props[LogsourcesActor].withDispatcher("api-dispatcher"), "LogsourcesActor")

  val normalizerActor: ActorRef = system.actorOf(Props[NormalizerActor].withDispatcher("api-dispatcher"), "NormalizerActor")

  val dashboardActor: ActorRef = system.actorOf(Props[DashboardActor].withDispatcher("api-dispatcher"), "DashboardActor")

  val threatActor: ActorRef = system.actorOf(Props[ThreatActor].withDispatcher("api-dispatcher"), "ThreatActor")

  var ruleSubSystem: ActorRef = null

  def setupAPI(ruleSubSystem: ActorRef) = {

    val main_config = ConfigFactory.load()

    val port = main_config.getInt("akka.http.port")

    lazy val route: Route = getRoutes()

    val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(corsHandler(route), "0.0.0.0", port);

    serverBinding.onComplete {

      case Success(bound) =>

        logger.info(s"HTTP Server running on port ${port}");

      case Failure(e) =>

        logger.error(s"Failed to start HTTP Server");

        e.printStackTrace();

        system.terminate();

    }

    Await.result(system.whenTerminated, Duration.Inf)

  }

}
