package com.iac.soc.backend.utility

import org.apache.camel.impl.{DefaultCamelContext, SimpleRegistry}

object Camel {

  val registry = new SimpleRegistry()
  val context = new DefaultCamelContext(registry)

  context.start()

  def getContext(): DefaultCamelContext = {
    context
  }

  def shutdown(): Unit = {

    // Wait for 30 seconds
    context.getShutdownStrategy.setTimeout(30)

    context.shutdown()
  }
}
