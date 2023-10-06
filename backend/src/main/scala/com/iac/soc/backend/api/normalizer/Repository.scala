package com.iac.soc.backend.api.normalizer

import com.iac.soc.backend.api.common.Utils.escapeString
import com.iac.soc.backend.api.common.mapping.NormalizerMapping._
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import scalikejdbc._

private[normalizer] object Repository {

  implicit val session = AutoSession

  def getNormalizerCount(auth_details: Claims, params: Map[String, String]): Int = {

    val getCountSql = s" select count(id) as total_count from normalizer";

    val totalCount = SQL(getCountSql).map(rs =>

      rs.int("total_count")

    ).single().apply()

    return totalCount.get;

  }

  def createNormalizer(auth_details: Claims, normalizer: NormalizerInsert): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""insert into normalizer(`filters`, `mapping`, `grok_pattern`, `log_source_id`, `normalizer_type`, `is_global`, `created_by`, `created_on`, `updated_by`, `updated_on`) VALUES ('${escapeString(normalizer.filters)}', '${escapeString(normalizer.mapping)}', '${escapeString(normalizer.grokPattern)}', ${normalizer.logSourceId}, '${escapeString(normalizer.normalizerType)}', ${normalizer.isGlobal}, ${auth_details.user_id}, NOW(), ${auth_details.user_id}, NOW())"""
    println(ins_sql);

    source_id = SQL(ins_sql).updateAndReturnGeneratedKey.apply()

    return source_id;
  }

  def updateNormalizer(auth_details: Claims, normalizer: NormalizerUpdate, normalizer_id: Int): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""update normalizer set `filters` = '${escapeString(normalizer.filters)}', `mapping` = '${escapeString(normalizer.mapping)}', `grok_pattern` = '${escapeString(normalizer.grokPattern)}', `log_source_id` = ${normalizer.logSourceId}, `normalizer_type` = '${escapeString(normalizer.normalizerType)}', `is_global` = ${normalizer.isGlobal}, `updated_by` = ${auth_details.user_id}, `updated_on` = NOW() WHERE id = ${normalizer_id}"""

    SQL(ins_sql).update.apply()
  }

  def deleteNormalizer(auth_details: Claims, normalizer_id: Int): Long = {
    var source_id: Long = 0;

    val ins_sql = s"""delete from normalizer WHERE id = ${normalizer_id}"""

    SQL(ins_sql).update.apply()
  }

  def getNormalizers(auth_details: Claims, params: Map[String, String]): List[NormalizerResponse] = {

    var page = 1

    var size = 5

    if (params.contains("page") && params.contains("size")) {

      page = params("page").toInt
      size = params("size").toInt

    }

    val sql = s" select id, filters, mapping, grok_pattern, log_source_id, normalizer_type, is_global from normalizer " +
      s" limit ${(page - 1) * size}, ${size}";

    val logsources = SQL(sql).map(rs =>

      NormalizerResponse(
        rs.int("id"),
        rs.string("filters"),
        rs.string("mapping"),
        rs.string("grok_pattern"),
        rs.int("log_source_id"),
        rs.string("normalizer_type"),
        rs.int("is_global")

      )).list().apply()

    return logsources;

  }
}
