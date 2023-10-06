package com.iac.soc.backend.utility

import com.typesafe.scalalogging.LazyLogging
import org.graalvm.polyglot.Context


object ASTUtility extends LazyLogging {

  def initializeContext(): Context = {

    val context: Context = Context.newBuilder("js").allowIO(true).build

    context.eval("js", "load('classpath:sqlParser.js');")

    context.eval("js", "load('classpath:parser.js');")

    context

  }


  /**
    * Add organizations to the query using AST
    *
    * @param organizationName the name of the organization
    * @param query            the query
    * @return the query
    */

  def addOrganizations(organizationName: String, query: String, contextParam: Context = null): String = {

//    val context = initializeContext()

    val context = if (contextParam != null) contextParam else initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    bindings.putMember("organizations", organizationName)

    val org_query = context.eval("js", "module.getQuery(module.addOrganizations(module.getAST(query), organizations));").asString

    org_query.toString

  }

  /**
    * Add log date to the query using AST
    *
    * @param query the query
    * @return the query
    */

  def addLogDate(query: String, contextParam: Context = null): String = {

//    val context = initializeContext()
    val context = if (contextParam != null) contextParam else initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    val updated_query = context.eval("js", "module.addLogDate(module.getAST(query));")

    updated_query.toString

  }

  /**
    * Add query to the query using AST
    *
    * @param query the query
    * @return the query
    */

  def addTimestampsToQuery(query: String, lower_bound_in_days: String, upper_bound_datetime: String): String = {

    val context = initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    bindings.putMember("lowerBoundInDays", lower_bound_in_days)

    bindings.putMember("upperBoundDate", upper_bound_datetime)

    val updated_query = context.eval("js", "module.addTimestampBounds(module.getAST(query), lowerBoundInDays,upperBoundDate);")

    updated_query.toString

  }

  /**
    * Add query to the query using AST
    *
    * @param query the query
    * @return the query
    */

  def getOrganizations(query: String, contextParam: Context = null): String = {

    val context = if (contextParam != null) contextParam else initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    val js_organizations = context.eval("js", "module.getOrganizations(module.getAST(query));")

    js_organizations.toString

  }

  /**
    * Check for order by clause in ast if not present add order by timestamp desc
    *
    * @param query the query
    * @return the query
    */

  def checkAndAddOrderClause(query: String, contextParam: Context = null): String = {

//    val context = initializeContext()
    val context = if (contextParam != null) contextParam else initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    val updatedQuery = context.eval("js", "module.checkAndAddOrderClause(module.getAST(query));")

    updatedQuery.toString

  }

  /**
    * Check for limit by clause in ast if not present add limit
    *
    * @param query the query
    * @return the query
    */

  def checkAndAddLimitClause(query: String, limit: String): String = {

    val context = initializeContext()

    val bindings = context.getBindings("js")

    bindings.putMember("query", query)

    bindings.putMember("limit", limit)

    val updatedQuery = context.eval("js", "module.checkAndAddLimitClause(module.getAST(query), limit);")

    updatedQuery.toString

  }

}

