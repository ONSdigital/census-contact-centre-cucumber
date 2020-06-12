package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.cases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.swagger.client.model.AddressDTO;
import io.swagger.client.model.AddressQueryResponseDTO;
import io.swagger.client.model.CaseDTO;
import io.swagger.client.model.DeliveryChannel;
import io.swagger.client.model.EstabType;
import io.swagger.client.model.FulfilmentDTO;
import io.swagger.client.model.InvalidateCaseRequestDTO;
import io.swagger.client.model.RefusalRequestDTO;
import io.swagger.client.model.RefusalRequestDTO.ReasonEnum;
import io.swagger.client.model.ResponseDTO;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.event.EventPublisher.Channel;
import uk.gov.ons.ctp.common.event.EventPublisher.EventType;
import uk.gov.ons.ctp.common.event.EventPublisher.Source;
import uk.gov.ons.ctp.common.event.model.Address;
import uk.gov.ons.ctp.common.event.model.AddressNotValid;
import uk.gov.ons.ctp.common.event.model.AddressNotValidEvent;
import uk.gov.ons.ctp.common.event.model.AddressNotValidPayload;
import uk.gov.ons.ctp.common.event.model.CollectionCaseNewAddress;
import uk.gov.ons.ctp.common.event.model.Header;
import uk.gov.ons.ctp.common.event.model.NewAddress;
import uk.gov.ons.ctp.common.event.model.NewAddressPayload;
import uk.gov.ons.ctp.common.event.model.NewAddressReportedEvent;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalDetails;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalEvent;
import uk.gov.ons.ctp.common.event.model.RespondentRefusalPayload;
import uk.gov.ons.ctp.common.event.model.SurveyLaunchedEvent;
import uk.gov.ons.ctp.common.rabbit.RabbitHelper;
import uk.gov.ons.ctp.common.util.TimeoutParser;
import uk.gov.ons.ctp.integration.contcencucumber.cloud.CachedCase;
import uk.gov.ons.ctp.integration.contcencucumber.cucSteps.ResetMockCaseApiAndPostCasesBase;
import uk.gov.ons.ctp.integration.contcencucumber.main.repository.impl.CaseDataRepositoryImpl;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.Codec;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.EQJOSEProvider;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.KeyStore;

public class TestCaseEndpoints extends ResetMockCaseApiAndPostCasesBase {

  @Autowired private CaseDataRepositoryImpl dataRepo;

  private static final Logger log = LoggerFactory.getLogger(TestCaseEndpoints.class);
  private static final String RABBIT_EXCHANGE = "events";
  private static final long RABBIT_TIMEOUT = 2000L;

  private String caseId;
  private String uprn;
  private RefusalRequestDTO refusalDTO;
  private ResponseDTO responseDTO;
  private ReasonEnum reason = RefusalFixture.A_REASON;
  private String agentId = RefusalFixture.AN_AGENT_ID;
  private CaseDTO caseDTO;
  private List<CaseDTO> caseDTOList;
  private Exception exception;
  private String ccSmokeTestUrl;
  private String mockCaseSvcSmokeTestUrl;
  private String telephoneEndpointUrl;
  private String telephoneEndpointBody1;
  private String telephoneEndpointBody2;
  private RabbitHelper rabbit;
  private String queueName;
  private List<CaseDTO> listOfCasesWithUprn;
  private URI caseForUprnUrl;
  private AddressNotValidEvent addressNotValidEvent;
  private String uprnStr;
  private String status = "";

  @Value("${keystore}")
  private String keyStore;

  @Before
  public void setup() throws Exception {
    rabbit = RabbitHelper.instance(RABBIT_EXCHANGE);
    addressNotValidEvent = null;
  }

  @Given("I am about to do a smoke test by going to a contact centre endpoint")
  public void i_am_about_to_do_a_smoke_test_by_going_to_a_contact_centre_endpoint() {
    log.info("About to check that the Contact Centre service is running...");
  }

