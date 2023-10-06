package com.iac.soc.backend.api.user.validators

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.iac.soc.backend.api.common.mapping.CommonMapping.FieldErrorInfo
import com.iac.soc.backend.api.common.messages.Messages.Organization
import com.iac.soc.backend.api.gateway.Validator
import com.iac.soc.backend.api.user.Repository
import com.iac.soc.backend.api.user.messages.Messages.{UpdateRequest}
import spray.json.DefaultJsonProtocol

object Update extends Validator[UpdateRequest] with SprayJsonSupport with DefaultJsonProtocol {

  def isUsernameEmpty(username: String): Boolean = {

    if (username.isEmpty) true else false

  }

  def usernameExists(username: String): Boolean = {

    if (Repository.getUserByUsername(username).isEmpty) false else true

  }

  def isEmailEmpty(email: String): Boolean = {

    if (email.isEmpty) true else false

  }

  def userExists(email: String, username: String): Boolean = {

    if (Repository.getCheckUserExists(email, username).isEmpty) false else true

  }

  def checkRoleOrganization(role: String, organization: Seq[Organization]): Boolean = {

    if (role.isEmpty && organization.isEmpty) true else false

  }

  override def apply(model: UpdateRequest): Seq[FieldErrorInfo] = {

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)

    val usernameEmptyError: Option[FieldErrorInfo] = validationStage(isUsernameEmpty(model.username), "username", "Username is a Compulsory field")

    //    val usernameCheckError: Option[FieldErrorInfo] = validationStage(usernameExists(model.username), "username", "Username already exists")

    val emailEmptyError: Option[FieldErrorInfo] = validationStage(isEmailEmpty(model.email), "email", "Email is a Compulsory field")

    val emailCheckError: Option[FieldErrorInfo] = validationStage(userExists(model.username, model.email), "email", "Email already exists")

    val roleOrgError: Option[FieldErrorInfo] = validationStage(checkRoleOrganization(model.role, model.organizations), "role", "Organization is Compulsory if no role is selected")

    Seq(usernameEmptyError, emailEmptyError, emailCheckError, roleOrgError).flatten

  }

}