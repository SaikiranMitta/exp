
package com.iac.soc.backend.api.common.mapping

object OrganizationMapping {

  final case class Organization(name: String, id: Long);

  final case class Organizations(organization: List[Organization]);

  final case class OrganizationsReponse(status_code: Int, message: String, organizations: List[Organization]);
}

