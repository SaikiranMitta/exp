package com.iac.soc.backend

import akka.actor.Actor
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.util.Timeout
import com.iac.soc.backend.rules.messages.RestartShardOnNodeExit
import com.typesafe.scalalogging.LazyLogging
import akka.pattern.ask
import com.iac.soc.backend.pipeline.ingestion.RestartS3Sharding
import com.iac.soc.backend.threat.messages.RestartThreatSharding

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ClusterDomainEventListener extends Actor with LazyLogging {

  Cluster(context.system).subscribe(self, classOf[MemberExited], classOf[MemberUp])

  val clusterAddress = Cluster(context.system).selfAddress

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val timeout: Timeout = Timeout(2 minutes)

  /**
    * The default timeout for the enrichment worker actor
    */
  val actorSelectionTimeout: Duration = 2 minutes

  /**
    * The default timeout for the enrichment worker actor
    */
  implicit val ec: ExecutionContext = context.dispatcher

  def receive = {

    case MemberUp(member) => {

      try {

        if (!clusterAddress.equals(member.address)) {

          logger.info(s"Cluster Status :: $member UP.")

          /*val ruleManagerFuture = context.system.actorSelection("akka://backend/user/backend/rule-manager").resolveOne()
          val ruleManager = Await.result(ruleManagerFuture, actorSelectionTimeout)

          val ingestionManagerFuture = context.system.actorSelection("akka://backend/user/backend/pipeline/ingestion").resolveOne()
          val ingestionManager = Await.result(ingestionManagerFuture, actorSelectionTimeout)

          val threatManagerFuture = context.system.actorSelection("akka://backend/user/backend/threat").resolveOne()
          val threatManager = Await.result(threatManagerFuture, actorSelectionTimeout)
          */

          val ruleManager = context.system.actorSelection("akka://backend/user/backend/rule-manager")
          val ingestionManager = context.system.actorSelection("akka://backend/user/backend/pipeline/ingestion")
          val threatManager = context.system.actorSelection("akka://backend/user/backend/threat")

          val restartShardOnNodeExit = RestartShardOnNodeExit(member.toString())
          val restartS3Sharding = RestartS3Sharding(member.toString())
          val restartThreatSharding = RestartThreatSharding(member.toString())

          val delayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of rule on member up ${member}")
            ruleManager ! restartShardOnNodeExit
          })

          val S3Delayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of s3 on member up ${member}")
            ingestionManager ! restartS3Sharding
          })

          val threatDelayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of threat on member up ${member}")
            threatManager ! restartThreatSharding
          })

          logger.info("Message sent to restart the shard")
        }

      } catch {

        case ex: Exception => {

          ex.printStackTrace()
          logger.info(s"Failed to reallocate shard while member up : ${ex}")

        }

      }
    }
    case MemberExited(member) => {

      try {

        if (!clusterAddress.equals(member.address)) {

          logger.info(s"Cluster Status Member ${member} is down")

          /*val ruleManagerFuture = context.system.actorSelection("akka://backend/user/backend/rule-manager").resolveOne()
          val ruleManager = Await.result(ruleManagerFuture, actorSelectionTimeout)

          val ingestionManagerFuture = context.system.actorSelection("akka://backend/user/backend/pipeline/ingestion").resolveOne()
          val ingestionManager = Await.result(ingestionManagerFuture, actorSelectionTimeout)

          val threatManagerFuture = context.system.actorSelection("akka://backend/user/backend/threat").resolveOne()
          val threatManager = Await.result(threatManagerFuture, actorSelectionTimeout)*/

          val ruleManager = context.system.actorSelection("akka://backend/user/backend/rule-manager")
          val ingestionManager = context.system.actorSelection("akka://backend/user/backend/pipeline/ingestion")
          val threatManager = context.system.actorSelection("akka://backend/user/backend/threat")

          val restartShardOnNodeExit = RestartShardOnNodeExit(member.toString())
          val restartS3Sharding = RestartS3Sharding(member.toString())
          val restartThreatSharding = RestartThreatSharding(member.toString())

          val ruleDelayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of rule on member exit ${member}")
            ruleManager ! restartShardOnNodeExit
          })

          val S3Delayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of s3 on member exit ${member}")
            ingestionManager ! restartS3Sharding
          })

          val threatDelayed = akka.pattern.after(30.seconds, using = context.system.scheduler)(Future.successful {
            logger.info(s"Going to fire restart shard of threat on member exit ${member}")
            threatManager ! restartThreatSharding
          })

          logger.info("Message sent to restart the shard")
        }

      } catch {

        case ex: Exception => {

          ex.printStackTrace()
          logger.info(s"Failed to reallocate shard while member exit : ${ex}")

        }

      }

    }
    case MemberRemoved(member, previousState) => {

      logger.info(s"Cluster Status :: $member Removed.")

    }
    case UnreachableMember(member) => {

      logger.info(s"Cluster Status :: $member Unreachable.")

    }
    case ReachableMember(member) => logger.info(s"Cluster Status :: $member UP.")
    case s: CurrentClusterState => logger.info(s"cluster state: $s")

  }

  override def postStop(): Unit = {
    Cluster(context.system).unsubscribe(self)
    super.postStop()
  }
}
