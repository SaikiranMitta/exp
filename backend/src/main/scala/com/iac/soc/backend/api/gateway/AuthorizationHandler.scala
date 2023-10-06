package com.iac.soc.backend.api.gateway

import java.math.BigInteger
import java.security.spec.RSAPublicKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, RequestContext}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.keycloak.RSATokenVerifier
import org.keycloak.adapters.{KeycloakDeployment, KeycloakDeploymentBuilder}
import org.keycloak.jose.jws.AlgorithmType
import org.keycloak.representations.IDToken
import org.keycloak.representations.adapters.config._
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait AuthorizationHandler extends SprayJsonSupport with DefaultJsonProtocol with LazyLogging {

  case class Keys(keys: Seq[KeyData])

  case class KeyData(kid: String, n: String, e: String)

  lazy val config: Config = ConfigFactory.load()

  val configuration = new AdapterConfig

  logger.info("Keycloak Host :: " + config.getString("keycloak.auth_server_url"))

  configuration.setRealm(config.getString("keycloak.realm"))
  configuration.setAuthServerUrl(config.getString("keycloak.auth_server_url"))
  configuration.setSslRequired(config.getString("keycloak.ssl_required"))
  configuration.setResource(config.getString("keycloak.resource"))
  configuration.setPublicClient(config.getBoolean("keycloak.public_client"))
  configuration.setConfidentialPort(config.getInt("keycloak.confidential_port"))

  val keycloakDeployment: KeycloakDeployment = KeycloakDeploymentBuilder.build(configuration)

  implicit val keyDataFormat = jsonFormat3(KeyData)

  implicit val keysFormat = jsonFormat1(Keys)

  var publicKeys: Future[Map[String, PublicKey]] = _

  implicit val system: ActorSystem

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  implicit val ec: ExecutionContext = system.dispatchers.lookup("api-dispatcher")

  def initializeKeycloak()(): Unit = {

    publicKeys = Http().singleRequest(HttpRequest(uri = keycloakDeployment.getJwksUrl)).flatMap(response => {

      Unmarshal(response).to[Keys].map(_.keys.map(k => (k.kid, generateKey(k))).toMap)

    })

  }

  private def generateKey(keyData: KeyData): PublicKey = {

    val keyFactory = KeyFactory.getInstance(AlgorithmType.RSA.toString)

    val urlDecoder = Base64.getUrlDecoder

    val modulus = new BigInteger(1, urlDecoder.decode(keyData.n))

    val publicExponent = new BigInteger(1, urlDecoder.decode(keyData.e))

    keyFactory.generatePublic(new RSAPublicKeySpec(modulus, publicExponent))
  }

  def authenticate: Directive1[Claims] = {

    extractCredentials.flatMap {

      case Some(OAuth2BearerToken(token)) =>

        onComplete(verifyToken(token)).flatMap {

          case Success(Some(t)) =>

            logger.info(s"Recevied token ${t}")

            if (t.getId() != "" && t.getOtherClaims.containsKey("user_id")) {

              logger.info(s"Recevied token for user ${t.getOtherClaims.get("user_id")}")

              if (t.getOtherClaims.containsKey("organizations")) {

                logger.info("Received Organizations in token")

                val claims = Claims(t.getId, t.getOtherClaims.get("user_id").toString.toLong, t.getOtherClaims.get("organizations").toString.split(",").toList, if (t.getOtherClaims.containsKey("soc_role")) t.getOtherClaims.get("soc_role").toString else null)

                logger.info("sending claims to next route")

                provide(claims)

              } else {

                logger.info("No Organizations received in token")

                val claims = Claims(t.getId, t.getOtherClaims.get("user_id").toString.toLong, null, if (t.getOtherClaims.containsKey("soc_role")) t.getOtherClaims.get("soc_role").toString else null)

                provide(claims)

              }

            } else {

              logger.info("Unauthorized request. Token do not have user id in token")

              complete(StatusCodes.Unauthorized)

            }

          case _ => {

            logger.info("Unauthorized request. Exception while validating token")

            complete(StatusCodes.Unauthorized)

          }

        }

      case _ => {

        logger.info("Unauthorized request. Error while verifyToken")

        complete(StatusCodes.Unauthorized)

      }

    }

  }

  def verifyToken(token: String): Future[Option[IDToken]] = {

    try {

      val tokenVerifier = RSATokenVerifier.create(token).realmUrl(keycloakDeployment.getRealmInfoUrl)

      val publicKey_temp = publicKeys.map(_.get(tokenVerifier.getHeader.getKeyId))

      publicKey_temp.onComplete {

        case Success(pk) => val token = tokenVerifier.publicKey(pk.get).verify().getToken

        case Failure(t) => {

          logger.error("Authentication Failure")
          t.printStackTrace()

        }

      }

      for {

        publicKey <- publicKeys.map(_.get(tokenVerifier.getHeader.getKeyId))

      } yield publicKey match {

        case Some(pk) => {

          val token = tokenVerifier.publicKey(pk).verify().getToken

          Some(token)

        }
        case None => {

          logger.info("No Public key found")

          None

        }

      }
    } catch {

      case e: Exception => {

        e.printStackTrace()

        logger.error("Exception in verify token ")

        null
      }

    }

  }

  def authorization(ctx: RequestContext, auth_details: Claims): Directive1[Boolean] = {

    logger.info(s"Verifying authorization for user ${auth_details.user_id}")

    val allowed_roles = List("site_admin", "admin")

    val request_method = ctx.request.method.name()

    val soc_role = auth_details.roles

    var auth_flag = false;

    if (soc_role != null) {

      allowed_roles.foreach { role =>

        if (soc_role.equalsIgnoreCase(role)) {

          logger.info(s"SOC role ${soc_role} found in token for user ${auth_details.user_id}")

          auth_flag = true

        }

      }

    } else {

      logger.info(s"No SOC role found in token for user ${auth_details.user_id}")
      logger.info(s"This is for URL ${ctx.request.uri.path}")

    }

    if (auth_flag == false) {

      logger.info(s"Verifying access for user ${auth_details.user_id}")
      logger.info(s"This is for URL ${ctx.request.uri.path}")

      if (request_method == "GET" || (ctx.request.uri.path.toString().toLowerCase().equals("/api/v1/logs") && request_method == "POST") || (ctx.request.uri.path.toString().toLowerCase().equals("/api/v1/logs/summary") && request_method == "POST") || (ctx.request.uri.path.toString().toLowerCase().equals("/api/v1/dashboard") && request_method == "POST")) {

        auth_flag = true

      }

    }

    if (auth_flag == false) {

      logger.info(s"Request is not authorize for user ${auth_details.user_id}")
      logger.info(s"This is for URL ${ctx.request.uri.path}")

      complete(StatusCodes.Unauthorized)

    } else {

      provide(true)

    }

  }

}