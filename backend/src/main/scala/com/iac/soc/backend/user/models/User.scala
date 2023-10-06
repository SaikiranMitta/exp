package com.iac.soc.backend.user.models

/**
  * Represents a user
  *
  * @param id    the id of the user
  * @param name  the name of the user
  * @param email the email of the user
  */
case class User(id: Int, name: String, email: String)
