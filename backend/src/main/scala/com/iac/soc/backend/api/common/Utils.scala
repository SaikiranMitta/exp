package com.iac.soc.backend.api.common

object Utils {

  def escapeString(sql: Any): String = {

    val sql_escaped = sql.toString.replaceAll("\\\\", "\\\\\\\\")
      .replaceAll("'", "\\\\'")
      .replaceAll("\"", "\\\\\"")
      .replaceAll("\b", "\\\\b")
      .replaceAll("\n", "\\\\n")
      .replaceAll("\r", "\\\\r")
      .replaceAll("\t", "\\\\t")
      .replaceAll("\\x1A", "\\\\Z")
      .replaceAll("\\x00", "\\\\0")

    sql_escaped

  }


}
