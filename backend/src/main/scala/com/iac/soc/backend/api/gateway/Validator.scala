package com.iac.soc.backend.api.gateway

import com.iac.soc.backend.api.common.mapping.CommonMapping.FieldErrorInfo

trait Validator[T] extends (T => Seq[FieldErrorInfo]) {

  protected def validationStage(rule: Boolean, fieldName: String, errorText: String): Option[FieldErrorInfo] =

    if (rule) Some(FieldErrorInfo(fieldName, errorText)) else None

}