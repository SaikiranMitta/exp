package com.iac.soc.backend.api.common

import com.iac.soc.backend.api.common.mapping.CategoryMapping.Category
import scalikejdbc._

object CategoryService {


  implicit val session = AutoSession

  def getCategories(): List[Category] = {

    /**
      * Execute Query
      */
    val sql = "select name, id from categories where is_active = true "

    val catList = SQL(sql).map(rs =>

      Category(
        rs.string("name"),
        rs.int("id")
      )).list().apply()

    return catList

  }

}