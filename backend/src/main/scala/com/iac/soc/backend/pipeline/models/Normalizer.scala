package com.iac.soc.backend.pipeline.models

/**
  * Represents a normalizer
  *
  * @param id         the id of the normalizer
  * @param schema     the schema associated with the normalizer
  * @param ingestorId the id of the associated ingestor
  * @param pattern    the pattern to be matched against which will determine whether to use the normalizer or not
  * @param processor  the JavaScript function responsible for normalization processing
  */
case class Normalizer(id: Int, schema: String, ingestorId: Int, pattern: String, processor: String)
