
package com.iac.soc.backend.api.common.mapping

object CategoryMapping {

  final case class Category(name: String, id: Long);

  final case class Categories(category: List[Category]);

  final case class CategoriesReponse(status_code: Int, message: String, categories: List[Category]);

}
