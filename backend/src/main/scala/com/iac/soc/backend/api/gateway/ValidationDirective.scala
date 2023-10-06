package com.iac.soc.backend.api.gateway

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives._
import com.iac.soc.backend.api.common.mapping.CommonMapping.{FieldErrorInfo, ModelValidationRejection}
import spray.json.DefaultJsonProtocol

object ValidationDirective extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)

  def validateModel[T](model: T)(implicit validator: Validator[T]): Directive1[T] = {

    validator(model) match {

      case Nil => provide(model)

      case errors: Seq[FieldErrorInfo] => reject(ModelValidationRejection(errors))

    }

  }

}