  @Then("I do the smoke test and receive a response of OK from the contact centre service")
  public void i_do_the_smoke_test_and_receive_a_response_of_OK_from_the_contact_centre_service() {
    try {
      HttpStatus contactCentreStatus = checkContactCentreRunning();
      log.with(contactCentreStatus).info("Smoke Test: The response from " + ccSmokeTestUrl);
      assertEquals(
          "THE CONTACT CENTRE SERVICE MAY NOT BE RUNNING - it does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (ResourceAccessException e) {
      log.error(
          "THE CONTACT CENTRE SERVICE MAY NOT BE RUNNING: A ResourceAccessException has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } catch (Exception e) {
      log.error("THE CONTACT CENTRE SERVICE MAY NOT BE RUNNING: An unexpected has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Given("I am about to do a smoke test by going to a mock case api endpoint")
  public void i_am_about_to_do_a_smoke_test_by_going_to_a_mock_case_api_endpoint() {
    log.info("About to check that the mock case api service is running...");
  }

  @Then("I do the smoke test and receive a response of OK from the mock case api service")
  public void i_do_the_smoke_test_and_receive_a_response_of_OK_from_the_mock_case_api_service() {
    try {
      HttpStatus mockCaseApiStatus = checkMockCaseApiRunning();
      log.with(mockCaseApiStatus).info("Smoke Test: The response from " + mockCaseSvcSmokeTestUrl);
      assertEquals(
          "THE MOCK CASE API SERVICE MAY NOT BE RUNNING - it does not give a response code of 200",
          HttpStatus.OK,
          mockCaseApiStatus);
    } catch (ResourceAccessException e) {
      log.error(
          "THE MOCK CASE API SERVICE MAY NOT BE RUNNING: A ResourceAccessException has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } catch (Exception e) {
      log.error("THE MOCK CASE API SERVICE MAY NOT BE RUNNING: An unexpected has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Given("I have a valid case ID {string}")
  public void i_have_a_valid_case_ID(String caseId) {
    this.caseId = caseId;
  }

  @When("I Search cases By case ID {string}")
  public void i_Search_cases_By_case_ID(String showCaseEvents) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .queryParam("caseEvents", showCaseEvents);
    caseDTO = getRestTemplate().getForObject(builder.build().encode().toUri(), CaseDTO.class);
  }

  @Then("the correct case for my case ID is returned {int}")
  public void the_correct_case_for_my_case_ID_is_returned(Integer uprn) {
    assertNotNull("Case Query Response must not be null", caseDTO);
    assertEquals("Case Query Response UPRN must match", caseDTO.getUprn(), Integer.toString(uprn));
  }

  @Then("the correct number of events are returned {string} {int}")
  public void the_correct_number_of_events_are_returned(
      String showCaseEvents, Integer expectedCaseEvents) {
    if (!Boolean.parseBoolean(showCaseEvents)) {
      assertNull("Events must be null", caseDTO.getCaseEvents());
    } else {
      assertEquals(
          "Must have the correct number of case events",
          Long.valueOf(expectedCaseEvents),
          Long.valueOf(caseDTO.getCaseEvents().size()));
    }
  }

  @And("the establishment UPRN is {string}")
  public void the_establishment_UPRN_is(String expectedEstabUprn) {
    UniquePropertyReferenceNumber estabUprn =
        UniquePropertyReferenceNumber.create(caseDTO.getEstabUprn());
    if (estabUprn.getValue() == 0L) {
      estabUprn = null;
    }
    if (StringUtils.isBlank(expectedEstabUprn)) {
      assertNull("There should be no establishment UPRN", estabUprn);
    } else {
      assertNotNull("Establishment UPRN should exist", estabUprn);
      assertEquals(
          "Mismatching establishment UPRNs",
          expectedEstabUprn,
          Long.toString(estabUprn.getValue()));
    }
  }

  @And("the secure establishment is set to {string}")
  public void the_secure_establishment_is_set_to(String secure) {
    boolean secureEstablishment = caseDTO.isSecureEstablishment();
    boolean expectedSecure = Boolean.parseBoolean(secure);
    assertEquals(
        "Mismatching expectation of secure establishment", expectedSecure, secureEstablishment);
  }

  @Given("I have an invalid case ID {string}")
  public void i_have_an_invalid_case_ID(String caseId) {
    this.caseId = caseId;
  }

  @When("I Search for cases By case ID")
  public void i_Search_for_cases_By_case_ID() {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId);
    try {
      caseDTO = getRestTemplate().getForObject(builder.build().encode().toUri(), CaseDTO.class);
    } catch (HttpClientErrorException | HttpServerErrorException httpClientErrorException) {
      this.exception = httpClientErrorException;
    }
  }

  @Then("An error is thrown and no case is returned {string}")
  public void an_error_is_thrown_and_no_case_is_returned(String httpError) {
    assertNotNull("An error was expected, but it succeeded", exception);
    assertTrue(
        "The correct http status must be returned " + httpError,
        exception.getMessage().trim().contains(httpError));
  }

  @Given("I have a valid UPRN {string}")
  public void i_have_a_valid_UPRN(String uprn) {
    this.uprn = uprn;
  }

  @When("I Search cases By UPRN")
  public void i_Search_cases_By_UPRN() {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprn);
    try {
      ResponseEntity<List<CaseDTO>> caseResponse =
          getRestTemplate()
              .exchange(
                  builder.build().encode().toUri(),
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<CaseDTO>>() {});
      caseDTOList = caseResponse.getBody();
    } catch (HttpClientErrorException httpClientErrorException) {
      this.exception = httpClientErrorException;
    }
  }

  @Then("the correct cases for my UPRN are returned {string}")
  public void the_correct_cases_for_my_UPRN_are_returned(String caseIds) {
    final List<String> caseIdList = Arrays.stream(caseIds.split(",")).collect(Collectors.toList());
    caseDTOList.forEach(
        caseDetails -> {
          String caseID = caseDetails.getId().toString().trim();
          assertTrue("case ID must be in case list - ", caseIdList.contains(caseID));
        });
  }

  @Given("I have an invalid UPRN {string}")
  public void i_have_an_invalid_UPRN(String uprn) {
    this.uprn = uprn;
  }

  @When("I Search cases By invalid UPRN")
  public void i_Search_cases_By_invalid_UPRN() {
    exception = null;
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprn);
    try {
      ResponseEntity<List<CaseDTO>> caseResponse =
          getRestTemplate()
              .exchange(
                  builder.build().encode().toUri(),
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<CaseDTO>>() {});
      caseDTOList = caseResponse.getBody();
    } catch (HttpClientErrorException httpClientErrorException) {
      exception = httpClientErrorException;
    }
  }

  @Then("no cases for my UPRN are returned {string}")
  public void no_cases_for_my_UPRN_are_returned(String httpError) {
    assertNotNull("Should throw an exception", exception);
    assertTrue(
        "Invalid UPRN causes http status " + httpError,
        exception.getMessage() != null && exception.getMessage().contains(httpError));

    assertNull("UPRN response must be null", caseDTOList);
  }

  @Given("confirmed CaseType {string} {string}")
  public void confirmed_caseType(final String caseId, final String individual)
      throws InterruptedException {
    this.caseId = caseId;
    boolean isIndividual = Boolean.parseBoolean(individual);
    log.info(
        "The CC advisor clicks a button to confirm that the case type is HH and then launch EQ...");

    try {
      ResponseEntity<String> eqResponse1 = getEqToken(caseId, isIndividual);
      telephoneEndpointBody1 = eqResponse1.getBody();
      HttpStatus contactCentreStatus1 = eqResponse1.getStatusCode();
      log.with(contactCentreStatus1)
          .info("Launch EQ for HH: The response from " + telephoneEndpointUrl);
      assertEquals(
          "LAUNCHING EQ FOR HH HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus1);
    } catch (ResourceAccessException e) {
      log.error("LAUNCHING EQ FOR HH HAS FAILED: A ResourceAccessException has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } catch (Exception e) {
      log.error("LAUNCHING EQ FOR HH HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }

    log.info(
        "Repeat launching EQ for HH so that the two responses can be compared. Wait a second to get different time values.");
    Thread.sleep(1000);

    try {
      ResponseEntity<String> eqResponse2 = getEqToken(caseId, isIndividual);
      telephoneEndpointBody2 = eqResponse2.getBody();
      HttpStatus contactCentreStatus2 = eqResponse2.getStatusCode();
      log.with(contactCentreStatus2)
          .info("Launch EQ for HH: The response from " + telephoneEndpointUrl);
      assertEquals(
          "LAUNCHING EQ FOR HH HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus2);
    } catch (ResourceAccessException e) {
      log.error("LAUNCHING EQ FOR HH HAS FAILED: A ResourceAccessException has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    } catch (Exception e) {
      log.error("LAUNCHING EQ FOR HH HAS FAILED: An unexpected has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Then("EQ is launched {string} {string} {string}")
  public void eq_is_launched(final String caseType, final String caseId, final String individual)
      throws Exception {
    String hhEqToken1;
    String hhEqToken2;
    final boolean isIndividual = Boolean.parseBoolean(individual);

    log.info(
        "Create a substring that removes the first part of the telephoneEndpointBody to leave just the EQ token value");

    hhEqToken1 = telephoneEndpointBody1.substring(37);
    hhEqToken2 = telephoneEndpointBody2.substring(37);

    log.info("The first EQ token is: " + hhEqToken1);
    log.info("The second EQ token is: " + hhEqToken2);

    EQJOSEProvider coderDecoder = new Codec();

    String decryptedEqToken1 = coderDecoder.decrypt(hhEqToken1, new KeyStore(keyStore));
    String decryptedEqToken2 = coderDecoder.decrypt(hhEqToken2, new KeyStore(keyStore));

    log.info("The first decrypted EQ token is: " + decryptedEqToken1);
    log.info("The second decrypted EQ token is: " + decryptedEqToken2);

    @SuppressWarnings("unchecked")
    HashMap<String, String> result1 =
        new ObjectMapper().readValue(decryptedEqToken1, HashMap.class);

    @SuppressWarnings("unchecked")
    HashMap<String, String> result2 =
        new ObjectMapper().readValue(decryptedEqToken2, HashMap.class);

    log.info(
        "Assert that the "
            + result1.size()
            + " keys in the first hashmap are the ones that we expect e.g. it should not contain accountServiceUrl or accountServiceLogoutUrl");

    ArrayList<String> hashKeysExpected = new ArrayList<>();
    hashKeysExpected.add("questionnaire_id");
    hashKeysExpected.add("response_id");
    hashKeysExpected.add("display_address");
    hashKeysExpected.add("channel");
    hashKeysExpected.add("case_type");
    hashKeysExpected.add("eq_id");
    hashKeysExpected.add("form_type");
    hashKeysExpected.add("tx_id");
    hashKeysExpected.add("ru_ref");
    hashKeysExpected.add("language_code");
    hashKeysExpected.add("user_id");
    hashKeysExpected.add("collection_exercise_sid");
    hashKeysExpected.add("case_id");
    hashKeysExpected.add("survey");
    hashKeysExpected.add("exp");
    hashKeysExpected.add("period_id");
    hashKeysExpected.add("iat");
    hashKeysExpected.add("jti");
    hashKeysExpected.add("region_code");

    log.info("The hash keys expected are: " + hashKeysExpected.toString());

    List<String> hashKeysFound = new ArrayList<>(result1.keySet());

    log.info("The hash keys found are: " + hashKeysFound.toString());

    assertEquals(
        "Must have the correct number of hash keys", hashKeysExpected.size(), hashKeysFound.size());
    assertEquals(
        "Must have the correct hash keys", hashKeysExpected.toString(), hashKeysFound.toString());
    assertNotEquals(
        "Must have different questionnaire_id values",
        result1.get("questionnaire_id"),
        result2.get("questionnaire_id"));
    assertNotEquals(
        "Must have different response_id values",
        result1.get("response_id"),
        result2.get("response_id"));
    assertEquals(
        "Must have the correct address", "4, Okehampton Road, ", result1.get("display_address"));
    assertEquals("Must have the correct channel", "cc", result1.get("channel"));
    assertEquals("Must have the correct case type", caseType, result1.get("case_type"));

    /*
     * The following assert will need to be changed if the eq_id value, which is hard-coded in the
     * CCSVC, is updated
     */
    assertEquals("Must have the correct eq id", "census", result1.get("eq_id"));

    /*
     * The following assert will need to be changed if the form_type value, which is hard-coded in
     * the CCSVC, is updated
     */
    assertEquals("Must have the correct form type", "H", result1.get("form_type"));

    assertNotEquals("Must have different tx_id values", result1.get("tx_id"), result2.get("tx_id"));
    assertEquals("Must have the correct ru_ref value", "100041045599", result1.get("ru_ref"));
    assertEquals("Must have the correct language_code value", "en", result1.get("language_code"));
    assertEquals("Must have the correct user_id value", agentId, result1.get("user_id"));
    assertEquals(
        "Must have the correct collection_exercise_sid value",
        "49871667-117d-4a63-9101-f6a0660f73f6",
        result1.get("collection_exercise_sid"));
    if (isIndividual) {
      assertNotEquals("Must have a new case_id value", caseId, result1.get("case_id"));
    } else {
      assertEquals("Must have the correct case_id value", caseId, result1.get("case_id"));
    }

    assertEquals("Must have the correct survey value", "CENSUS", result1.get("survey"));
    assertNotEquals("Must have different exp values", result1.get("exp"), result2.get("exp"));

    /*
     * The following assert will need to be changed if the period_id value, which is hard-coded in
     * the CCSVC, is updated
     */
    assertEquals("Must have the correct period id", "2019", result1.get("period_id"));

    assertNotEquals("Must have different iat values", result1.get("iat"), result2.get("iat"));
    assertNotEquals("Must have different jti values", result1.get("jti"), result2.get("jti"));
    assertEquals("Must have the correct region code", "GB-ENG", result1.get("region_code"));
  }

  @And("an empty queue exists for sending Refusal events")
  public void an_empty_queue_exists_for_sending_Refusal_events() throws CTPException {
    EventType eventType = EventType.valueOf("REFUSAL_RECEIVED");
    log.info("Creating queue for events of type: '" + eventType + "'");
    queueName = rabbit.createQueue(eventType);
    log.info("Flushing queue: '" + queueName + "'");
    rabbit.flushQueue(queueName);
  }

  @And("a Refusal event is sent with type {string}")
  public void a_Refusal_event_is_sent(String type) throws CTPException {
    log.info("Check that a Refusal event has been sent");
    RespondentRefusalEvent event =
        rabbit.getMessage(queueName, RespondentRefusalEvent.class, 2_000L);

    assertNotNull(event);
    Header header = event.getEvent();
    assertEquals(EventType.REFUSAL_RECEIVED, header.getType());
    assertEquals(Source.CONTACT_CENTRE_API, header.getSource());
    assertEquals(Channel.CC, header.getChannel());

    RespondentRefusalPayload payload = event.getPayload();
    assertNotNull(payload);
    RespondentRefusalDetails details = payload.getRefusal();
    assertEquals(type, details.getType());
    log.info("Verifying refusal event details");
    assertEquals(RefusalFixture.compactAddress(), details.getAddress());
    assertEquals(RefusalFixture.contact(), details.getContact());
    assertEquals(RefusalFixture.SOME_NOTES, details.getReport());
    assertEquals(RefusalFixture.A_CALL_ID, details.getCallId());
    assertEquals(agentId, details.getAgentId());
    assertEquals(UUID.fromString(caseId), details.getCollectionCase().getId());
  }

  @When("I Refuse a case")
  public void i_Refuse_a_case() {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .pathSegment("refusal");
    try {
      responseDTO =
          getRestTemplate()
              .postForObject(builder.build().encode().toUri(), refusalDTO, ResponseDTO.class);
    } catch (HttpClientErrorException | HttpServerErrorException httpClientErrorException) {
      this.exception = httpClientErrorException;
    }
  }

  @And("I supply a {string} reason for Refusal")
  public void i_supply_a_reason_for_Refusal(String reason) {
    this.reason = StringUtils.isBlank(reason) ? null : ReasonEnum.valueOf(reason);
  }

  @And("I supply an {string} agentId for Refusal")
  public void i_supply_an_agentId_for_Refusal(String agentId) {
    this.agentId = agentId;
  }

  @And("I supply the Refusal information")
  public void i_supply_the_Refusal_information() {
    this.refusalDTO = RefusalFixture.createRequest(caseId, agentId, reason);
  }

  @Then("the call succeeded and responded with the supplied case ID")
  public void the_call_succeeded_and_responded_with_the_supplied_case_ID() {
    assertNotNull("Response must not be null", responseDTO);
    assertNotNull("Response date/time must not be null", responseDTO.getDateTime());
    assertTrue("Response ID must match case ID", caseId.equalsIgnoreCase(responseDTO.getId()));
  }

  private HttpStatus checkContactCentreRunning() {
    log.info("Entering checkContactCentreRunning method");
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl).port(ccBasePort).pathSegment("fulfilments");

    ccSmokeTestUrl = builder.build().encode().toUri().toString();

    log.info(
        "Using the following endpoint to check that the contact centre service is running: "
            + ccSmokeTestUrl);

    ResponseEntity<List<FulfilmentDTO>> fulfilmentResponse =
        getRestTemplate()
            .exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FulfilmentDTO>>() {});

    return fulfilmentResponse.getStatusCode();
  }

  private HttpStatus checkMockCaseApiRunning() {
    log.info("Entering checkMockCaseApiRunning method");
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(mcsBaseUrl)
            .port(mcsBasePort)
            .pathSegment("cases")
            .pathSegment("info");

    RestTemplate restTemplate = getAuthenticationFreeRestTemplate();

    mockCaseSvcSmokeTestUrl = builder.build().encode().toUri().toString();

    log.info(
        "Using the following endpoint to check that the mock case api service is running: "
            + mockCaseSvcSmokeTestUrl);

    ResponseEntity<String> mockCaseApiResponse =
        restTemplate.getForEntity(builder.build().encode().toUri(), String.class);

    return mockCaseApiResponse.getStatusCode();
  }

  private ResponseEntity<String> getEqToken(String caseId, boolean isIndividual) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .pathSegment("launch")
            .queryParam("agentId", agentId)
            .queryParam("individual", isIndividual);

    telephoneEndpointUrl = builder.build().encode().toUri().toString();
    log.info("Using the following endpoint to launch EQ: " + telephoneEndpointUrl);
    return getRestTemplate().getForEntity(builder.build().encode().toUri(), String.class);
  }

  @Given("the CC advisor has provided a valid UPRN {string}")
  public void the_CC_advisor_has_provided_a_valid_UPRN(String strUprn) {
    try {
      ResponseEntity<List<CaseDTO>> caseUprnResponse = getCaseForUprn(strUprn);
      listOfCasesWithUprn = caseUprnResponse.getBody();
      HttpStatus contactCentreStatus = caseUprnResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("GET CASE BY UPRN: The response from " + caseForUprnUrl.toString());
      assertEquals(HttpStatus.OK, contactCentreStatus);
    } catch (Exception e) {
      fail(
          "GET CASE BY UPRN HAS FAILED -  the contact centre does not give a response code of 200");
    }
  }

  private ResponseEntity<List<CaseDTO>> getCaseForUprn(String uprn) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprn);

    ResponseEntity<List<CaseDTO>> caseResponse = null;
    caseForUprnUrl = builder.build().encode().toUri();

    try {
      caseResponse =
          getRestTemplate()
              .exchange(
                  caseForUprnUrl,
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<CaseDTO>>() {});
    } catch (HttpClientErrorException httpClientErrorException) {
      log.debug(
          "A HttpClientErrorException has occurred when trying to get list of cases using getCaseByUprn endpoint in contact centre: "
              + httpClientErrorException.getMessage());
    }
    return caseResponse;
  }

  @Then("the Case endpoint returns a case associated with UPRN {string}")
  public void the_Case_endpoint_returns_a_case_associated_with_UPRN(String strUprn) {
    caseId = listOfCasesWithUprn.get(0).getId().toString();
    log.with(caseId).debug("The case id returned by getCasesWithUprn endpoint");

    UniquePropertyReferenceNumber expectedUprn = new UniquePropertyReferenceNumber(strUprn);
    assertEquals(
        expectedUprn, UniquePropertyReferenceNumber.create(listOfCasesWithUprn.get(0).getUprn()));
  }

  @Given("an empty queue exists for sending AddressNotValid events")
  public void an_empty_queue_exists_for_sending_AddressNotValid_events() throws CTPException {
    String eventTypeAsString = "ADDRESS_NOT_VALID";
    log.info("Creating queue for events of type: '" + eventTypeAsString + "'");
    EventType eventType = EventType.valueOf(eventTypeAsString);
    queueName = rabbit.createQueue(eventType);
    log.info("Flushing queue: '" + queueName + "'");

    rabbit.flushQueue(queueName);
  }

  @When("CC Advisor selects the address status change {string}")
  public void cc_Advisor_selects_the(String statusSelected) {
    log.with(caseId).info("Calling invalidate endpoint");

    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .pathSegment("invalidate");

    URI invalidateCaseUrl = builder.build().encode().toUri();

    InvalidateCaseRequestDTO dto = new InvalidateCaseRequestDTO();
    dto.caseId(UUID.fromString(caseId))
        .status(InvalidateCaseRequestDTO.StatusEnum.valueOf(statusSelected))
        .notes("Two houses have been knocked into one.")
        .dateTime(OffsetDateTime.now(ZoneId.of("Z")).withNano(0).toString());

    ResponseEntity<ResponseDTO> response =
        getRestTemplate()
            .postForEntity(invalidateCaseUrl, new HttpEntity<>(dto), ResponseDTO.class);

    HttpStatus contactCentreStatus = response.getStatusCode();
    log.with(contactCentreStatus).info("INVALIDATE CASE Response");
    assertEquals(HttpStatus.OK, contactCentreStatus);
  }

  @Then("an AddressNotValid event is emitted to RM, which contains the {string} change")
  public void an_AddressNotValid_event_is_emitted_to_RM_which_contains_the_change(
      String expectedReason) throws CTPException {
    log.info(
        "Check that an ADDRESS_NOT_VALID event has now been put on the empty queue, named {}, ready to be picked up by RM",
        queueName);

    String timeout = "2000ms";

    log.info(
        "Getting from queue: '{}' and converting to an object of type '{}', with timeout of '{}'",
        queueName,
        "AddressNotValid.class",
        timeout);

    addressNotValidEvent =
        rabbit.getMessage(
            queueName, AddressNotValidEvent.class, TimeoutParser.parseTimeoutString(timeout));

    assertNotNull(addressNotValidEvent);
    Header addressNotValidHeader = addressNotValidEvent.getEvent();
    assertNotNull(addressNotValidHeader);
    AddressNotValidPayload addressNotValidPayload = addressNotValidEvent.getPayload();
    assertNotNull(addressNotValidPayload);

    EventType expectedType = EventType.ADDRESS_NOT_VALID;
    Source expectedSource = Source.CONTACT_CENTRE_API;
    Channel expectedChannel = Channel.CC;
    UUID expectedCollectionCaseId = UUID.fromString("3305e937-6fb1-4ce1-9d4c-077f147789aa");

    assertEquals(expectedType, addressNotValidHeader.getType());
    assertEquals(expectedSource, addressNotValidHeader.getSource());
    assertEquals(expectedChannel, addressNotValidHeader.getChannel());
    assertNotNull(addressNotValidHeader.getDateTime());
    assertNotNull(addressNotValidHeader.getTransactionId());

    AddressNotValid addressNotValid = addressNotValidPayload.getInvalidAddress();
    assertEquals(expectedReason, addressNotValid.getReason());
    assertEquals(expectedCollectionCaseId, addressNotValid.getCollectionCase().getId());
  }

  @Given("the CC agent has confirmed the respondent address")
  public void the_CC_agent_has_confirmed_the_respondent_address() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("addresses")
            .queryParam("input", "1, West Grove Road, Exeter, EX2 4LU");

    ResponseEntity<AddressQueryResponseDTO> addressQueryResponse =
        getRestTemplate()
            .exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<AddressQueryResponseDTO>() {});

    log.with(addressQueryResponse).info("The address query response here");

    AddressQueryResponseDTO addressQueryBody = addressQueryResponse.getBody();

    assert addressQueryBody != null;
    List<AddressDTO> addressesFound = addressQueryBody.getAddresses();

    String addressToFind = "1 West Grove Road, Exeter, EX2 4LU";
    String addressFound = "";
    int indexFound = 500;
    log.info(
        "The indexFound value defaults to 500 as that will cause an exception if it does not get reset in the while loop");
    for (int i = 0; i < addressesFound.size(); i++) {
      addressFound = addressesFound.get(i).getFormattedAddress();

      if (addressFound.equals(addressToFind)) {
        log.with(addressFound).info("This is the address that was found in AIMS");
        indexFound = i;
        break;
      }
    }
    assertEquals(
        "The address query response does not contain the correct address",
        addressToFind,
        addressFound);

    uprnStr = addressesFound.get(indexFound).getUprn();
  }

  @Given("the case service does not have any case created for the address in question")
  public void the_case_service_does_not_have_any_case_created_for_the_address_in_question() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(mcsBaseUrl)
            .port(mcsBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprnStr);
    String mcsUprnEndpointUrl = builder.build().encode().toUri().toString();

    log.info(
        "Using the following mock case service endpoint to check case does not exist for uprn in question: "
            + mcsUprnEndpointUrl);

    try {
      getRestTemplate().getForEntity(builder.build().encode().toUri(), String.class);
    } catch (RestClientException e) {
      log.with(e.getMessage())
          .info("catching the error returned by the mock case service endpoint");
      status = Objects.requireNonNull(e.getMessage()).substring(0, 13);
    }

    log.info("The response status: " + status);
  }

  @When("Get\\/Case API returns a {string} error because there is no case found")
  public void getCaseAPIReturnsAErrorBecauseThereIsNoCaseFound(String statusStr) {
    int returnStatus = Integer.parseInt(statusStr);
    assertEquals(
        "THE CASE SHOULD NOT EXIST - the mock case service endpoint should give a response code of "
            + returnStatus,
        "404 Not Found",
        status);
  }

  @When("CC SVC creates a fake Case with the address details from AIMS")
  public void cc_SVC_creates_a_fake_Case_with_the_address_details_from_AIMS() throws CTPException {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprnStr);
    String ccUprnEndpointUrl = builder.build().encode().toUri().toString();

    log.info(
        "As the case does not exist in the case service the endpoint {} should cause a new fake case to be created",
        ccUprnEndpointUrl);

    ResponseEntity<List<CaseDTO>> caseResponse =
        getRestTemplate()
            .exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CaseDTO>>() {});
    caseDTOList = caseResponse.getBody();
    assert caseDTOList != null;
    CaseDTO response = caseDTOList.get(0);

    assertNotNull(response.getId());
    assertNull(response.getCaseRef());
    assertEquals("HH", response.getCaseType().name());
    assertEquals("HH", response.getAddressType().name());
    assertFalse(response.isSecureEstablishment());
    assertEquals(
        Arrays.asList(DeliveryChannel.POST, DeliveryChannel.SMS),
        response.getAllowedDeliveryChannels());
    assertEquals(EstabType.HOUSEHOLD.name(), response.getEstabType().name());
    assertEquals("Household", response.getEstabDescription());
    assertNotNull(response.getCreatedDateTime());
    assertEquals("1 West Grove Road", response.getAddressLine1());
    assertEquals("Exeter", response.getTownName());
    assertEquals("E", response.getRegion().name());
    assertEquals("EX2 4LU", response.getPostcode());
    assertEquals(100040239948L, Long.parseLong(response.getUprn()));
    assertNull(response.getEstabUprn());
    assertNull(response.getCaseEvents());

    Optional<CachedCase> cachedCase =
        dataRepo.readCachedCaseByUPRN(UniquePropertyReferenceNumber.create(response.getUprn()));
    log.with(cachedCase).info("The fake case that has been created in Firestore");
    assertTrue(cachedCase.isPresent());
  }

  @Given("the fake case does not already exist in Firestore")
  public void the_fake_case_does_not_already_exist_in_Firestore() throws CTPException {

    log.info(
        "Make sure that the case does not already exist in Firestore otherwise a NEW_ADDRESS_REPORTED event will not get created");
    UniquePropertyReferenceNumber uprn = UniquePropertyReferenceNumber.create(uprnStr);
    Optional<CachedCase> cachedCase = dataRepo.readCachedCaseByUPRN(uprn);
    log.with("uprn", uprnStr).info("The uprn of the case we're looking for");
    if (cachedCase.isPresent()) {
      log.with("uprn", uprnStr)
          .info("The case already exists in Firestore so we need to delete it for the test..");
      dataRepo.deleteCachedCase(cachedCase.get().getId());
    }
  }

  @Given("an empty queue exists for sending NewAddressReported events")
  public void an_empty_queue_exists_for_sending_NewAddressReported_events() throws CTPException {
    String eventTypeAsString = "NEW_ADDRESS_REPORTED";
    log.info("Creating queue for events of type: '" + eventTypeAsString + "'");
    EventType eventType = EventType.valueOf(eventTypeAsString);
    queueName = rabbit.createQueue(eventType);
    log.info("Flushing queue: '" + queueName + "'");

    rabbit.flushQueue(queueName);
  }

  @Then("the CC SVC must publish a new address event to RM with the fake CaseID")
  public void the_CC_SVC_must_publish_a_new_address_event_to_RM_with_the_fake_CaseID()
      throws CTPException {
    log.info(
        "Check that a NEW_ADDRESS_REPORTED event has now been put on the empty queue, named {}, ready to be picked up by RM",
        queueName);

    String clazzName = "NewAddress.class";
    String timeout = "2000ms";

    log.info(
        "Getting from queue: '{}' and converting to an object of type '{}', with timeout of '{}'",
        queueName,
        clazzName,
        timeout);

    NewAddressReportedEvent newAddressReportedEvent =
        rabbit.getMessage(queueName, NewAddressReportedEvent.class, RABBIT_TIMEOUT);

    assertNotNull(newAddressReportedEvent);

    Header newAddressReportedHeader = newAddressReportedEvent.getEvent();
    assertNotNull(newAddressReportedHeader);
    assertEquals("NEW_ADDRESS_REPORTED", newAddressReportedHeader.getType().toString());
    assertEquals("CONTACT_CENTRE_API", newAddressReportedHeader.getSource().toString());
    assertEquals("CC", newAddressReportedHeader.getChannel().toString());
    assertNotNull(newAddressReportedHeader.getDateTime());
    assertNotNull(newAddressReportedHeader.getTransactionId());

    NewAddressPayload newAddressReportedPayload = newAddressReportedEvent.getPayload();
    assertNotNull(newAddressReportedPayload);

    NewAddress newAddress = newAddressReportedPayload.getNewAddress();
    assertNull(newAddress.getSourceCaseId());

    CollectionCaseNewAddress collectionCase = newAddress.getCollectionCase();
    assertNotNull(collectionCase.getId());
    assertEquals("HH", collectionCase.getCaseType());
    assertEquals("CENSUS", collectionCase.getSurvey());
    assertNull(collectionCase.getFieldCoordinatorId());
    assertNull(collectionCase.getFieldOfficerId());

    Address address = collectionCase.getAddress();
    assertEquals("1 West Grove Road", address.getAddressLine1());
    assertEquals("", address.getAddressLine2());
    assertEquals("", address.getAddressLine3());
    assertEquals("Exeter", address.getTownName());
    assertEquals("EX2 4LU", address.getPostcode());
    assertEquals("E", address.getRegion());
    assertEquals("HH", address.getAddressType());
    assertEquals("U", address.getAddressLevel());
    assertEquals("Household", address.getEstabType());
    assertNull(address.getLatitude());
    assertNull(address.getLongitude());
    assertEquals(uprnStr, address.getUprn());
  }

  @And("an empty queue exists for sending SurveyLaunched events")
  public void an_empty_queue_exists_for_sending_SurveyLaunched_events() throws CTPException {
    EventType eventType = EventType.valueOf("SURVEY_LAUNCHED");
    log.info("Creating queue for events of type: '" + eventType + "'");
    queueName = rabbit.createQueue(eventType);
    log.info("Flushing queue: '" + queueName + "'");
    rabbit.flushQueue(queueName);
  }

  @When("CC Advisor selects the survey launch")
  public void cc_Advisor_selects_the_launch() {
    try {
      log.with(caseId).info("Calling survey launch endpoint");
      final String response = requestSurveyLaunch();
      log.info("SURVEY LAUNCH: The response from " + telephoneEndpointUrl);
      assertNotNull("SURVEY LAUNCH failure", response);
    } catch (Exception e) {
      fail("SURVEY launch HAS FAILED - the contact centre does not give a response code of 200");
    }
  }

  private String requestSurveyLaunch() {
    log.with(telephoneEndpointUrl).info("The url for requesting the survey launch");
    return getRestTemplate().getForObject(telephoneEndpointUrl, String.class);
  }

  @Then("a Survey Launched event is emitted to RM")
  public void aSurveyLaunchedEventIsEmittedToRMWhichContainsTheLaunchStatusType() {
    log.info(
        "Check that a SURVEY_LAUNCHED event has now been put on the empty queue, named {}, ready to be picked up by RM",
        queueName);

    String clazzName = "SurveyLaunchedEvent.class";
    String timeout = "2000ms";

    log.info(
        "Getting from queue: '{}' and converting to an object of type '{}', with timeout of '{}'",
        queueName,
        clazzName,
        timeout);

    SurveyLaunchedEvent launchedEvent = null;
    try {
      launchedEvent =
          rabbit.getMessage(
              queueName, SurveyLaunchedEvent.class, TimeoutParser.parseTimeoutString(timeout));
    } catch (Exception exception) {
      fail("SURVEY launch HAS FAILED - the contact centre does not give a response code of 200");
    }

    assertNotNull(launchedEvent.getEvent());
    assertEquals(EventType.SURVEY_LAUNCHED, launchedEvent.getEvent().getType());
    assertNotNull(launchedEvent.getPayload().getResponse().getQuestionnaireId());
    assertNotNull(launchedEvent.getPayload().getResponse().getCaseId());
    assertEquals(agentId, launchedEvent.getPayload().getResponse().getAgentId());
  }

  @Given("the CC agent has selected an address that is not of addressType CE, HH, or SPG")
  public void the_CC_agent_has_selected_an_address_that_is_not_of_addressType_CE_HH_or_SPG() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("addresses")
            .queryParam("input", "Public Telephone 13M From 11 Nine Acres");

    ResponseEntity<AddressQueryResponseDTO> addressQueryResponse =
        getRestTemplate()
            .exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<AddressQueryResponseDTO>() {});

    log.with(addressQueryResponse).info("The address query response here");

    AddressQueryResponseDTO addressQueryBody = addressQueryResponse.getBody();

    List<AddressDTO> addressesFound = addressQueryBody.getAddresses();

    String addressToFind =
        "Public Telephone 13M From 11 Nine Acres On Unnamed Road, "
            + "Steep Marsh Bungalows, Steep, Petersfield, GU32 2BW";
    String addressFound = "";
    int indexFound = 500;
    log.info(
        "The indexFound value defaults to 500 as that will cause an exception if it does not get reset in the while loop");
    for (int i = 0; i < addressesFound.size(); i++) {
      addressFound = addressesFound.get(i).getFormattedAddress();
      if (addressFound.equals(addressToFind)) {
        log.with(addressFound).info("This is the address that was found in AIMS");
        indexFound = i;
        break;
      }
    }
    assertEquals(
        "The address query response does not contain the correct address",
        addressToFind,
        addressFound);

    uprnStr = addressesFound.get(indexFound).getUprn();
    String addressTypeFound = addressesFound.get(indexFound).getAddressType().name();
    log.with(addressTypeFound).info("The addressType of the address found");
    assertNotEquals("CE", addressTypeFound);
    assertNotEquals("HH", addressTypeFound);
    assertNotEquals("SPG", addressTypeFound);
  }

  @Then("the CC SVC must also return a {string} error")
  public void the_CC_SVC_must_also_return_a_error(String expectedErr) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprnStr);
    final String ccUprnEndpointUrl = builder.build().encode().toUri().toString();

    log.info(
        "As the case does not exist in the case service the endpoint {}, like the AIMS endpoint, should also throw a 404 error.",
        ccUprnEndpointUrl);

    status = "";

    try {
      getRestTemplate()
          .exchange(
              builder.build().encode().toUri(),
              HttpMethod.GET,
              null,
              new ParameterizedTypeReference<List<CaseDTO>>() {});
    } catch (RestClientException e) {
      log.with(e.getMessage())
          .info("catching the error returned by the getCaseByUprn cc service endpoint");
      status = e.getMessage().substring(0, 13);
    }

    log.info("The response status: " + status);

    assertEquals(
        "THE CASE SHOULD NOT EXIST - the contact centre service endpoint should give a response code of 404",
        expectedErr,
        status);
  }
}
