#Author: andrew.johnys@ext.ons.gov.uk
#Keywords Summary : CC CONTACT CENTRE SERVICE
#Feature: Test Contact centre Fulfilments Endpoints
#Scenario: Get fulfilments for various cases
## (Comments)
Feature: Test Contact centre Fulfilments Endpoints
  I want to verify that all endpoints in CC-SERVICE fulfilments work correctly

  Scenario Outline: I want to verify that the get Fulfilments endpoint works
    When I Search fulfilments <caseType> <region> <individual>
    Then A list of fulfilments is returned of the correct products <caseType> <region> <individual>

    Examples:
      | caseType  | region        | individual |
      | "HH"      | "E"           | "true"     |
      | "HH"      | "N"           | "true"     |
      | "HH"      | "W"           | "true"     |
      | "CE"      | "E"           | "true"     |
      | "CE"      | "N"           | "true"     |
      | "CE"      | "W"           | "true"     |
      | "SPG"     | "E"           | "true"     |
      | "SPG"     | "N"           | "true"     |
      | "SPG"     | "W"           | "true"     |
      | "HH"      | "E"           | "false"     |
      | "HH"      | "N"           | "false"     |
      | "HH"      | "W"           | "false"     |
      | "CE"      | "E"           | "false"     |
      | "CE"      | "N"           | "false"     |
      | "CE"      | "W"           | "false"     |
      | "SPG"      | "E"          | "false"     |
      | "SPG"      | "N"          | "false"     |
      | "SPG"      | "W"          | "false"     |

  Scenario Outline: I want to verify that Fulfilments work end to end
    Given I have a valid address search String <address>
    When I Search Addresses By Address Search String
    Then A list of addresses for my search is returned containing the address I require
    Given I have a valid UPRN from my found address <uprn>
    When I Search cases By UPRN
    Then the correct cases for my UPRN are returned <case_ids>
    Given I have a valid case from my search UPRN
    When I Search fulfilments
    Then the correct fulfilments are returned for my case


    Examples:
      | address                             | uprn           | case_ids                                 |
      | "70, Magdalen Street"               |"100040222798"  | "3305e937-6fb1-4ce1-9d4c-077f147789de"   |
      | "33 Serge Court"                    |"100041131297"  | "03f58cb5-9af4-4d40-9d60-c124c5bddfff"   |

  Scenario Outline: [CR-T269, CR-T273, CR-T293, CR-T306, CR-T319, CR-T322] I want to verify that Fulfilments
                    are provided from a CC addvisor UPRN
    Given I have a valid UPRN provided by a CC advisor <uprn>
    When I Search cases By UPRN
    Then I have a valid case from my search UPRN <case_id>
    When I Search fulfilments <delivery_channel> <individual>
    Then the correct fulfilments are returned for my case <case_type> <region> <delivery_channel> <individual>

    Examples:
      | uprn           | case_id                                  | case_type | region | delivery_channel | individual |
      |"100140222798"  | "3305e937-6fb2-4ce1-9d4c-077f147789de"   | "HH"      | "E"    | "SMS"            | "false"    |
      |"100240222798"  | "3305e937-6fb3-4ce1-9d4c-077f147789de"   | "CE"      | "E"    | "SMS"            | "true"     |
      |"100340222798"  | "3305e937-6fb4-4ce1-9d4c-077f147789de"   | "HI"      | "W"    | "SMS"            | "true"     |
      |"100440222798"  | "3305e937-6fb5-4ce1-9d4c-077f147789de"   | "CE"      | "W"    | "SMS"            | "false"    |
      |"100540222798"  | "3305e937-6fb6-4ce1-9d4c-077f147789de"   | "SPG"     | "N"    | "SMS"            | "false"    |
      |"100640222798"  | "3305e937-6fb7-4ce1-9d4c-077f147789de"   | "SPG"     | "N"    | "SMS"            | "true"     |
