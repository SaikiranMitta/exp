package com.iac.soc.backend.api.common

import com.iac.soc.backend.api.user.messages.Messages.{CreateRequest, UpdateRequest}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.keycloak.representations.idm.{CredentialRepresentation, RequiredActionProviderRepresentation, UserRepresentation}

import scala.collection.JavaConverters._

final case class CustomException(private val message: String = "", private val cause: Throwable = None.orNull) extends Exception(message, cause)

object KeycloakUtils extends LazyLogging {

  val config: Config = ConfigFactory.load()

  private var keycloak: Keycloak = _

  val realm = config.getString("keycloak.realm")

  def initialize(): Unit = {

    keycloak = KeycloakBuilder.builder()
      .serverUrl(config.getString("keycloak.auth_server_url"))
      .realm(realm)
      .grantType(OAuth2Constants.PASSWORD)
      .clientId(config.getString("keycloak.backend_resource"))
      .username(config.getString("keycloak.admin_username"))
      .password(config.getString("keycloak.admin_password"))
      .build();

  }

  def createUser(userRequest: CreateRequest, user_id: Long): String = {

    initialize();

    var user: UserRepresentation = new UserRepresentation

    if (userRequest.status == "enabled") {

      user.setEnabled(true)

    } else {

      user.setEnabled(false)

    }

    user.setUsername(userRequest.username)

    user.setFirstName(userRequest.firstName)

    user.setLastName(userRequest.lastName)

    user.setEmail(userRequest.email)

    user.setRequiredActions(List("CONFIGURE_TOTP").asJava)

    /**
      * Set user Attributes like user_id and organizations
      */

    var setUserAttr = scala.collection.immutable.Map[String, java.util.List[String]]()

    if (userRequest.organizations.size > 0) {

      val org_ids = userRequest.organizations.map(_.id);

      setUserAttr += ("organizations" -> List(org_ids.mkString(",")).asJava)

    }

    if (userRequest.role != "") {

      logger.info(s"User creation with role :: ${userRequest.role}")

      setUserAttr += ("soc_role" -> List(userRequest.role).asJava)

    }

    setUserAttr += ("user_id" -> List(user_id.toString).asJava)

    user.setAttributes(setUserAttr.asJava)

    logger.info(s"Requesting keycloak to create user ${user.getEmail}, ${user.getUsername}")

    val realmResource = keycloak.realm(realm)

    val userResource = realmResource.users

    val response = userResource.create(user);

    if (response.getStatus == 201) {

      val userId = response.getLocation.getPath.split("/")(response.getLocation.getPath.split("/").size - 1)

      logger.info(s"User created in keycloak with user id :: ${userId}")

      /**
        * Set user password
        */

      /*val passwordCred = new CredentialRepresentation

      passwordCred.setTemporary(true)

      passwordCred.setType(CredentialRepresentation.PASSWORD)

      passwordCred.setValue(userRequest.password)

      userResource.get(userId).resetPassword(passwordCred)*/


      /**
        * Assign role to user
        */


      return userId;

    } else {

      logger.info("Keycloak User creation failed :: " + response.getStatusInfo + " " + response.getStatus)

      throw CustomException("Failed to create user in keycloak")

    }

  }

  def sentUpdatePasswordEmail(user_id: String): Unit = {

    logger.info(s"Sent update password email to user ${user_id}")

    initialize();

    val realmResource = keycloak.realm(realm)

    val userResource = realmResource.users

    userResource.get(user_id).executeActionsEmail(List("UPDATE_PASSWORD").asJava)

  }

  def updateUser(userRequest: UpdateRequest, user_id: Long): Unit = {

    initialize();

    val realmResource = keycloak.realm(realm)

    val userResource = realmResource.users

    val user_idpid = userRequest.idpUserId

    val userAttributes = userResource.get(user_idpid).toRepresentation.getAttributes.asScala

    var user: UserRepresentation = new UserRepresentation

    user.setUsername(userRequest.username)

    user.setFirstName(userRequest.firstName)

    user.setLastName(userRequest.lastName)

    user.setEmail(userRequest.email)

    if (userRequest.status == "enabled") {

      user.setEnabled(true)

    } else {

      user.setEnabled(false)

    }

    var setUserAttr = scala.collection.immutable.Map[String, java.util.List[String]]()

    if (userRequest.organizations.size > 0) {

      val org_ids = userRequest.organizations.map(_.id);

      setUserAttr += ("organizations" -> List(org_ids.mkString(",")).asJava)

    }

    if (userRequest.role != "") {

      logger.info(s"User creation with role :: ${userRequest.role}")

      setUserAttr += ("soc_role" -> List(userRequest.role).asJava)

    }

    setUserAttr += ("user_id" -> List(user_id.toString).asJava)

    user.setAttributes(setUserAttr.asJava)

    userResource.get(user_idpid).update(user)

  }

  def resetUserPassword(user_id: String, password: String): Unit = {

    initialize();

    val realmResource = keycloak.realm(realm)

    val userResource = realmResource.users

    val passwordCred = new CredentialRepresentation

    passwordCred.setTemporary(true)

    passwordCred.setType(CredentialRepresentation.PASSWORD)

    passwordCred.setValue(password)

    userResource.get(user_id).resetPassword(passwordCred)

    userResource.get(user_id).disableCredentialType(List("otp").asJava)

    var user: UserRepresentation = new UserRepresentation

    user.setRequiredActions(List("CONFIGURE_TOTP").asJava)

    userResource.get(user_id).update(user)

  }

  def deleteUser(user_id: String): Unit = {

    initialize();

    val realmResource = keycloak.realm(realm)

    val userResource = realmResource.users

    /*var user: UserRepresentation = new UserRepresentation

    user.setEnabled(false)

    userResource.get(user_id).update(user)*/

    userResource.get(user_id).remove()

  }


}