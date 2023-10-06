package com.iac.soc.backend.api.gateway.routes

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import com.iac.soc.backend.api.category.CategoryActor.getCategories
import com.iac.soc.backend.api.common.JsonSupport
import com.iac.soc.backend.api.common.mapping.CategoryMapping.CategoriesReponse
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims
import fr.davit.akka.http.scaladsl.marshallers.scalapb.ScalaPBJsonSupport

import scala.concurrent.Future

object Categories extends JsonSupport with ScalaPBJsonSupport {

  def getRoutes(statusMap: Map[Int, StatusCode], auth_details: Claims,  categoryActor: ActorRef)(implicit  timeout: Timeout): Route = {

    lazy val routes: Route =

      concat(

        path("categories") {

          concat(

            get {

              val category_details: Future[CategoriesReponse] = (categoryActor ? getCategories(auth_details)).mapTo[CategoriesReponse]

              onSuccess(category_details) { response =>

                complete(statusMap(response.status_code), response)

              }

            }

          )

        }

      )

    routes

  }

}
