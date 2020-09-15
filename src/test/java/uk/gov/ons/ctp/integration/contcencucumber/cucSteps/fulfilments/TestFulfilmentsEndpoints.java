package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.fulfilments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import cucumber.api.PendingException;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import io.swagger.client.model.AddressDTO;
import io.swagger.client.model.AddressQueryResponseDTO;
import io.swagger.client.model.CaseDTO;
import io.swagger.client.model.CaseType;
import io.swagger.client.model.FulfilmentDTO;
import io.swagger.client.model.PostalFulfilmentRequestDTO;
import io.swagger.client.model.Region;
import io.swagger.client.model.ResponseDTO;
import io.swagger.client.model.SMSFulfilmentRequestDTO;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.common.domain.UniquePropertyReferenceNumber;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.event.EventPublisher.EventType;
import uk.gov.ons.ctp.common.event.model.FulfilmentPayload;
import uk.gov.ons.ctp.common.event.model.FulfilmentRequest;
import uk.gov.ons.ctp.common.event.model.FulfilmentRequestedEvent;
import uk.gov.ons.ctp.common.event.model.Header;
import uk.gov.ons.ctp.common.rabbit.RabbitHelper;
import uk.gov.ons.ctp.common.util.TimeoutParser;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.contcencucumber.cloud.CachedCase;
import uk.gov.ons.ctp.integration.contcencucumber.cucSteps.ResetMockCaseApiAndPostCasesBase;
import uk.gov.ons.ctp.integration.contcencucumber.main.service.ProductService;

public class TestFulfilmentsEndpoints extends ResetMockCaseApiAndPostCasesBase {

  private List<FulfilmentDTO> fulfilmentDTOList;
  private AddressQueryResponseDTO addressQueryResponseDTO;
  private String addressSearchString;
  private String uprnStr;
  private List<CaseDTO> caseDTOList;
  private CaseDTO caseDTO;
  private String requestChannel = "";
  private URI caseForUprnUrl;
  private URI productsUrl;
  private List<CaseDTO> listOfCasesWithUprn;
  private List<Product> listOfProducts;
  private RabbitHelper rabbit;
  private String queueName;
  private FulfilmentRequestedEvent fulfilmentRequestedEvent;
  private String caseId;
  private String productCodeSelected;
  private Exception fulfillmentException;

  @Autowired private ProductService productService;

  private static final String RABBIT_EXCHANGE = "events";

  private static final Logger log = LoggerFactory.getLogger(TestFulfilmentsEndpoints.class);

  @Before("@SetUp")
  public void setup() throws CTPException {
    rabbit = RabbitHelper.instance(RABBIT_EXCHANGE);
    fulfilmentRequestedEvent = null;
  }

  @Given("I Search fulfilments")
  public void i_Search_fulfilments() {
    searchFulfillments(caseDTO.getCaseType().name(), caseDTO.getRegion().name(), "true");
  }

  @Given("I Search fulfilments {string} {string} {string}")
  public void i_Search_fulfilments(String caseType, String region, String individual) {
    searchFulfillments(caseType, region, individual);
  }

  private void searchFulfillments(String caseType, String region, String individual) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("fulfilments")
            .queryParam("caseType", caseType)
            .queryParam("region", region)
            .queryParam("individual", individual);

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

  @Then("A list of fulfilments is returned of the correct products {string} {string} {string}")
  public void a_list_of_fulfilments_is_returned_of_the_correct_products(
      String caseType, String region, String individual) throws CTPException {

    this.requestChannel = "CC";
    List<Product> expectedProducts = getExpectedProducts(caseType, region, individual);

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
  public void i_have_a_valid_address_search_String(String addressSearchString) {
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
      this.uprnStr = addressList.get(0).getUprn();
      assertEquals("Should have returned the correct UPRN", expectedUPRN, this.uprnStr);
    }
  }

  @And("cached cases for the UPRN do not already exist")
  public void cachedCasesDoNotAlreadyExist() throws CTPException {
    UniquePropertyReferenceNumber uprn = UniquePropertyReferenceNumber.create(uprnStr);
    List<CachedCase> cachedCases = dataRepo.readCachedCasesByUprn(uprn);
    for (CachedCase cc : cachedCases) {
      dataRepo.deleteCachedCase(cc.getId());
    }
  }

