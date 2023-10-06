package com.iac.soc.backend.log

import java.sql.Timestamp
import java.time.Instant

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kudu.client.{KuduClient, KuduSession, KuduTable}
import scalikejdbc.{NamedDB, SQL}

/**
  * The log sub-system repository
  */
private[log] object Repository extends LazyLogging {

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * Get the kudu masters
    */
 // private[this] val kudu_masters: String = config.getString("kudu.masters")

  /**
    * Get the kudu table
    */
  //private[this] val kudu_table: String = config.getString("kudu.table")

  //logger.info(s"Using kudu configuration with masters: ${kudu_masters} and table: ${kudu_table}")

  /**
    * The kudu client
    */
  //private[this] val client: KuduClient = new KuduClient.KuduClientBuilder(kudu_masters).build()

  /**
    * The kudu table where logs must be inserted to
    */
  //private[this] val table: KuduTable = client.openTable(kudu_table)

  /**
    * The kudu session for inserting logs within
    */
  //private[this] val session: KuduSession = client.newSession()

  /**
    * The lower bound value in days for adding timestamp to query using ASt
    */
  private[this] val lower_bound_in_days: String = config.getString("ast.lower-bound-in-days")


  /**
    * Gets the logs for an organization
    *
    * @param query            the query to be executed
    * @param organizationId   the id of the organization
    * @param organizationName the name of the organization
    * @return the results of the execution
    */
  def getLogByOrganization(query: String, organizationId: Int, organizationName: String): List[String] = {

    try {

      logger.info(s"Fetching logs for organization:: ${organizationName} for query ${query}")

      val logs: List[String] = NamedDB('presto_rules) readOnly { implicit session =>

        SQL(query).map(_.toMap().toString()).list().apply()

      }

      // Returning a dummy string; return null or empty string if nothing found
      if (logs.size > 0) {
        //return logs
        logs
      } else {
        List.empty
      }

    }
    catch {

      case e: Exception => {

        e.printStackTrace()

        logger.error("Error in fetching Logs By Organization")

        null

      }

    }

  }


}
