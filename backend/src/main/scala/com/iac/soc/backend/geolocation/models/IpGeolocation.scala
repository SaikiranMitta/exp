package com.iac.soc.backend.geolocation.models

/**
  * Represents the gelocation details of an IP address
  *
  * @param ip        the IP address
  * @param latitude  the latitude of the IP address
  * @param longitude the longitude of the IP address
  * @param city      the city of the IP address
  * @param country   the country of the IP address
  */
case class IpGeolocation(ip: String, latitude: String, longitude: String, city: String, country: String)
