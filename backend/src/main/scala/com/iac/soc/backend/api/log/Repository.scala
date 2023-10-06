package com.iac.soc.backend.api.log

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.LogMapping.StatementResponse
import com.ibm.icu.util.Calendar
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{NamedDB, SQL}

import scala.concurrent.Future

private[log] class Repository()(implicit val system: ActorSystem) extends JsonSupport with LazyLogging {

  val CONFIG_NAMESPACE = "com.iac.soc.backend.api.log"

  lazy val config: Config = ConfigFactory.load()

  lazy val presto_uri = config.getString("db.presto_analyst.uri");

  lazy val presto_user = config.getString("db.presto_analyst.user")

  lazy val presto_catlog = config.getString("db.presto_analyst.catalog")

  lazy val presto_schema = config.getString("db.presto_analyst.schema")

  lazy val presto_source = config.getString("db.presto_analyst.source")

  def postStatement(query: String): Future[HttpResponse] = {

    val reqHeaders: scala.collection.immutable.Seq[HttpHeader] = scala.collection.immutable.Seq(

      RawHeader("X-Presto-Catalog", presto_catlog),

      RawHeader("X-Presto-Schema", presto_schema),

      RawHeader("X-Presto-User", presto_user),

      RawHeader("X-Presto-Source", presto_source)

    )

    val postEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, query)

    val statementRequest = HttpRequest(method = HttpMethods.POST, uri = presto_uri + "/v1/statement", reqHeaders, postEntity)

    val statementResponseFuture: Future[HttpResponse] = Http().singleRequest(statementRequest);

    statementResponseFuture

  }

  def getQueryStats(response: StatementResponse): Future[HttpResponse] = {

    val statementGetRequest = HttpRequest(method = HttpMethods.GET, uri = response.nextUri)

    val statementGetResponseFuture: Future[HttpResponse] = Http().singleRequest(statementGetRequest);

    statementGetResponseFuture

  }

  def getQueryResults(query_id: String, page: Int): Future[HttpResponse] = {


    val httpUri = presto_uri + "/v1/statement/" + query_id.replace("-", "/") + "/" + page

    //val httpUri = presto_uri + "/v1/statement/" + query_id + "/" + page

    val queryGetRequest = HttpRequest(method = HttpMethods.GET, uri = httpUri)

    logger.info(s"Generated URI :: ${httpUri}")

    val queryGetResponseFuture: Future[HttpResponse] = Http().singleRequest(queryGetRequest);

    queryGetResponseFuture

  }

  def getHistogramResult(query: String): List[(String, String)] = {

    logger.info(s"Dashboard Log Sumary API Histogram query started :: ${Calendar.getInstance().getTime()}")
    val histogramResult = NamedDB('presto_analyst) readOnly { implicit session =>

      SQL(query).map(rs =>

        rs.string("histogram")

      ).single().apply()

    }
    logger.info(s"Dashboard Log Sumary API Histogram query end :: ${Calendar.getInstance().getTime()}")

    if (histogramResult != None && histogramResult.get != "") {
      return sortHistogramResults(histogramResult.get)
    } else {
      return List.empty
    }

  }

  def sortHistogramResults(histogramResult: String): List[(String, String)] = {

    logger.info(s"Dashboard Log Sumary API Histogram result preparation started :: ${Calendar.getInstance().getTime()}")
    val rawHistogram = histogramResult.replace("{", "").replace("}", "")

    val timeBasedCount = rawHistogram.split(",")

    val keyValueTimeCount = timeBasedCount.map(_.split("="))

    val rawResultHistogram: List[Map[String, String]] = keyValueTimeCount.map { arr =>

      arr.grouped(2).map { a => a(0).trim -> a(1) }.toMap

    }.toList

    val sortedHistogramResult = rawResultHistogram.flatten.toMap.toSeq.sortBy(_._1).toList
    logger.info(s"Dashboard Log Sumary API Histogram result preparation end :: ${Calendar.getInstance().getTime()}")

    sortedHistogramResult

  }

  def getLogCount(query: String): Option[Int] = {

    logger.info(s"Dashboard Log Sumary API log query started :: ${Calendar.getInstance().getTime()}")

    val logResult = NamedDB('presto_analyst) readOnly { implicit session =>

      SQL(query).map(rs =>

        rs.int("count")

      ).single().apply()

    }
    logger.info(s"Dashboard Log Sumary API log query end :: ${Calendar.getInstance().getTime()}")

    logResult

  }

  def getCategoryResult(query: String): List[Map[String, Int]] = {

    logger.info(s"Dashboard Log Sumary API category query started :: ${Calendar.getInstance().getTime()}")

    val categoryResult = NamedDB('presto_analyst) readOnly { implicit session =>

      SQL(query).map(rs =>

        Map(rs.string("type") -> rs.string("count").toInt)

      ).list().apply()

    }
    logger.info(s"Dashboard Log Sumary API category query end :: ${Calendar.getInstance().getTime()}")

    categoryResult

  }

}