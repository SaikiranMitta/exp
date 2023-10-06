package com.iac.soc.backend.api.common.mapping

object NormalizerMapping {
  final case class NormalizerResponse(id: Int, filters: String, mapping: String, grokPattern: String, logSourceId: Int, normalizerType: String, isGlobal: Int);
  final case class NormalizersResponse(status_code: Int, message: String, page: Int, size: Int, total_records: Int, normalizer: List[NormalizerResponse]);
  final case class NormalizerInsert(filters: String, mapping: String, grokPattern: String, logSourceId: Int, normalizerType: String, isGlobal: Int)
  final case class NormalizerUpdate(filters: String, mapping: String, grokPattern: String, logSourceId: Int, normalizerType: String, isGlobal: Int)

}
