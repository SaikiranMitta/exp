package com.iac.soc.backend.api.common

import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.config._

object Datasource extends LazyLogging {


  def getConnection() = {

    try {

      DBs.setupAll()

    } catch {

      case e: Exception => {

        logger.error(s"Exception :: ${e.printStackTrace()}")

      }

    }

  }
}
