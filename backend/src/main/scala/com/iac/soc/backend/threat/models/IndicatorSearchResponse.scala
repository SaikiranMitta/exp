package com.iac.soc.backend.threat.models

import com.fasterxml.jackson.annotation.JsonProperty

/**
  * Represents a trustar indicator search response
  */
class IndicatorSearchResponse {

  @JsonProperty("pageNumber")
  var pageNumber: Int = _

  @JsonProperty("totalPages")
  var totalPages: Int = _

  @JsonProperty("pageSize")
  var pageSize: Int = _

  @JsonProperty("totalElements")
  var totalElements: Int = _

  @JsonProperty("items")
  var items: Array[Indicator] = _

  @JsonProperty("empty")
  var empty: Boolean = _

  @JsonProperty("hasNext")
  var hasNext: Boolean = _
}
