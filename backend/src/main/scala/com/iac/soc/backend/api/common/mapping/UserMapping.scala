
package com.iac.soc.backend.api.common.mapping

object UserMapping {

  case class Claims(sub: String, user_id: Long, organizations: List[String], roles: String)

}