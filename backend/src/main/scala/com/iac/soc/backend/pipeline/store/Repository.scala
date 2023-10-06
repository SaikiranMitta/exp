package com.iac.soc.backend.pipeline.store

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{Date, TimeZone}

import com.iac.soc.backend.schemas._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kudu.client._

/**
  * The log sub-system repository
  */
private[store] class Repository extends LazyLogging {

  /**
    * The typesafe configuration
    */
  private[this] val config: Config = ConfigFactory.load()

  /**
    * Get the kudu masters
    */
  private[this] val kudu_masters: String = config.getString("kudu.masters")

  /**
    * Get the kudu table
    */
  private[this] val kudu_table: String = config.getString("kudu.table")

  logger.info(s"Using kudu configuration with masters: ${kudu_masters} and table: ${kudu_table}")

  /**
    * The kudu client
    */
  private[this] val client: KuduClient = new KuduClient.KuduClientBuilder(kudu_masters).build()

  /**
    * The kudu session for inserting logs within
    */
  private[this] val session: KuduSession = client.newSession()

  /**
    * Set flush mode to Manual flush
    */
  //session.setFlushMode(SessionConfiguration.FlushMode.MANUAL_FLUSH)

  /**
    * The lower bound value in days for adding timestamp to query using ASt
    */
  private[this] val lower_bound_in_days: String = config.getString("ast.lower-bound-in-days")

  val todayDate = Date.from(Instant.parse(new Date().toInstant.toString))

  val todayDateFormatter = new SimpleDateFormat("yyyy_MM_dd")

  val timezone = todayDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))

  var formatTableName = todayDateFormatter.format(todayDate)

  var table: KuduTable = client.openTable(kudu_table + "_" + formatTableName)

  /**
    * Inserts a log with endpoint schema to the data warehouse
    *
    * @param log the log to be inserted
    */
  def insertLogs(logs: Logs): Seq[Log] = {

    logger.info(s"Inserting log into data warehouse")

    try {

      val checkTodayDate = Date.from(Instant.parse(new Date().toInstant.toString))

      val checkTodayDateFormatter = new SimpleDateFormat("yyyy_MM_dd")

      checkTodayDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))

      val checkFormatTableName = checkTodayDateFormatter.format(checkTodayDate)

      logger.info(s"Today's date : ${formatTableName}, Check table date ${checkFormatTableName}")

      if (formatTableName != checkFormatTableName) {

        table = client.openTable(kudu_table + "_" + checkTodayDateFormatter.format(checkTodayDate))

        formatTableName = checkFormatTableName

      }

      val results: Seq[Log] = logs.value.map(log => {

        logger.info("Received Timestamp : " + log.timestamp)

        if (log.timestamp.get != "" && log.timestamp.get != null && log.organization.get != null && log.organization.get != "" && log.date.get != null && log.date.get != "" && log.`type`.get != null && log.`type`.get != "") {

          // Get the table insert option
          val insert = table.newInsert()

          // Create the row for insertion
          val row = insert.getRow

          // Add the log data to the row
          row.addString("id", log.id.get)
          row.addTimestamp("timestamp", Timestamp.from(Instant.parse(log.timestamp.get)))
          row.addString("message", log.message.get)
          row.addString("organization", log.organization.get)
          row.addString("type", log.`type`.get)
          row.addString("date", log.date.get)

          if (log.vendor != None && log.vendor.get != null && log.vendor.get != "") row.addString("vendor", log.vendor.get)
          if (log.product != None && log.product.get != null && log.product.get != "") row.addString("product", log.product.get)
          if (log.version != None && log.version.get != null && log.version.get != "") row.addString("version", log.version.get)
          if (log.servicehost != None && log.servicehost.get != null && log.servicehost.get != "") row.addString("servicehost", log.servicehost.get)
          if (log.servicetype != None && log.servicetype.get != null && log.servicetype.get != "") row.addString("servicetype", log.servicetype.get)
          if (log.loghost != None && log.loghost.get != null && log.loghost.get != "") row.addString("loghost", log.loghost.get)
          if (log.logsource != None && log.logsource.get != null && log.logsource.get != "") row.addString("logsource", log.logsource.get)
          if (log.severity != None && log.severity.get.isValidInt) row.addInt("severity", log.severity.get)
          if (log.protocol != None && log.protocol.get != null && log.protocol.get != "") row.addString("protocol", log.protocol.get)
          if (log.method != None && log.method.get != null && log.method.get != "") row.addString("method", log.method.get)
          if (log.status != None && log.status.get != null && log.status.get != "") row.addString("status", log.status.get)
          if (log.statuscode != None && log.statuscode.get != null && log.statuscode.get != "") row.addString("statuscode", log.statuscode.get)
          if (log.direction != None && log.direction.get.isValidInt) row.addInt("direction", log.direction.get)
          if (log.incomingbytes != None && log.incomingbytes.get.isValidLong) row.addLong("incomingbytes", log.incomingbytes.get)
          if (log.outgoingbytes != None && log.outgoingbytes.get.isValidLong) row.addLong("outgoingbytes", log.outgoingbytes.get.toInt)
          if (log.session != None && log.session.get != null && log.session.get != "") row.addString("session", log.session.get)
          if (log.account != None && log.account.get != null && log.account.get != "") row.addString("account", log.account.get)
          if (log.accountgroup != None && log.accountgroup.get != null && log.accountgroup.get != "") row.addString("accountgroup", log.accountgroup.get)
          if (log.sourceuser != None && log.sourceuser.get != null && log.sourceuser.get != "") row.addString("sourceuser", log.sourceuser.get)
          if (log.sourcehost != None && log.sourcehost.get != null && log.sourcehost.get != "") row.addString("sourcehost", log.sourcehost.get)
          if (log.sourcemac != None && log.sourcemac.get != null && log.sourcemac.get != "") row.addString("sourcemac", log.sourcemac.get)
          if (log.sourcedevicetype != None && log.sourcedevicetype.get != null && log.sourcedevicetype.get != "") row.addString("sourcedevicetype", log.sourcedevicetype.get)
          if (log.sourcedeviceos != None && log.sourcedeviceos.get != null && log.sourcedeviceos.get != "") row.addString("sourcedeviceos", log.sourcedeviceos.get)
          if (log.sourceuseragent != None && log.sourceuseragent.get != null && log.sourceuseragent.get != "") row.addString("sourceuseragent", log.sourceuseragent.get)
          if (log.sourceip != None && log.sourceip.get != null && log.sourceip.get != "") row.addString("sourceip", log.sourceip.get)
          if (log.sourceport != None && log.sourceport.get.isValidInt) row.addInt("sourceport", log.sourceport.get)
          if (log.sourcenatip != None && log.sourcenatip.get != null && log.sourcenatip.get != "") row.addString("sourcenatip", log.sourcenatip.get)
          if (log.sourcenatport != None && log.sourcenatport.get.isValidInt) row.addInt("sourcenatport", log.sourcenatport.get)
          if (log.sourcecategory != None && log.sourcecategory.get != null && log.sourcecategory.get != "") row.addString("sourcecategory", log.sourcecategory.get)
          if (log.sourcecity != None && log.sourcecity.get != null && log.sourcecity.get != "") row.addString("sourcecity", log.sourcecity.get)
          if (log.sourcecountry != None && log.sourcecountry.get != null && log.sourcecountry.get != "") row.addString("sourcecountry", log.sourcecountry.get)
          if (log.sourcelatitude != None && log.sourcelatitude.get.toString != "") row.addFloat("sourcelatitude", log.sourcelatitude.get)
          if (log.sourcelongitude != None && log.sourcelongitude.get.toString != "") row.addFloat("sourcelongitude", log.sourcelongitude.get)
          if (log.destinationuser != None && log.destinationuser.get != null && log.destinationuser.get != "") row.addString("destinationuser", log.destinationuser.get)
          if (log.destinationhost != None && log.destinationhost.get != null && log.destinationhost.get != "") row.addString("destinationhost", log.destinationhost.get)
          if (log.destinationmac != None && log.destinationmac.get != null && log.destinationmac.get != "") row.addString("destinationmac", log.destinationmac.get)
          if (log.destinationdevicetype != None && log.destinationdevicetype.get != null && log.destinationdevicetype.get != "") row.addString("destinationdevicetype", log.destinationdevicetype.get)
          if (log.destinationdeviceos != None && log.destinationdeviceos.get != null && log.destinationdeviceos.get != "") row.addString("destinationdeviceos", log.destinationdeviceos.get)
          if (log.destinationip != None && log.destinationip.get != null && log.destinationip.get != "") row.addString("destinationip", log.destinationip.get)
          if (log.destinationport != None && log.destinationport.get.toString != "") row.addInt("destinationport", log.destinationport.get)
          if (log.destinationnatip != None && log.destinationnatip.get != null && log.destinationnatip.get != "") row.addString("destinationnatip", log.destinationnatip.get)
          if (log.destinationnatport != None && log.destinationnatport.get.isValidInt) row.addInt("destinationnatport", log.destinationnatport.get)
          if (log.destinationcategory != None && log.destinationcategory.get != null && log.destinationcategory.get != "") row.addString("destinationcategory", log.destinationcategory.get)
          if (log.destinationcity != None && log.destinationcity.get != null && log.destinationcity.get != "") row.addString("destinationcity", log.destinationcity.get)
          if (log.destinationcountry != None && log.destinationcountry.get != null && log.destinationcountry.get != "") row.addString("destinationcountry", log.destinationcountry.get)
          if (log.destinationlatitude != None && log.destinationlatitude.get.toString != "") row.addFloat("destinationlatitude", log.destinationlatitude.get)
          if (log.destinationlongitude != None && log.destinationlongitude.get.toString != "") row.addFloat("destinationlongitude", log.destinationlongitude.get)
          if (log.resource != None && log.resource.get != null && log.resource.get != "") row.addString("resource", log.resource.get)
          if (log.resourcesize != None && log.resourcesize.get.isValidLong) row.addLong("resourcesize", log.resourcesize.get)
          if (log.resourcecategory != None && log.resourcecategory.get != null && log.resourcecategory.get != "") row.addString("resourcecategory", log.resourcecategory.get)
          if (log.signature != None && log.signature.get != null && log.signature.get != "") row.addString("signature", log.signature.get)
          if (log.signaturecategory != None && log.signaturecategory.get != null && log.signaturecategory.get != "") row.addString("signaturecategory", log.signaturecategory.get)
          if (log.application != None && log.application.get != null && log.application.get != "") row.addString("application", log.application.get)
          if (log.subject != None && log.subject.get != null && log.subject.get != "") row.addString("subject", log.subject.get)
          if (log.body != None && log.body.get != null && log.body.get != "") row.addString("body", log.body.get)
          if (log.contenttype != None && log.contenttype.get != null && log.contenttype.get != "") row.addString("contenttype", log.contenttype.get)
          if (log.resourcegroup != None && log.resourcegroup.get != null && log.resourcegroup.get != "") row.addString("resourcegroup", log.resourcegroup.get)
          if (log.resourcetype != None && log.resourcetype.get != null && log.resourcetype.get != "") row.addString("resourcetype", log.resourcetype.get)
          if (log.sha1 != None && log.sha1.get != null && log.sha1.get != "") row.addString("sha1", log.sha1.get)
          if (log.sha1Category != None && log.sha1Category.get != null && log.sha1Category.get != "") row.addString("sha1category", log.sha1Category.get)
          if (log.sha256 != None && log.sha256.get != null && log.sha256.get != "") row.addString("sha256", log.sha256.get)
          if (log.sha256Category != None && log.sha256Category.get != null && log.sha256Category.get != "") row.addString("sha256category", log.sha256Category.get)
          if (log.md5 != None && log.md5.get != null && log.md5.get != "") row.addString("md5", log.md5.get)
          if (log.md5Category != None && log.md5Category.get != null && log.md5Category.get != "") row.addString("md5category", log.md5Category.get)
          if (log.sourcegroup != None && log.sourcegroup.get != null && log.sourcegroup.get != "") row.addString("sourcegroup", log.sourcegroup.get)
          if (log.destinationgroup != None && log.destinationgroup.get != null && log.destinationgroup.get != "") row.addString("destinationgroup", log.destinationgroup.get)

          // Insert the log to the table
          session.apply(insert)

          logger.info(s"Successfully Inserted logs into data warehouse ${log.id}")

          log

        } else {

          logger.info(s"Invalid log ${log.id}")

          log

        }

      })

      results

    }
    catch {

      case e: Exception => {
        logger.error(s"Error inserting logs ${logs.value.map(_.id)} Exception is: ${e}")

        logs.value
      }

    }

  }


  /**
    * Inserts a log with endpoint schema to the data warehouse
    *
    * @param log the log to be inserted
    */
  def insertLog(log: Log): Log = {

    logger.info(s"Inserting log into data warehouse")

    try {

      val todayDate = Date.from(Instant.parse(new Date().toInstant.toString))
      val todayDateFormatter = new SimpleDateFormat("yyyy_MM_dd")
      val timezone = todayDateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"))

      val table: KuduTable = client.openTable(kudu_table + "_" + todayDateFormatter.format(todayDate))

      logger.info("Received Timestamp : " + log.timestamp)

      if (log.timestamp.get != "" && log.timestamp.get != null && log.organization.get != null && log.organization.get != "" && log.date.get != null && log.date.get != "" && log.`type`.get != null && log.`type`.get != "") {

        // Get the table insert option
        val insert = table.newInsert()

        // Create the row for insertion
        val row = insert.getRow

        // Add the log data to the row
        row.addString("id", log.id.get)
        row.addTimestamp("timestamp", Timestamp.from(Instant.parse(log.timestamp.get)))
        row.addString("message", log.message.get)
        row.addString("organization", log.organization.get)
        row.addString("type", log.`type`.get)
        row.addString("date", log.date.get)

        if (log.vendor != None && log.vendor.get != null && log.vendor.get != "") row.addString("vendor", log.vendor.get)
        if (log.product != None && log.product.get != null && log.product.get != "") row.addString("product", log.product.get)
        if (log.version != None && log.version.get != null && log.version.get != "") row.addString("version", log.version.get)
        if (log.servicehost != None && log.servicehost.get != null && log.servicehost.get != "") row.addString("servicehost", log.servicehost.get)
        if (log.servicetype != None && log.servicetype.get != null && log.servicetype.get != "") row.addString("servicetype", log.servicetype.get)
        if (log.loghost != None && log.loghost.get != null && log.loghost.get != "") row.addString("loghost", log.loghost.get)
        if (log.logsource != None && log.logsource.get != null && log.logsource.get != "") row.addString("logsource", log.logsource.get)
        if (log.severity != None && log.severity.get.isValidInt) row.addInt("severity", log.severity.get)
        if (log.protocol != None && log.protocol.get != null && log.protocol.get != "") row.addString("protocol", log.protocol.get)
        if (log.method != None && log.method.get != null && log.method.get != "") row.addString("method", log.method.get)
        if (log.status != None && log.status.get != null && log.status.get != "") row.addString("status", log.status.get)
        if (log.statuscode != None && log.statuscode.get != null && log.statuscode.get != "") row.addString("statuscode", log.statuscode.get)
        if (log.direction != None && log.direction.get.isValidInt) row.addInt("direction", log.direction.get)
        if (log.incomingbytes != None && log.incomingbytes.get.isValidLong) row.addLong("incomingbytes", log.incomingbytes.get)
        if (log.outgoingbytes != None && log.outgoingbytes.get.isValidLong) row.addLong("outgoingbytes", log.outgoingbytes.get.toInt)
        if (log.session != None && log.session.get != null && log.session.get != "") row.addString("session", log.session.get)
        if (log.account != None && log.account.get != null && log.account.get != "") row.addString("account", log.account.get)
        if (log.accountgroup != None && log.accountgroup.get != null && log.accountgroup.get != "") row.addString("accountgroup", log.accountgroup.get)
        if (log.sourceuser != None && log.sourceuser.get != null && log.sourceuser.get != "") row.addString("sourceuser", log.sourceuser.get)
        if (log.sourcehost != None && log.sourcehost.get != null && log.sourcehost.get != "") row.addString("sourcehost", log.sourcehost.get)
        if (log.sourcemac != None && log.sourcemac.get != null && log.sourcemac.get != "") row.addString("sourcemac", log.sourcemac.get)
        if (log.sourcedevicetype != None && log.sourcedevicetype.get != null && log.sourcedevicetype.get != "") row.addString("sourcedevicetype", log.sourcedevicetype.get)
        if (log.sourcedeviceos != None && log.sourcedeviceos.get != null && log.sourcedeviceos.get != "") row.addString("sourcedeviceos", log.sourcedeviceos.get)
        if (log.sourceuseragent != None && log.sourceuseragent.get != null && log.sourceuseragent.get != "") row.addString("sourceuseragent", log.sourceuseragent.get)
        if (log.sourceip != None && log.sourceip.get != null && log.sourceip.get != "") row.addString("sourceip", log.sourceip.get)
        if (log.sourceport != None && log.sourceport.get.isValidInt) row.addInt("sourceport", log.sourceport.get)
        if (log.sourcenatip != None && log.sourcenatip.get != null && log.sourcenatip.get != "") row.addString("sourcenatip", log.sourcenatip.get)
        if (log.sourcenatport != None && log.sourcenatport.get.isValidInt) row.addInt("sourcenatport", log.sourcenatport.get)
        if (log.sourcecategory != None && log.sourcecategory.get != null && log.sourcecategory.get != "") row.addString("sourcecategory", log.sourcecategory.get)
        if (log.sourcecity != None && log.sourcecity.get != null && log.sourcecity.get != "") row.addString("sourcecity", log.sourcecity.get)
        if (log.sourcecountry != None && log.sourcecountry.get != null && log.sourcecountry.get != "") row.addString("sourcecountry", log.sourcecountry.get)
        if (log.sourcelatitude != None && log.sourcelatitude.get.toString != "") row.addFloat("sourcelatitude", log.sourcelatitude.get)
        if (log.sourcelongitude != None && log.sourcelongitude.get.toString != "") row.addFloat("sourcelongitude", log.sourcelongitude.get)
        if (log.destinationuser != None && log.destinationuser.get != null && log.destinationuser.get != "") row.addString("destinationuser", log.destinationuser.get)
        if (log.destinationhost != None && log.destinationhost.get != null && log.destinationhost.get != "") row.addString("destinationhost", log.destinationhost.get)
        if (log.destinationmac != None && log.destinationmac.get != null && log.destinationmac.get != "") row.addString("destinationmac", log.destinationmac.get)
        if (log.destinationdevicetype != None && log.destinationdevicetype.get != null && log.destinationdevicetype.get != "") row.addString("destinationdevicetype", log.destinationdevicetype.get)
        if (log.destinationdeviceos != None && log.destinationdeviceos.get != null && log.destinationdeviceos.get != "") row.addString("destinationdeviceos", log.destinationdeviceos.get)
        if (log.destinationip != None && log.destinationip.get != null && log.destinationip.get != "") row.addString("destinationip", log.destinationip.get)
        if (log.destinationport != None && log.destinationport.get.toString != "") row.addInt("destinationport", log.destinationport.get)
        if (log.destinationnatip != None && log.destinationnatip.get != null && log.destinationnatip.get != "") row.addString("destinationnatip", log.destinationnatip.get)
        if (log.destinationnatport != None && log.destinationnatport.get.isValidInt) row.addInt("destinationnatport", log.destinationnatport.get)
        if (log.destinationcategory != None && log.destinationcategory.get != null && log.destinationcategory.get != "") row.addString("destinationcategory", log.destinationcategory.get)
        if (log.destinationcity != None && log.destinationcity.get != null && log.destinationcity.get != "") row.addString("destinationcity", log.destinationcity.get)
        if (log.destinationcountry != None && log.destinationcountry.get != null && log.destinationcountry.get != "") row.addString("destinationcountry", log.destinationcountry.get)
        if (log.destinationlatitude != None && log.destinationlatitude.get.toString != "") row.addFloat("destinationlatitude", log.destinationlatitude.get)
        if (log.destinationlongitude != None && log.destinationlongitude.get.toString != "") row.addFloat("destinationlongitude", log.destinationlongitude.get)
        if (log.resource != None && log.resource.get != null && log.resource.get != "") row.addString("resource", log.resource.get)
        if (log.resourcesize != None && log.resourcesize.get.isValidLong) row.addLong("resourcesize", log.resourcesize.get)
        if (log.resourcecategory != None && log.resourcecategory.get != null && log.resourcecategory.get != "") row.addString("resourcecategory", log.resourcecategory.get)
        if (log.signature != None && log.signature.get != null && log.signature.get != "") row.addString("signature", log.signature.get)
        if (log.signaturecategory != None && log.signaturecategory.get != null && log.signaturecategory.get != "") row.addString("signaturecategory", log.signaturecategory.get)
        if (log.application != None && log.application.get != null && log.application.get != "") row.addString("application", log.application.get)
        if (log.subject != None && log.subject.get != null && log.subject.get != "") row.addString("subject", log.subject.get)
        if (log.body != None && log.body.get != null && log.body.get != "") row.addString("body", log.body.get)
        if (log.contenttype != None && log.contenttype.get != null && log.contenttype.get != "") row.addString("contenttype", log.contenttype.get)
        if (log.resourcegroup != None && log.resourcegroup.get != null && log.resourcegroup.get != "") row.addString("resourcegroup", log.resourcegroup.get)
        if (log.resourcetype != None && log.resourcetype.get != null && log.resourcetype.get != "") row.addString("resourcetype", log.resourcetype.get)
        if (log.sha1 != None && log.sha1.get != null && log.sha1.get != "") row.addString("sha1", log.sha1.get)
        if (log.sha1Category != None && log.sha1Category.get != null && log.sha1Category.get != "") row.addString("sha1category", log.sha1Category.get)
        if (log.sha256 != None && log.sha256.get != null && log.sha256.get != "") row.addString("sha256", log.sha256.get)
        if (log.sha256Category != None && log.sha256Category.get != null && log.sha256Category.get != "") row.addString("sha256category", log.sha256Category.get)
        if (log.md5 != None && log.md5.get != null && log.md5.get != "") row.addString("md5", log.md5.get)
        if (log.md5Category != None && log.md5Category.get != null && log.md5Category.get != "") row.addString("md5category", log.md5Category.get)
        if (log.sourcegroup != None && log.sourcegroup.get != null && log.sourcegroup.get != "") row.addString("sourcegroup", log.sourcegroup.get)
        if (log.destinationgroup != None && log.destinationgroup.get != null && log.destinationgroup.get != "") row.addString("destinationgroup", log.destinationgroup.get)

        // Insert the log to the table
        session.apply(insert)

        logger.info(s"Successfully Inserted logs into data warehouse ${log.id}")

        log

      } else {

        logger.info(s"Invalid log ${log.id}")

        log

      }

    }
    catch {

      case e: Exception => {

        logger.error(s"Error inserting logs ${log.id} Exception is: ${e}")

        log

      }

    }

  }

}
