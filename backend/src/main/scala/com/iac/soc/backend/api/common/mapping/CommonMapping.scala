
package com.iac.soc.backend.api.common.mapping

import akka.http.scaladsl.server.Rejection

object CommonMapping {

  final case class ActionPerformed(status_code: Int, message: String)

  final case class FieldErrorInfo(name: String, error: String)

  final case class ModelValidationRejection(invalidFields: Seq[FieldErrorInfo]) extends Rejection

}