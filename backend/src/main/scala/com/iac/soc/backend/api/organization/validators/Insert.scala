package com.iac.soc.backend.api.organization.validators

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.FieldErrorInfo
import com.iac.soc.backend.api.gateway.Validator
import com.iac.soc.backend.api.organization.messages.Messages.CreateRequest
import spray.json.DefaultJsonProtocol
import com.iac.soc.backend.api.organization.Repository

object Insert extends Validator[CreateRequest] with SprayJsonSupport with DefaultJsonProtocol {

  def isOrgNameEmpty(name: String): Boolean = {

    if (name.isEmpty) true else false

  }

  def isOrgNameExists(name: String): Boolean = {
    if (Repository.getOrganizationByName(name).isEmpty) false else true
  }

  override def apply(model: CreateRequest): Seq[FieldErrorInfo] = {

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)

    val orgNameEmptyError: Option[FieldErrorInfo] = validationStage(isOrgNameEmpty(model.name), "name", "Organization name is a compulsory field")

    val orgNameDuplicateError: Option[FieldErrorInfo] = validationStage(isOrgNameExists(model.name), "name", "Organization name already exists")


    Seq(orgNameEmptyError, orgNameDuplicateError).flatten

  }

}
