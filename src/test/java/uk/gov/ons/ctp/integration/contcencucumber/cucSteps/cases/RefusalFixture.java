package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.cases;

import java.util.Date;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import uk.gov.ons.ctp.common.event.model.AddressCompact;
import uk.gov.ons.ctp.common.event.model.Contact;
import uk.gov.ons.ctp.common.model.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.Reason;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.RefusalRequestDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.Region;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RefusalFixture {

  public static final Reason A_REASON = Reason.HARD;
  public static final String AN_AGENT_ID = "123";
  public static final String SOME_NOTES = "Description of refusal";
  public static final String A_TITLE = "Mr";
  public static final String A_FORENAME = "Steve";
  public static final String A_SURNAME = "Jones";
  public static final String A_TEL_NO = "07968583119";
  public static final String AN_ADDR_LINE_1 = "1 High Street";
  public static final String AN_ADDR_LINE_2 = "Delph";
  public static final String AN_ADDR_LINE_3 = "Oldham";
  public static final String A_TOWN = "Manchester";
  public static final String A_POSTCODE = "OL3 5DJ";
  public static final Region A_REGION = Region.E;
  public static final String A_UPRN_STR = "1234";
  public static final UniquePropertyReferenceNumber A_UPRN =
      new UniquePropertyReferenceNumber(A_UPRN_STR);

  public static RefusalRequestDTO createRequest(String caseId, String agentId, Reason reason) {
    Date dateTime = new Date();

    RefusalRequestDTO refusal =
        RefusalRequestDTO.builder()
            .caseId(caseId)
            .agentId(agentId)
            .notes(SOME_NOTES)
            .title(A_TITLE)
            .forename(A_FORENAME)
            .surname(A_SURNAME)
            .telNo(A_TEL_NO)
            .addressLine1(AN_ADDR_LINE_1)
            .addressLine2(AN_ADDR_LINE_2)
            .addressLine3(AN_ADDR_LINE_3)
            .townName(A_TOWN)
            .postcode(A_POSTCODE)
            .uprn(A_UPRN)
            .region(A_REGION)
            .reason(reason)
            .dateTime(dateTime)
            .build();
    return refusal;
  }

  // to match details in the request DTO
  public static AddressCompact compactAddress() {
    AddressCompact addr = new AddressCompact();
    addr.setAddressLine1(AN_ADDR_LINE_1);
    addr.setAddressLine2(AN_ADDR_LINE_2);
    addr.setAddressLine3(AN_ADDR_LINE_3);
    addr.setTownName(A_TOWN);
    addr.setPostcode(A_POSTCODE);
    addr.setRegion(A_REGION.name());
    addr.setUprn(A_UPRN_STR);
    return addr;
  }

  // to match details in the request DTO
  public static Contact contact() {
    Contact contact = new Contact();
    contact.setTitle(A_TITLE);
    contact.setForename(A_FORENAME);
    contact.setSurname(A_SURNAME);
    contact.setTelNo(A_TEL_NO);
    return contact;
  }
}
