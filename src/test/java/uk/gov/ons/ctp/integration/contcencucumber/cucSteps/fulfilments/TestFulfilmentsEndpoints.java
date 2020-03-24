package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.fulfilments;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.integration.common.product.model.Product;
import uk.gov.ons.ctp.integration.contactcentresvc.representation.*;
import uk.gov.ons.ctp.integration.contcencucumber.cucSteps.ResetMockCaseApiAndPostCasesBase;
import uk.gov.ons.ctp.integration.contcencucumber.main.service.ProductService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  @Autowired private ProductService productService;

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
      List caseIdList =
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
    if (caseDTO.getCaseType().equalsIgnoreCase("CI")) {
      isIndividual = true;
      caseDTO.setCaseType("CE");
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
