package com.iac.soc.backend.rule

import akka.actor.ActorRef
import akka.dispatch.Dispatcher
import org.quartz.{Scheduler => QuartzScheduler}
import org.quartz.impl.StdSchedulerFactory

import scala.concurrent.ExecutionContextExecutor

object Scheduler {

  val scheduler = new StdSchedulerFactory().getScheduler

  def getScheduler(user: ActorRef, notification: ActorRef, log: ActorRef, ec: ExecutionContextExecutor): QuartzScheduler = {

    // Set the execution context from the actor system
    scheduler.getContext.put("user", user)
    scheduler.getContext.put("notification", notification)
    scheduler.getContext.put("log", log)
    scheduler.getContext.put("ec", ec)

    // Start the scheduler
    scheduler.start()

    scheduler

  }

  def clearScheduler(): Unit = {
    scheduler.clear()
  }

}
