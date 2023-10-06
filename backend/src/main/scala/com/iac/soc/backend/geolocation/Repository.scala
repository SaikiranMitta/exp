package com.iac.soc.backend.geolocation

import com.iac.soc.backend.geolocation.models.IpGeolocation
import com.typesafe.scalalogging.LazyLogging

/**
  * The geolocation sub-system repository
  */
private[geolocation] object Repository extends LazyLogging {

  /**
    * Get the geolocation details for an IP
    *
    * @param ip the IP whose geolocation details are needed
    * @return the geolocation details for the IP
    */
  def getIpGeolocation(ip: String): IpGeolocation = {

    logger.info(s"Fetching ip geolocation details from database for ip: ${ip}")

    // Returning IP address of Pune for testing purposes
    IpGeolocation(ip, "18.5167", "73.8562", "Pune", "India")
  }
}
