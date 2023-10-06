package com.iac.soc.backend.api.dashboard

import akka.actor.ActorSystem
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.Utils.escapeString
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.ibm.icu.util.Calendar
import com.typesafe.scalalogging.LazyLogging
import scalikejdbc.{AutoSession, NamedDB, SQL}

private[dashboard] class Repository()(implicit val system: ActorSystem) extends JsonSupport with LazyLogging {

  implicit val session = AutoSession

  def postStatement(query: String): List[Map[String, Any]] = {

    logger.info(s"Dashboard API presto query started :: ${Calendar.getInstance().getTime()}")

    val logResult: List[Map[String, Any]] = NamedDB('presto_analyst) readOnly { implicit session =>

      SQL(query).map(_.toMap()).list().apply()

    }
    logger.info(s"Dashboard API presto query finished :: ${Calendar.getInstance().getTime()}")

    logResult

  }

  def getAlertsCount(auth_details: Claims, params: Map[String, String], range: Int): Map[String, List[Double]] = {

    var group_by = ""
    var projection = ""
    var join_by = ""
    var whr = ""

    val by = params.get("by").get.toString
    val organizations = params.get("organization")
    val category = params.get("category")
    val severity = params.get("severity")
    val rules = params.get("rule")
    val to = params.get("to").get
    val from = params.get("from").get
    val buckets = params.get("buckets").get.toInt

    by match {
      case "severity" => {
        projection = "r.severity as name"
        group_by = "r.severity"
        join_by = " inner join rules r on i.rule_id = r.id "
        join_by += "inner join rules_categories rc on (r.id=rc.rule_id and rc.is_active=true) "
      }
      case "category" => {
        projection = "c.name as name"
        group_by = "c.name"
        join_by = " inner join rules r on i.rule_id = r.id "
        join_by += "inner join rules_categories rc on (r.id=rc.rule_id and rc.is_active=true) "
        join_by += " inner join categories c on c.id = rc.category_id "

        //        inner join iac_dev.rules_categories rc on r.id=rc.rule_id
        //        inner join iac_dev.categories c on c.id = rc.category_id
      }
      case _ => {
        projection = "r.severity as name"
        group_by = "r.severity"
        join_by = " inner join rules r on i.rule_id = r.id "
      }
    }

    if (!organizations.isEmpty) {
      whr += s" AND i.organization_id in (${organizations.get})"
    } else {

      if (auth_details.organizations != null && auth_details.organizations.size > 0) {

        whr = whr + s" AND i.organization_id in (${escapeString(auth_details.organizations.mkString(","))}) "

      }

    }

    if (!rules.isEmpty) {
      whr += s" AND r.id in (${rules.get})"
    }

    if (!category.isEmpty) {
      whr += s" AND rc.category_id in (${category.get})"
    }

    if (!severity.isEmpty) {
      whr += s" AND r.severity in ('${severity.get.split(",").mkString("','")}')"
    }

//    val sql =
//      s"""SELECT ${projection}, FLOOR(UNIX_TIMESTAMP(i.created_on) / FLOOR( (UNIX_TIMESTAMP("${to}") - UNIX_TIMESTAMP("${from}") ) / ${buckets})) AS time, COUNT(*) AS alerts_count
//                 FROM incidents i ${join_by} WHERE i.created_on >= "${from}" and i.created_on<= "${to}" ${whr} GROUP BY time, ${group_by} order by time """
//    val sql =
//        s"""SELECT ${projection}, FLOOR(UNIX_TIMESTAMP(i.created_on) / ${range}) AS time, COUNT(*) AS alerts_count
//                 FROM incidents i ${join_by} WHERE i.created_on >= "${from}" and i.created_on<= "${to}" ${whr} GROUP BY time, ${group_by} order by time """
    val sql =
          s"""SELECT ${projection}, (UNIX_TIMESTAMP(i.created_on)*1000) AS time
                 FROM incidents i ${join_by} WHERE i.created_on >= "${from}" and i.created_on<= "${to}" ${whr} order by time """

    logger.info(s"SQL for test: ${sql}")
    val results = SQL(sql).map(rs =>
      Map("by" -> rs.string("name"), "time" -> rs.string("time"))
    ).list().apply()

    logger.info(s"--->>>${results.groupBy(_ ("by"))}")

    val resultGroup = results.groupBy(_ ("by"))

    val customResults: scala.collection.mutable.Map[String, List[Double]] = scala.collection.mutable.Map.empty[String, List[Double]]

    for ((k, v) <- resultGroup) {

      val l = v.map(_ ("time").toDouble)

      customResults += (k -> l)
    }

    return customResults.toMap;

  }
}