  @When("I Search cases By UPRN")
  public void i_Search_cases_By_UPRN() {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment("uprn")
            .pathSegment(uprnStr);
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
  public void the_correct_cases_for_my_UPRN_are_returned(String caseIds) {
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
              assertEquals("Cases must have the correct UPRN", uprnStr, caseDetails.getUprn());
              assertTrue(
                  "Cases must have the correct ID" + caseIds,
                  caseIdList.contains(caseDetails.getId().toString()));
            });
      } catch (NullPointerException npe) {
        fail("Null pointer exception on case list for UPRN: " + uprnStr);
      }
    }
  }

  @Given("I have a valid case from my search UPRN")
  public void i_have_a_valid_case_from_my_search_UPRN() {
    caseDTO = caseDTOList.isEmpty() ? null : caseDTOList.get(0);
    requestChannel = "CC";
  }

  @Then("the correct fulfilments are returned for my case")
  public void the_correct_fulfilments_are_returned_for_my_case() throws CTPException {
    List<Product> expectedProducts =
        getExpectedProducts(caseDTO.getCaseType().name(), caseDTO.getRegion().name(), "true");
    List<String> expectedCodes =
        expectedProducts.stream().map(ex -> ex.getFulfilmentCode()).collect(Collectors.toList());

    if (caseDTO != null) {
      assertEquals(
          "Fulfilments list size should be " + expectedProducts.size(),
          Integer.valueOf(expectedProducts.size()),
          Integer.valueOf(fulfilmentDTOList.size()));
      fulfilmentDTOList.forEach(
          fulfilment -> {
            assertTrue(
                "Case: " + caseDTO + " Fulfilment should be of correct code ",
                expectedCodes.contains(fulfilment.getFulfilmentCode()));
          });
    }
  }

  private List<Product> getExpectedProducts(
      final String caseType, final String region, final String individual) throws CTPException {

    return productService
        .getProducts()
        .stream()
        .filter(p1 -> (containsCaseType(p1, caseType)))
        .filter(p2 -> (containsRegion(p2, region)))
        .filter(p3 -> containsChannel(p3))
        .filter(p4 -> p4.getIndividual().equals(Boolean.parseBoolean(individual)))
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
        UniquePropertyReferenceNumber.create(listOfCasesWithUprn.get(0).getUprn()));
    assertEquals(
        "The caseType found is not the expected one",
        CaseType.valueOf(strCaseType),
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
        new UniquePropertyReferenceNumber(listOfCasesWithUprn.get(0).getUprn()));
    assertEquals(
        "The caseType found is not the expected one",
        strCaseType,
        listOfCasesWithUprn.get(0).getCaseType().name());
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

  @When("CC Advisor selects the product code for productGroup {string} deliveryChannel {string}")
  public void ccAdvisorSelectsTheProductCodeForProductGroupDeliveryChannel(
      String strProductGroup, String strDeliveryChannel) {
    productCodeSelected =
        getSelectedProductCodeFromListOfProducts(strProductGroup, strDeliveryChannel);
  }

  private String getSelectedProductCodeFromListOfProducts(
      final String strProductGroup, final String strDeliveryChannel) throws PendingException {
    String prodCodeSelected = null;
    for (Product p : listOfProducts) {
      String productGroup = p.getProductGroup().toString().toUpperCase();
      String deliveryChannel = p.getDeliveryChannel().toString().toUpperCase();
      if (productGroup.equals(strProductGroup)
          && deliveryChannel.equals(strDeliveryChannel)
          && p.getFulfilmentCode() != null) {
        prodCodeSelected = p.getFulfilmentCode();
      }
    }
    log.info("The product code selected is: " + prodCodeSelected);
    if (prodCodeSelected == null) {
      throw new PendingException(
          "The Product Reference Service contains no products that match this combination of productGroup ("
              + strProductGroup
              + ") and deliveryChannel ("
              + strDeliveryChannel
              + ")");
    }
    return prodCodeSelected;
  }

  @And("Requests a fulfillment for the case and delivery channel {string}")
  public void requestsAFulfillmentForTheCaseAndDeliveryChannel(final String strDeliveryChannel) {
    fulfillmentException = null;
    try {
      log.with(caseId).info("Now requesting a postal fulfilment for this case id..");
      ResponseEntity<ResponseDTO> fulfilmentRequestResponse;
      if (strDeliveryChannel.equalsIgnoreCase("SMS")) {
        fulfilmentRequestResponse = requestFulfilmentBySMS(caseId, productCodeSelected);
      } else {
        fulfilmentRequestResponse = requestFulfilmentByPost(caseId, productCodeSelected);
      }

      if (fulfillmentException != null) {
        throw fulfillmentException;
      }
      assertNotNull("Fulfillment Response is NULL", fulfilmentRequestResponse);

      HttpStatus contactCentreStatus = fulfilmentRequestResponse.getStatusCode();
      log.with(contactCentreStatus)
          .info("REQUEST FULFILMENT: The response from " + productsUrl.toString());
      assertEquals(
          "REQUEST FULFILMENT HAS FAILED - the contact centre does not give a response code of 200",
          HttpStatus.OK,
          contactCentreStatus);
    } catch (Exception e) {
      log.error(
          "REQUEST FULFILMENT HAS FAILED: An unexpected error has occurred. Case ID: " + caseId);
      log.error(e.getMessage());
      fail();
    }
  }

  @And("Requests a fulfillment for the case and title {string} forename {string} surname {string}")
  public void requestsAFulfillmentForTheCaseAndTitleForenameSurname(
      String title, String forename, String surname) {
    fulfillmentException = null;
    log.with(caseId).info("Now requesting a postal fulfilment for this case id..");
    requestFulfilmentByPost(caseId, productCodeSelected, title, forename, surname);
  }

  @Then("an exception is thrown stating {string}")
  public void anExceptionIsThrownStating(String expectedExceptionMessage) {
    assertTrue(
        "Exception must contain message: " + expectedExceptionMessage,
        fulfillmentException.getMessage().contains(expectedExceptionMessage));
  }

  @Then(
      "a fulfilment request event is emitted to RM for UPRN = {string} addressType = {string} individual = {string} and region = {string}")
  public void
      a_fulfilment_request_event_is_emitted_to_RM_for_UPRN_addressType_individual_and_region(
          String expectedAddressType, String individual) throws CTPException {
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
    Header fulfilmentRequestedHeader = fulfilmentRequestedEvent.getEvent();
    assertNotNull(fulfilmentRequestedHeader);
    FulfilmentPayload fulfilmentPayload = fulfilmentRequestedEvent.getPayload();
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
    // SPG and CE indiv product requests do not need an indiv id creating (see CaseServiceImpl, line
    // 435

    if (individual.equals("true") && expectedAddressType.equals("HH")) {
      assertNotNull(fulfilmentRequest.getIndividualCaseId());
    } else {
      assertNull(fulfilmentRequest.getIndividualCaseId());
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
    return requestFulfilmentByPost(caseId, productCode, "Mrs", "Joanna", "Bloggs");
  }

  private ResponseEntity<ResponseDTO> requestFulfilmentByPost(
      final String caseId,
      final String productCode,
      final String title,
      final String forename,
      final String surname) {
    final PostalFulfilmentRequestDTO postalFulfilmentRequest =
        new PostalFulfilmentRequestDTO()
            .caseId(UUID.fromString(caseId))
            .title(title)
            .forename(forename)
            .surname(surname)
            .fulfilmentCode(productCode)
            .dateTime(OffsetDateTime.now(ZoneId.of("Z")).withNano(0).toString());
    return requestFulfilmentByPost(postalFulfilmentRequest);
  }

  private ResponseEntity<ResponseDTO> requestFulfilmentByPost(
      final PostalFulfilmentRequestDTO postalFulfilmentRequest) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(postalFulfilmentRequest.getCaseId().toString())
            .pathSegment("fulfilment")
            .pathSegment("post");

    ResponseEntity<ResponseDTO> requestFulfilmentByPostResponse = null;
    URI fulfilmentByPostUrl = builder.build().encode().toUri();

    log.with(fulfilmentByPostUrl).info("The url for requesting the postal fulfilment");
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
      fulfillmentException = httpClientErrorException;
    }
    return requestFulfilmentByPostResponse;
  }

  private ResponseEntity<ResponseDTO> requestFulfilmentBySMS(String caseId, String productCode) {
    final UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(ccBaseUrl)
            .port(ccBasePort)
            .pathSegment("cases")
            .pathSegment(caseId)
            .pathSegment("fulfilment")
            .pathSegment("sms");

    ResponseEntity<ResponseDTO> requestFulfilmentBySMSResponse = null;
    URI fulfilmentBySMSUrl = builder.build().encode().toUri();

    log.with(fulfilmentBySMSUrl).info("The url for requesting the SMS fulfilment");

    SMSFulfilmentRequestDTO smsFulfilmentRequestDTO = new SMSFulfilmentRequestDTO();
    smsFulfilmentRequestDTO
        .caseId(UUID.fromString(caseId))
        .fulfilmentCode(productCode)
        .dateTime(OffsetDateTime.now(ZoneId.of("Z")).withNano(0).toString());
    smsFulfilmentRequestDTO.setTelNo("447777777777");

    HttpEntity<SMSFulfilmentRequestDTO> requestEntity = new HttpEntity<>(smsFulfilmentRequestDTO);

    try {
      requestFulfilmentBySMSResponse =
          getRestTemplate()
              .exchange(fulfilmentBySMSUrl, HttpMethod.POST, requestEntity, ResponseDTO.class);
    } catch (HttpClientErrorException httpClientErrorException) {
      log.debug(
          "A HttpClientErrorException has occurred when trying to post to fulfilmentRequestBySMS endpoint in contact centre: "
              + httpClientErrorException.getMessage());
    }
    return requestFulfilmentBySMSResponse;
  }
}
