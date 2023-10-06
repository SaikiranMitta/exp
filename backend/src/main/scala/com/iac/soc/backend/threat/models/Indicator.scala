package com.iac.soc.backend.threat.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Represents a trustar indicator
  */
class Indicator {

  @JsonProperty("indicatorType")
  var indicatorType: String = _

  @JsonProperty("value")
  var value: String = _

  @JsonProperty("priorityLevel")
  var priorityLevel: String = _

  @JsonProperty("guid")
  var guid: String = _
}