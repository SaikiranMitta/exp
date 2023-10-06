
package com.iac.soc.backend.api.category

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import com.iac.soc.backend.api.common.CategoryService
import com.iac.soc.backend.api.common.mapping.CategoryMapping.{CategoriesReponse, Category}
import com.iac.soc.backend.api.common.mapping.UserMapping.Claims

object CategoryActor {

  final case class ActionPerformed(response: ToResponseMarshallable)

  final case class getCategories(auth_details: Claims);

  def props: Props = Props[CategoryActor]
}

class CategoryActor extends Actor with ActorLogging {

  import CategoryActor._

  def receive: Receive = {

    /*
      Fetch, Sort, Filter Rules
     */

    case getCategories(auth_details) =>

      try {

        val categories: List[Category] = CategoryService.getCategories()

        if (categories.size > 0)
          sender() ! CategoriesReponse(200, "Success", categories)
        else
          sender() ! CategoriesReponse(200, "Not Found", categories)

      } catch {

        case e: Exception => sender() ! CategoriesReponse(500, "Something went wrong, please try again", List.empty)

      }

  }
}
