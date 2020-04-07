package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.fulfilments;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.event.EventPublisher.EventType;
import uk.gov.ons.ctp.common.event.model.*;
import uk.gov.ons.ctp.common.model.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.rabbit.RabbitHelper;
import uk.gov.ons.ctp.common.util.TimeoutParser;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.*;
import uk.gov.ons.ctp.integration.contcencucumber.cucSteps.ResetMockCaseApiAndPostCasesBase;
import uk.gov.ons.ctp.integration.contcencucumber.main.service.ProductService;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class TestFulfilmentsEndpoints extends ResetMockCaseApiAndPostCasesBase {

  private List<FulfilmentDTO> fulfilmentDTOList;
  private AddressQueryResponseDTO addressQueryResponseDTO;
  private String addressSearchString;
  private String uprn;
  private List<CaseDTO> caseDTOList;
  private CaseDTO caseDTO;
  private String requestChannel = "";
  private RabbitHelper rabbit;
  private String queueName;
  private FulfilmentRequestedEvent fulfilmentRequestedEvent;
  private Header fulfilmentRequestedHeader;
  private FulfilmentPayload fulfilmentPayload;
  private String caseId;
  private String productCodeSelected;
  private URI caseForUprnUrl;
  private URI productsUrl;
  private List<CaseDTO> listOfCasesWithUprn;
  private List<Product> listOfProducts;

  @Autowired private ProductService productService;
  private URI fulfilmentByPostUrl;

  private static final String RABBIT_EXCHANGE = "events";

  private static final Logger log = LoggerFactory.getLogger(TestFulfilmentsEndpoints.class);

  @Before("@SetUp")
  public void setup() throws CTPException {
    rabbit = RabbitHelper.instance(RABBIT_EXCHANGE);
    fulfilmentRequestedEvent = null;
  }

  @When("I Search fulfilments {string} {string} {string}")
  public void i_Search_fulfilments(
      final String caseType, final String region, final String individual) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("/fulfilments")
            .queryParam("caseType", caseType)
            .queryParam("region", region)
            .queryParam("individual", individual);
    searchFulfillments(builder);
  }

  @Then("A list of fulfilments is returned of the correct products {string} {string} {string}")
  public void a_list_of_fulfilments_is_returned_of_the_correct_products(
      final String caseType, final String region, final String individual) throws CTPException {

    boolean isIndividual = Boolean.parseBoolean(individual);
    this.requestChannel = "CC";
    List<DeliveryChannel> deliveryChannels = new ArrayList<>();
    List<Product> expectedProducts =
        getExpectedProducts(caseType, region, isIndividual, deliveryChannels);

    assertEquals(
        "Fulfilments list size should be " + expectedProducts.size(),
        Integer.valueOf(expectedProducts.size()),
        Integer.valueOf(fulfilmentDTOList.size()));
    fulfilmentDTOList.forEach(
        fulfilment -> {
          assertTrue(
              "Fulfilment should be of correct caseType",
              fulfilmentContainsCaseType(fulfilment, caseType));
          assertTrue(
              "Fulfilment should be of correct region",
              fulfilment.getRegions().contains(Region.valueOf(region)));
        });
  }

  private boolean fulfilmentContainsCaseType(final FulfilmentDTO dto, final String caseType) {
    boolean containsCaseType = false;
    for (CaseType caseType1 : dto.getCaseTypes()) {
      if (caseType1.name().equalsIgnoreCase(caseType)) {
        containsCaseType = true;
      }
    }
    return containsCaseType;
  }

  @Given("I have a valid address search String {string}")
  public void i_have_a_valid_address_search_String(final String addressSearchString) {
    this.addressSearchString = addressSearchString;
  }

  @When("I Search Addresses By Address Search String")
  public void i_Search_Addresses_By_Address_Search_String() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("addresses")
            .queryParam("input", addressSearchString);
    addressQueryResponseDTO =
        getRestTemplate()
            .getForObject(builder.build().encode().toUri(), AddressQueryResponseDTO.class);
  }

  @Then("A list of addresses for my search is returned containing the address I require")
  public void a_list_of_addresses_for_my_search_is_returned_containing_the_address_I_require() {
    assertNotNull("Address Query Response must not be null", addressQueryResponseDTO);
    assertTrue("Address list size must be > 0", addressQueryResponseDTO.getAddresses().size() > 0);
  }

  @Given("I have a valid UPRN from my found address {string}")
  public void i_have_a_valid_UPRN_from_my_found_address(final String expectedUPRN) {

    List<AddressDTO> addressList =
        addressQueryResponseDTO
            .getAddresses()
            .stream()
            .filter(aq -> aq.getUprn().equals(expectedUPRN))
            .collect(Collectors.toList());
    if (addressList.isEmpty()) {
      fail(
          "i_have_a_valid_UPRN_from_my_found_address - filtered address list must not be empty: expected UPRN "
              + expectedUPRN);
    } else {
      this.uprn = addressList.get(0).getUprn();
      assertEquals("Should have returned the correct UPRN", expectedUPRN, this.uprn);
    }
  }

  @When("I Search cases By UPRN")
  public void i_Search_cases_By_UPRN() {
    getCasesByUPRN();
  }

  private void getCasesByUPRN() {

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
      fail(httpClientErrorException.getMessage());
    }
  }

  @Then("the correct cases for my UPRN are returned {string}")
  public void the_correct_cases_for_my_UPRN_are_returned(final String caseIds) {
    getValidCasesByCaseIds(caseIds);
  }

  private void getValidCasesByCaseIds(final String caseIds) {
    if (caseIds.isEmpty()) {
      assertNull(caseDTOList);
      caseDTOList = new ArrayList<>();
    } else {
      List<String> caseIdList =
          Arrays.stream(caseIds.split(","))
              .filter(item -> !item.isEmpty())
              .collect(Collectors.toList());
      try {
        caseDTOList.forEach(
            caseDetails -> {
              assertEquals(
                  "Cases must have the correct UPRN",
                  uprn,
                  Long.toString(caseDetails.getUprn().getValue()));
              assertTrue(
                  "Cases must have the correct ID" + caseIds,
                  caseIdList.contains(caseDetails.getId().toString()));
            });
      } catch (NullPointerException npe) {
        fail("Null pointer exception on case list for UPRN: " + uprn);
      }
    }
  }

  @Given("I have a valid case from my search UPRN")
  public void i_have_a_valid_case_from_my_search_UPRN() {
    caseDTO = caseDTOList.isEmpty() ? null : caseDTOList.get(0);
    requestChannel = "CC";
  }

  @When("I Search fulfilments")
  public void i_Search_fulfilments() {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("/fulfilments")
            .queryParam("caseType", caseDTO.getCaseType())
            .queryParam("region", caseDTO.getRegion())
            .queryParam("individual", getIndividualStatusAndCaseType(caseDTO));
    searchFulfillments(builder);
  }

  @When("I Search fulfilments {string} {string}")
  public void i_Search_fulfilments(final String deliveryChannel, final String individual) {
    boolean isIndividual = Boolean.parseBoolean(individual);
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("/fulfilments")
            .queryParam("caseType", caseDTO.getCaseType())
            .queryParam("region", caseDTO.getRegion())
            .queryParam("individual", isIndividual)
            .queryParam("deliveryChannel", deliveryChannel);

    searchFulfillments(builder);
  }

  private void searchFulfillments(final UriComponentsBuilder builder) {
    try {
      ResponseEntity<List<FulfilmentDTO>> fulfilmentResponse =
          getRestTemplate()
              .exchange(
                  builder.build().encode().toUri(),
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<FulfilmentDTO>>() {});
      fulfilmentDTOList = fulfilmentResponse.getBody();
    } catch (HttpClientErrorException httpClientErrorException) {
      fail(httpClientErrorException.getMessage());
    }
  }

  @Then("the correct fulfilments are returned for my case")
  public void the_correct_fulfilments_are_returned_for_my_case() throws CTPException {
    correctFulfilmentsAreReturnedForCase(getIndividualStatusAndCaseType(caseDTO));
  }

  @Then("the correct fulfilments are returned for my case {string} {string} {string} {string}")
  public void the_correct_fulfilments_are_returned_for_my_case(
      final String caseType,
      final String region,
      final String deliveryChannel,
      final String individual)
      throws CTPException {
    final boolean isIndividual = Boolean.parseBoolean(individual);
    caseDTO.setAllowedDeliveryChannels(Arrays.asList(DeliveryChannel.valueOf(deliveryChannel)));
    correctFulfilmentsAreReturnedForCase(isIndividual);
  }

  @Then(
      "a fulfilment request event is emitted to RM for UPRN = {string} addressType = {string} individual = {string} and region = {string}")
  public void
  a_fulfilment_request_event_is_emitted_to_RM_for_UPRN_addressType_individual_and_region(
      String expectedUprn, String expectedAddressType, String individual, String expectedRegion)
      throws CTPException {
    log.info(
        "Check that a FULFILMENT_REQUESTED event has now been put on the empty queue, named "
            + queueName
            + ", ready to be picked up by RM");

    String clazzName = "FulfilmentRequestedEvent.class";
    String timeout = "2000ms";

    log.info(
        "Getting from queue: '"
            + queueName
            + "' and converting to an object of type '"
            + clazzName
            + "', with timeout of '"
            + timeout
            + "'");

    fulfilmentRequestedEvent =
        (FulfilmentRequestedEvent)
            rabbit.getMessage(
                queueName,
                FulfilmentRequestedEvent.class,
                TimeoutParser.parseTimeoutString(timeout));

    assertNotNull(fulfilmentRequestedEvent);
    fulfilmentRequestedHeader = fulfilmentRequestedEvent.getEvent();
    assertNotNull(fulfilmentRequestedHeader);
    fulfilmentPayload = fulfilmentRequestedEvent.getPayload();
    assertNotNull(fulfilmentPayload);

    String expectedType = "FULFILMENT_REQUESTED";
    String expectedSource = "CONTACT_CENTRE_API";
    String expectedChannel = "CC";
    String expectedFulfilmentCode = productCodeSelected;
    String expectedCaseId = caseId;

    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'type'",
        expectedType,
        fulfilmentRequestedHeader.getType().name());
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'source'",
        expectedSource,
        fulfilmentRequestedHeader.getSource().name());
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'channel'",
        expectedChannel,
        fulfilmentRequestedHeader.getChannel().name());
    assertNotNull(fulfilmentRequestedHeader.getDateTime());
    assertNotNull(fulfilmentRequestedHeader.getTransactionId());

    FulfilmentRequest fulfilmentRequest = fulfilmentPayload.getFulfilmentRequest();
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'fulfilmentCode'",
        expectedFulfilmentCode,
        fulfilmentRequest.getFulfilmentCode());
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'caseId'",
        expectedCaseId,
        fulfilmentRequest.getCaseId());
    Address address = fulfilmentRequest.getAddress();
    // SPG and CE indiv product requests do not need an indiv id creating (see CaseServiceImpl, line
    // 435
    if (individual.equals("true") && address.getAddressType().equals("HH")) {
      assertNotNull(fulfilmentRequest.getIndividualCaseId());
    } else {
      assertNull(fulfilmentRequest.getIndividualCaseId());
    }
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'uprn'",
        expectedUprn,
        address.getUprn());
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'addressType'",
        expectedAddressType,
        address.getAddressType());
    assertEquals(
        "The FulfilmentRequested event contains an incorrect value of 'region'",
        expectedRegion,
        address.getRegion());
  }

  private void correctFulfilmentsAreReturnedForCase(boolean individual) throws CTPException {
    List<Product> expectedProducts =
        getExpectedProducts(
            caseDTO.getCaseType(),
            caseDTO.getRegion(),
            individual,
            caseDTO.getAllowedDeliveryChannels());
    List<String> expectedCodes =
        expectedProducts.stream().map(ex -> ex.getFulfilmentCode()).collect(Collectors.toList());

    if (caseDTO != null) {
      assertEquals(
          "Fulfilments list size should be " + expectedProducts.size(),
          Integer.valueOf(expectedProducts.size()),
          Integer.valueOf(fulfilmentDTOList.size()));
      if (expectedProducts.size() == 0) {
        throw new cucumber.api.PendingException(
            "Not able to test until Product Reference Service is updated");
      }

      fulfilmentDTOList.forEach(
          fulfilment -> {
            assertTrue(
                "Case: " + caseDTO + " Fulfilment should be of correct code ",
                expectedCodes.contains(fulfilment.getFulfilmentCode()));
          });
    }
  }

  private boolean getIndividualStatusAndCaseType(final CaseDTO caseDTO) {
    boolean isIndividual = false;
    if (caseDTO.getCaseType().equalsIgnoreCase("HI")) {
      isIndividual = true;
      caseDTO.setCaseType("HH");
    }
    return isIndividual;
  }

  @Given("I have a valid UPRN provided by a CC advisor {string}")
  public void i_have_a_valid_UPRN_provided_by_a_CC_advisor(final String uprn) {
    this.uprn = uprn;
  }

  @Then("I have a valid case from my search UPRN {string}")
  public void i_have_a_valid_case_from_my_search_UPRN(final String caseId) {
    requestChannel = "CC";
    getCasesByUPRN();
    getValidCasesByCaseIds(caseId);
    assertTrue(
        "There should be 1 case for given uprn: " + uprn + " but found:" + caseDTOList.size(),
        caseDTOList.size() == 1);
    caseDTO = caseDTOList.get(0);
  }

  private List<Product> getExpectedProducts(
      final String caseType,
      final String region,
      final boolean individual,
      final List<DeliveryChannel> deliveryChannels)
      throws CTPException {

    return productService
        .getProducts()
        .stream()
        .filter(p1 -> (containsCaseType(p1, caseType)))
        .filter(p2 -> (containsRegion(p2, region)))
        .filter(p3 -> containsChannel(p3))
        .filter(p4 -> p4.getIndividual().equals(individual))
        .filter(p5 -> (containsDeliveryChannel(p5, deliveryChannels)))
        .collect(Collectors.toList());
  }

  private boolean containsCaseType(final Product product, final String caseType) {
    boolean containsCaseType = false;
    for (Product.CaseType pCaseType : product.getCaseTypes()) {
      if (pCaseType.name().equalsIgnoreCase(caseType)) {
        containsCaseType = true;
      }
    }
    return containsCaseType;
  }

  private boolean containsRegion(final Product product, final String region) {
    boolean containsRegion = false;
    for (Product.Region pRegion : product.getRegions()) {
      if (pRegion.name().equalsIgnoreCase(region)) {
        containsRegion = true;
      }
    }
    return containsRegion;
  }

  private boolean containsChannel(final Product product) {
    boolean containsChannel = false;
    for (Product.RequestChannel pRequestChannel : product.getRequestChannels()) {
      if (pRequestChannel.name().equalsIgnoreCase(requestChannel)) {
        containsChannel = true;
      }
    }
    return containsChannel;
  }

  @Given("the CC advisor has provided a valid UPRN with caseType HH")
  public void the_CC_advisor_has_provided_a_valid_UPRN_with_caseType_HH() {
    try {
      ResponseEntity<List<CaseDTO>> caseUprnResponse = getCaseForUprn("1347459991");
      listOfCasesWithUprn = caseUprnResponse.getBody();
      HttpStatus contactCentreStatus = caseUprnResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("GET CASE BY UPRN: The response from " + caseForUprnUrl.toString());
      assertEquals(
          "GET CASE BY UPRN HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error("GET CASE BY UPRN HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Given("the CC advisor has provided a valid UPRN {string}")
  public void the_CC_advisor_has_provided_a_valid_UPRN(String strUprn) {
    try {
      ResponseEntity<List<CaseDTO>> caseUprnResponse = getCaseForUprn(strUprn);
      listOfCasesWithUprn = caseUprnResponse.getBody();
      HttpStatus contactCentreStatus = caseUprnResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("GET CASE BY UPRN: The response from " + caseForUprnUrl.toString());
      assertEquals(
          "GET CASE BY UPRN HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error("GET CASE BY UPRN HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @When(
      "the Case endpoint returns a case, associated with UPRN {string}, which has caseType {string}")
  public void the_Case_endpoint_returns_a_case_associated_with_UPRN_which_has_caseType(
      String strUprn, String strCaseType) {
    caseId = listOfCasesWithUprn.get(0).getId().toString();
    log.with(caseId).debug("The case id returned by getCasesWithUprn endpoint");

    UniquePropertyReferenceNumber expectedUprn = new UniquePropertyReferenceNumber(strUprn);
    assertEquals(
        "The uprn found is not the expected one",
        expectedUprn,
        listOfCasesWithUprn.get(0).getUprn());
    assertEquals(
        "The caseType found is not the expected one",
        strCaseType,
        listOfCasesWithUprn.get(0).getCaseType());
  }

  @Then(
      "the Case endpoint returns a case, associated with UPRN {string}, which has caseType {string} and addressLevel {string} and handDelivery {string}")
  public void
      the_Case_endpoint_returns_a_case_associated_with_UPRN_which_has_caseType_and_addressLevel_and_handDelivery(
          String strUprn, String strCaseType, String strAddressLevel, String strHandDelivery) {
    caseId = listOfCasesWithUprn.get(0).getId().toString();
    log.with(caseId).debug("The case id returned by getCasesWithUprn endpoint");

    UniquePropertyReferenceNumber expectedUprn = new UniquePropertyReferenceNumber(strUprn);
    assertEquals(
        "The uprn found is not the expected one",
        expectedUprn,
        listOfCasesWithUprn.get(0).getUprn());
    assertEquals(
        "The caseType found is not the expected one",
        strCaseType,
        listOfCasesWithUprn.get(0).getCaseType());
    log.with(strAddressLevel)
        .info(
            "We cannot assert that the case has this addressLevel - because the addressLevel field is not shown in the CaseDTO representation to Serco.");
    log.with(strHandDelivery)
        .info(
            "We cannot assert the the case has this value of handDelivery - because the isHandDelivery result is deliberately hidden in the CaseDTO representation to Serco.");
  }

  @Given(
      "a list of available fulfilment product codes is presented for a HH caseType where individual flag = {string} and region = {string}")
  public void
      a_list_of_available_fulfilment_product_codes_is_presented_for_a_HH_caseType_where_individual_flag_and_region(
          String individual, String region) throws CTPException {
    try {
      ResponseEntity<List<Product>> productsResponse = getProducts("HH", region, individual);
      listOfProducts = productsResponse.getBody();
      HttpStatus contactCentreStatus = productsResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("GET PRODUCTS: The response from " + productsUrl.toString());
      assertEquals(
          "GET PRODUCTS HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error("GET PRODUCTS HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Given(
      "a list of available fulfilment product codes is presented for a caseType = {string} where individual flag = {string} and region = {string}")
  public void
      a_list_of_available_fulfilment_product_codes_is_presented_for_a_caseType_where_individual_flag_and_region(
          String caseType, String individual, String region) {
    try {
      ResponseEntity<List<Product>> productsResponse = getProducts(caseType, region, individual);
      listOfProducts = productsResponse.getBody();
      HttpStatus contactCentreStatus = productsResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("GET PRODUCTS: The response from " + productsUrl.toString());
      assertEquals(
          "GET PRODUCTS HAS FAILED -  the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error("GET PRODUCTS HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
    }
  }

  @Given("an empty queue exists for sending Fulfilment Requested events")
  public void an_empty_queue_exists_for_sending_Fulfilment_Requested_events() throws CTPException {
    String eventTypeAsString = "FULFILMENT_REQUESTED";
    log.info("Creating queue for events of type: '" + eventTypeAsString + "'");
    EventType eventType = EventType.valueOf(eventTypeAsString);
    queueName = rabbit.createQueue(eventType);
    log.info("Flushing queue: '" + queueName + "'");

    rabbit.flushQueue(queueName);
  }

  @When(
      "CC Advisor selects the product code for productGroup {string},  language {string}, deliveryChannel {string}")
  public void cc_Advisor_selects_the_product_code_for_productGroup_language_deliveryChannel(
      String strProductGroup, String strLanguage, String strDeliveryChannel) {
    productCodeSelected = null;
    for (Product p : listOfProducts) {
      String productGroup = p.getProductGroup().toString().toUpperCase();
      String language = p.getLanguage();
      String deliveryChannel = p.getDeliveryChannel().toString().toUpperCase();
      if (productGroup.equals(strProductGroup)
          && language.equals(strLanguage)
          && deliveryChannel.equals(strDeliveryChannel)) {
        productCodeSelected = p.getFulfilmentCode();
      }
    }
    log.info("The product code selected is: " + productCodeSelected);
    if (productCodeSelected == null) {
      throw new cucumber.api.PendingException(
          "The Product Reference Service contains no products that match this combination of productGroup ("
              + strProductGroup
              + ") language ("
              + strLanguage
              + ") and deliveryChannel ("
              + strDeliveryChannel
              + ")");
    }

    try {
      log.with(caseId).info("Now requesting a postal fulfilment for this case id..");
      ResponseEntity<ResponseDTO> fulfilmentRequestResponse =
          requestFulfilmentByPost(caseId, productCodeSelected);
      HttpStatus contactCentreStatus = fulfilmentRequestResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("REQUEST FULFILMENT: The response from " + productsUrl.toString());
      assertEquals(
          "REQUEST FULFILMENT HAS FAILED - the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error("REQUEST FULFILMENT HAS FAILED: An unexpected error has occurred.");
      log.error(e.getMessage());
      fail();
      System.exit(0);
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

  private ResponseEntity<List<Product>> getProducts(
      String caseType, String region, String individual) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("fulfilments")
            .queryParam("caseType", caseType)
            .queryParam("region", region)
            .queryParam("individual", individual);

    ResponseEntity<List<Product>> productsResponse = null;
    productsUrl = builder.build().encode().toUri();

    try {
      productsResponse =
          getRestTemplate()
              .exchange(
                  productsUrl,
                  HttpMethod.GET,
                  null,
                  new ParameterizedTypeReference<List<Product>>() {});
    } catch (HttpClientErrorException httpClientErrorException) {
      log.debug(
          "A HttpClientErrorException has occurred when trying to get list of cases using getCaseByUprn endpoint in contact centre: "
              + httpClientErrorException.getMessage());
    }
    return productsResponse;
  }

  private ResponseEntity<ResponseDTO> requestFulfilmentByPost(String caseId, String productCode) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .pathSegment("fulfilment")
            .pathSegment("post");

    ResponseEntity<ResponseDTO> requestFulfilmentByPostResponse = null;
    fulfilmentByPostUrl = builder.build().encode().toUri();

    log.with(fulfilmentByPostUrl).info("The url for requesting the postal fulfilment");

    PostalFulfilmentRequestDTO postalFulfilmentRequest = new PostalFulfilmentRequestDTO();
    postalFulfilmentRequest.setCaseId(UUID.fromString(caseId));
    postalFulfilmentRequest.setTitle("Mrs");
    postalFulfilmentRequest.setForename("Joanna");
    postalFulfilmentRequest.setSurname("Bloggs");
    postalFulfilmentRequest.setFulfilmentCode(productCode);
    postalFulfilmentRequest.setDateTime(new Date());

    HttpEntity<PostalFulfilmentRequestDTO> requestEntity =
        new HttpEntity<>(postalFulfilmentRequest);

    try {
      requestFulfilmentByPostResponse =
          getRestTemplate()
              .exchange(fulfilmentByPostUrl, HttpMethod.POST, requestEntity, ResponseDTO.class);
    } catch (HttpClientErrorException httpClientErrorException) {
      log.debug(
          "A HttpClientErrorException has occurred when trying to post to fulfilmentRequestByPost endpoint in contact centre: "
              + httpClientErrorException.getMessage());
    }
    return requestFulfilmentByPostResponse;
  }

  private boolean containsDeliveryChannel(
      final Product product, final List<DeliveryChannel> deliveryChannels) {
    if (deliveryChannels.isEmpty()) {
      return true;
    }
    List<String> deliveryChannelNames =
        deliveryChannels.stream().map(d -> d.name()).collect(Collectors.toList());
    boolean containsDeliverChannel = false;

    for (final String deliveryChannel : deliveryChannelNames) {
      if (deliveryChannel.equalsIgnoreCase(product.getDeliveryChannel().name())) {
        containsDeliverChannel = true;
      }
    }
    return containsDeliverChannel;
  }
}
