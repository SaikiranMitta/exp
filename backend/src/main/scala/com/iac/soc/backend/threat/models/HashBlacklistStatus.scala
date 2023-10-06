package com.iac.soc.backend.threat.models

/**
  * Represents if a hash is blacklisted or not
  *
  * @param hash        the hash to be checked
  * @param blacklisted the blacklist status of the hash
  */
case class HashBlacklistStatus(hash: String, blacklisted: Boolean)