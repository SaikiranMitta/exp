package com.iac.soc.backend.threat.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Represents a trustar access token response
  */
class TokenResponse {

  @JsonProperty("access_token")
  var access_token: String = _

  @JsonProperty("token_type")
  var token_type: String = _

  @JsonProperty("expires_in")
  var expires_in: Int = _

  @JsonProperty("scope")
  var scope: String = _
}
