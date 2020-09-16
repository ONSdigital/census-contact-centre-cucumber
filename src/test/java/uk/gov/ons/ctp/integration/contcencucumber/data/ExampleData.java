package uk.gov.ons.ctp.integration.contcencucumber.data;

import io.swagger.client.model.CaseType;
import io.swagger.client.model.EstabType;
import io.swagger.client.model.ModifyCaseRequestDTO;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.model.CaseContainerDTO;
import uk.gov.ons.ctp.integration.caseapiclient.caseservice.model.EventDTO;

public class ExampleData {
  public static ModifyCaseRequestDTO createModifyCaseRequest(final UUID caseId) {
    final ModifyCaseRequestDTO modifyCaseRequest = new ModifyCaseRequestDTO();
    modifyCaseRequest.setAddressLine1("33 Some Road");
    modifyCaseRequest.setAddressLine2("Some Small Area");
    modifyCaseRequest.setAddressLine3("Some Village");
    modifyCaseRequest.setCeOrgName("Some Organisation");
    modifyCaseRequest.setDateTime("2020-08-20T16:50:26.564+01:00");
    modifyCaseRequest.setCaseId(caseId);
    modifyCaseRequest.setEstabType(EstabType.OTHER);
    modifyCaseRequest.setCaseType(CaseType.CE);
    return modifyCaseRequest;
  }

  public static CaseContainerDTO createCaseContainer(final String caseId, final String uprnStr) {
    final CaseContainerDTO caseContainer = new CaseContainerDTO();
    caseContainer.setId(UUID.fromString(caseId));
    caseContainer.setCaseRef("124124009");
    caseContainer.setCaseType("CE");
    caseContainer.setAddressType("HH");
    caseContainer.setEstabType("OTHER");
    caseContainer.setCreatedDateTime(new Date());
    caseContainer.setLastUpdated(null);
    caseContainer.setAddressLine1("44 RM Road");
    caseContainer.setAddressLine2("RM Street");
    caseContainer.setAddressLine3("RM Village");
    caseContainer.setTownName("Newport");
    caseContainer.setRegion("W");
    caseContainer.setPostcode("G1 2AA");
    caseContainer.setOrganisationName("Response Management Org");
    caseContainer.setUprn(uprnStr);
    List<EventDTO> caseEvents = new ArrayList<>();
    caseContainer.setCaseEvents(caseEvents);
    return caseContainer;
  }
}
