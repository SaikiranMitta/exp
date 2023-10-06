package com.iac.soc.backend.api.logsources

import com.iac.soc.backend.api.common.Utils.escapeString
import com.iac.soc.backend.api.common.Encryption.{encrypt}
import com.iac.soc.backend.api.common.mapping.LogsourcesMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import scalikejdbc._

private[logsources] object Repository {

  implicit val session = AutoSession

  def getLogsourcesCount(auth_details: Claims, params: Map[String, String]): Int = {

    val getCountSql = s" select count(id) as total_count from log_sources";

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def createLogsource(auth_details: Claims, logsource: LogsourceInsert): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""insert into log_sources(`type`, `port`, `bucket`, `access_key`, `secret_key`, `created_by`, `created_on`, `updated_by`, `updated_on`) VALUES ('${escapeString(logsource.source)}','${escapeString(logsource.port.getOrElse(""))}', '${escapeString(encrypt(logsource.bucket.getOrElse("")))}', '${escapeString(encrypt(logsource.accessKey.getOrElse("")))}', '${escapeString(encrypt(logsource.secretKey.getOrElse("")))}', ${auth_details.user_id}, NOW(), ${auth_details.user_id}, NOW())"""

    source_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    return source_id;
  }

  def updateLogsource(auth_details: Claims, logsource: LogsourceUpdate, logsource_id: Int): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""update log_sources set `type` = '${escapeString(logsource.source)}', `port` = '${escapeString(logsource.port.getOrElse(""))}', `bucket` = '${escapeString(logsource.bucket.getOrElse(""))}', `access_key` = '${escapeString(logsource.accessKey.getOrElse(""))}', `secret_key` = '${escapeString(logsource.secretKey.getOrElse(""))}', `updated_by` = ${auth_details.user_id}, `updated_on` = NOW() WHERE id = ${logsource_id}"""

    SQL(ins_sql).update.apply()
  }

  def deleteLogsource(auth_details: Claims, logsource_id: Int): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""delete from log_sources WHERE id = ${logsource_id}"""

    SQL(ins_sql).update.apply()
  }

  def getLogsources(auth_details: Claims, params: Map[String, String]): List[LogsourceResponse] = {

    var page = 1

    var size = 5

    if (params.contains("page") && params.contains("size")) {

      page = params("page").toInt
      size = params("size").toInt

    }

    val sql = s" select id, type, port, bucket, access_key, secret_key from log_sources " +
      s" limit ${(page - 1) * size}, ${size}";

    val logsources = SQL(sql).map(rs =>

      LogsourceResponse(
        rs.int("id"),
        rs.string("type"),
        rs.stringOpt("port"),
        rs.stringOpt("bucket"),
        rs.stringOpt("access_key"),
        rs.stringOpt("secret_key")

      )).list().apply()

    return logsources;

  }
}
