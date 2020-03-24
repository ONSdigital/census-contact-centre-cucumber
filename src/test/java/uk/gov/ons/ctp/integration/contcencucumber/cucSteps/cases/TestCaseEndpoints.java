package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.cases;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.CaseDTO;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.FulfilmentDTO;
import uk.gov.ons.ctp.integration.contcencucumber.cucSteps.ResetMockCaseApiAndPostCasesBase;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.Codec;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.EQJOSEProvider;
import uk.gov.ons.ctp.integration.eqlaunch.crypto.KeyStore;

public class TestCaseEndpoints extends ResetMockCaseApiAndPostCasesBase {

  private String caseId;
  private String uprn;
  private CaseDTO caseDTO;
  private List<CaseDTO> caseDTOList;
  private Exception exception;
  private static final Logger log = LoggerFactory.getLogger(TestCaseEndpoints.class);
  private String ccSmokeTestUrl;
  private String mockCaseSvcSmokeTestUrl;
  private String telephoneEndpointUrl;
  private String telephoneEndpointBody1;
  private String telephoneEndpointBody2;

  @Value("${keystore}")
  private String keyStore;

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
    assertEquals(
        "Case Query Response UPRN must match", caseDTO.getUprn().getValue(), uprn.longValue());
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
    } catch (HttpClientErrorException httpClientErrorException) {
      this.exception = httpClientErrorException;
    } catch (HttpServerErrorException httpServerErrorException) {
      this.exception = httpServerErrorException;
    }
  }

  @Then("An error is thrown and no case is returned {string}")
  public void an_error_is_thrown_and_no_case_is_returned(String httpError) {
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
      log.error("LAUNCHING EQ FOR HH HAS FAILED: An unexpected has occurred.");
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
     * The following assert will need to be changed if the eq_id value, which is hard-coded in the CCSVC, is updated
     */
    assertEquals("Must have the correct eq id", "census", result1.get("eq_id"));

    /*
     * The following assert will need to be changed if the form_type value, which is hard-coded in the CCSVC, is updated
     */
    assertEquals("Must have the correct form type", "H", result1.get("form_type"));

    assertNotEquals("Must have different tx_id values", result1.get("tx_id"), result2.get("tx_id"));
    assertEquals("Must have the correct ru_ref value", "100041045599", result1.get("ru_ref"));
    assertEquals("Must have the correct language_code value", "en", result1.get("language_code"));
    assertEquals("Must have the correct user_id value", "1", result1.get("user_id"));
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
     * The following assert will need to be changed if the period_id value, which is hard-coded in the CCSVC, is updated
     */
    assertEquals("Must have the correct period id", "2019", result1.get("period_id"));

    assertNotEquals("Must have different iat values", result1.get("iat"), result2.get("iat"));
    assertNotEquals("Must have different jti values", result1.get("jti"), result2.get("jti"));
    assertEquals("Must have the correct region code", "GB-ENG", result1.get("region_code"));
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
            .queryParam("agentId", 1)
            .queryParam("individual", isIndividual);

    telephoneEndpointUrl = builder.build().encode().toUri().toString();
    log.info("Using the following endpoint to launch EQ: " + telephoneEndpointUrl);
    return getRestTemplate().getForEntity(builder.build().encode().toUri(), String.class);
  }
}
