package uk.gov.ons.ctp.integration.contcencucumber.cucSteps.address;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.swagger.client.model.AddressDTO;
import io.swagger.client.model.AddressDTO.AddressTypeEnum;
import io.swagger.client.model.AddressDTO.RegionEnum;
import io.swagger.client.model.AddressQueryResponseDTO;
import io.swagger.client.model.EstabType;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.ctp.integration.contcencucumber.context.CucTestContext;

public class TestAddressEndpoints {

  private static final Logger log = LoggerFactory.getLogger(TestAddressEndpoints.class);
  private AddressQueryResponseDTO addressQueryResponseDTO;
  private String postcode = "";
  private String addressSearchString = "";
  private String addressEndpointUrl;

  @Autowired private CucTestContext context;

  @Given("I have a valid Postcode {string}")
  public void i_have_a_valid_Postcode(final String postcode) {
    this.postcode = postcode;
  }

  @When("I Search Addresses By Postcode")
  public void i_Search_Addresses_By_Postcode() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .pathSegment("postcode")
            .queryParam("postcode", postcode);
    addressQueryResponseDTO =
        context
            .getRestTemplate()
            .getForObject(builder.build().encode().toUri(), AddressQueryResponseDTO.class);
  }

  @Then("A list of addresses for my postcode is returned")
  public void a_list_of_addresses_for_my_postcode_is_returned() {
    assertNotNull("Address Query Response must not be null", addressQueryResponseDTO);
    assertNotEquals(
        "Address list size must not be zero", 0, addressQueryResponseDTO.getAddresses().size());
  }

  @Given("I have an invalid Postcode {string}")
  public void i_have_an_invalid_Postcode(String postcode) {
    this.postcode = postcode;
  }

  @When("I Search Addresses By Invalid Postcode")
  public void i_Search_Addresses_By_Invalid_Postcode() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .pathSegment("postcode")
            .queryParam("postcode", postcode);
    try {
      addressQueryResponseDTO =
          context
              .getRestTemplate()
              .getForObject(builder.build().encode().toUri(), AddressQueryResponseDTO.class);
    } catch (HttpClientErrorException hcee) {
      assertNull(" Invalid format Address Query Response must be null", addressQueryResponseDTO);
    }
  }

  @Then("An empty list of addresses for my postcode is returned")
  public void an_empty_list_of_addresses_for_my_postcode_is_returned() {
    if (addressQueryResponseDTO != null) {
      assertNotNull("Address Query Response must not be null", addressQueryResponseDTO);
      assertEquals(
          "Address list size must be zero", 0, addressQueryResponseDTO.getAddresses().size());
    }
  }

  @Given("I have a valid address {string}")
  public void i_have_a_valid_address(String addressSearchString) {
    this.addressSearchString = addressSearchString;
  }

  @When("I Search Addresses By Address Search")
  public void i_Search_Addresses_By_Address_Search() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .queryParam("input", addressSearchString);
    addressQueryResponseDTO =
        context
            .getRestTemplate()
            .getForObject(builder.build().encode().toUri(), AddressQueryResponseDTO.class);
  }

  @Then("A list of addresses for my search is returned")
  public void a_list_of_addresses_for_my_search_is_returned() {
    assertNotNull("Address Query Response must not be null", addressQueryResponseDTO);
    assertNotEquals(
        "Address list size must not be zero", 0, addressQueryResponseDTO.getAddresses().size());
  }

  @Given("I have an invalid address {string}")
  public void i_have_an_invalid_address(String addressSearchString) {
    this.addressSearchString = addressSearchString;
  }

  @When("I Search invalid Addresses By Address Search")
  public void i_Search_invalid_Addresses_By_Address_Search() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .queryParam("input", addressSearchString);
    try {
      addressQueryResponseDTO =
          context
              .getRestTemplate()
              .getForObject(builder.build().encode().toUri(), AddressQueryResponseDTO.class);
    } catch (HttpClientErrorException hcee) {
      assertNull(" Invalid format Address Query Response must be null", addressQueryResponseDTO);
    }
  }

  @Then("An empty list of addresses for my search is returned")
  public void an_empty_list_of_addresses_for_my_search_is_returned() {
    if (addressQueryResponseDTO != null) {
      assertNotNull("Address Query Response must not be null", addressQueryResponseDTO);
      assertEquals(
          "Address list size must be zero", 0, addressQueryResponseDTO.getAddresses().size());
    }
  }

  @Given("the respondent calls the CC with a fulfilment request")
  public void the_respondent_calls_the_CC_with_a_fulfilment_request() {
    log.info("Nothing to do here: the respondent calls the CC with a fulfilment request");
  }

  @Given("the respondent address exists in AIMS")
  public void the_respondent_address_exists_in_AIMS() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .queryParam("input", "1, West Grove Road, Exeter, EX2 4LU");
    addressEndpointUrl = builder.build().encode().toUri().toString();

    log.info("Using the following endpoint to check address exists in AIMS: " + addressEndpointUrl);

    ResponseEntity<String> aimsEndpointResponse =
        context.getRestTemplate().getForEntity(builder.build().encode().toUri(), String.class);
    String aimsEndpointBody = aimsEndpointResponse.getBody();
    log.with(aimsEndpointBody).info("The response body");
    log.with(aimsEndpointResponse.getStatusCode()).info("The response status");
    assertEquals(
        "THE ADDRESS MAY NOT EXIST IN AIMS - AIMS does not give a response code of 200",
        HttpStatus.OK,
        aimsEndpointResponse.getStatusCode());
  }

  @When("the CC agent searches for the address")
  public void the_CC_agent_searches_for_the_address() {
    log.info("Nothing to do here: the CC agent searches for the address");
  }

  @Then(
      "the CC SVC returns address attributes with region code, address type and establishment type")
  public void
      the_CC_SVC_returns_address_attributes_with_region_code_address_type_and_establishment_type() {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromHttpUrl(context.getCcBaseUrl())
            .port(context.getCcBasePort())
            .pathSegment("addresses")
            .queryParam("input", "1, West Grove Road, Exeter, EX2 4LU");

    ResponseEntity<AddressQueryResponseDTO> addressQueryResponse =
        context
            .getRestTemplate()
            .exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<AddressQueryResponseDTO>() {});

    addressEndpointUrl = builder.build().encode().toUri().toString();

    log.info(
        "Using the following endpoint to check CCSVC returns expected values: "
            + addressEndpointUrl);

    log.with(addressQueryResponse).info("The address query response here");

    AddressQueryResponseDTO addressQueryBody = addressQueryResponse.getBody();

    assert addressQueryBody != null;
    List<AddressDTO> addressesFound = addressQueryBody.getAddresses();

    AddressDTO addressFound = null;
    for (int i = 0; i < addressesFound.size(); i++) {
      addressFound = addressesFound.get(i);
      log.with(addressFound).info("This is the address that was found in AIMS where i is: " + i);
      if (addressFound.getFormattedAddress().equals("1 West Grove Road, Exeter, EX2 4LU")) {
        log.with(addressFound).info("This is the address that was found in AIMS");
        break;
      }
    }
    assertNotNull(addressFound);
    assertEquals(
        "The address query response does not contain the correct address",
        "1 West Grove Road, Exeter, EX2 4LU",
        addressFound.getFormattedAddress());

    RegionEnum regionCode = addressFound.getRegion();
    log.with(regionCode).info("This is the region code that was found in AIMS");
    assertNotNull(regionCode);

    AddressTypeEnum addressType = addressFound.getAddressType();
    log.with(addressType).info("This is the address type that was found in AIMS");
    assertNotNull(addressType);

    EstabType estabType = addressFound.getEstabType();
    log.with(estabType).info("This is the establishment type that was found in AIMS");
    assertNotNull(estabType);
  }
}
