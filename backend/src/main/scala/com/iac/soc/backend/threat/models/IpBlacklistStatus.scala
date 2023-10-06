package com.iac.soc.backend.threat.models

/**
  * Represents if an IP is blacklisted or not
  *
  * @param ip          the IP to be checked
  * @param blacklisted the blacklist status of the IP
  */
case class IpBlacklistStatus(ip: String, blacklisted: Boolean)
