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
      | caseType | region | individual |
      | "HH"     | "E"    | "true"     |
      | "HH"     | "N"    | "true"     |
      | "HH"     | "W"    | "true"     |
      | "CE"     | "E"    | "true"     |
      | "CE"     | "N"    | "true"     |
      | "CE"     | "W"    | "true"     |
      | "SPG"    | "E"    | "true"     |
      | "SPG"    | "N"    | "true"     |
      | "SPG"    | "W"    | "true"     |
      | "HH"     | "E"    | "false"    |
      | "HH"     | "N"    | "false"    |
      | "HH"     | "W"    | "false"    |
      | "CE"     | "E"    | "false"    |
      | "CE"     | "N"    | "false"    |
      | "CE"     | "W"    | "false"    |
      | "SPG"    | "E"    | "false"    |
      | "SPG"    | "N"    | "false"    |
      | "SPG"    | "W"    | "false"    |

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
      | address               | uprn           | case_ids                               |
      | "70, Magdalen Street" | "100040222798" | "3305e937-6fb1-4ce1-9d4c-077f147789de" |
      | "33 Serge Court"      | "100041131297" | "03f58cb5-9af4-4d40-9d60-c124c5bddfff" |

  @SetUp
  Scenario: [CR-T142] I want to request an UAC for a HH Respondent in NI via POST
    Given the CC advisor has provided a valid UPRN "1347459995"
    Then the Case endpoint returns a case, associated with UPRN "1347459995", which has caseType "HH"
    Given a list of available fulfilment product codes is presented for a HH caseType where individual flag = "false" and region = "N"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "eng", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459995" addressType = "HH" individual = "false" and region = "N"

  #Scenario CR-T292 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 27/03/20
  @SetUp
  Scenario: [CR-T292] PENDING I want to request an UAC for a HI Respondent in Wales in welsh via Post
    Given the CC advisor has provided a valid UPRN "1347459992"
    When the Case endpoint returns a case, associated with UPRN "1347459992", which has caseType "HH"
    Given a list of available fulfilment product codes is presented for a HH caseType where individual flag = "true" and region = "W"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459992" addressType = "HH" individual = "true" and region = "W"

  @SetUp
  Scenario: [CR-T301] I want to request a Welsh Paper Questionnaire for a CE Individual Respondent in Wales
    Given the CC advisor has provided a valid UPRN "1347459993"
    Then the Case endpoint returns a case, associated with UPRN "1347459993", which has caseType "CE"
    Given a list of available fulfilment product codes is presented for a caseType = "CE" where individual flag = "true" and region = "W"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "QUESTIONNAIRE",  language "wel", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459993" addressType = "CE" individual = "true" and region = "W"

  #Scenario CR-T302 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 27/03/20
  @SetUp
  Scenario: [CR-T302] PENDING I want to request an UAC for a CE Individual Respondent in Wales in welsh via Post
    Given the CC advisor has provided a valid UPRN "1347459993"
    Then the Case endpoint returns a case, associated with UPRN "1347459993", which has caseType "CE"
    Given a list of available fulfilment product codes is presented for a caseType = "CE" where individual flag = "true" and region = "W"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459993" addressType = "CE" individual = "true" and region = "W"

  #Scenario CR-T304 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "QUESTIONNAIRE",  language "wel", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 27/03/20
  @SetUp
  Scenario: [CR-T304] PENDING I want to request a welsh Paper Questionnaire for a CE Manager in Wales
    Given the CC advisor has provided a valid UPRN "1347459993"
    Then the Case endpoint returns a case, associated with UPRN "1347459993", which has caseType "CE"
    Given a list of available fulfilment product codes is presented for a caseType = "CE" where individual flag = "false" and region = "W"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "QUESTIONNAIRE",  language "wel", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459993" addressType = "CE" individual = "false" and region = "W"

  #Scenario CR-T313 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "UAC",  language "eng", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 30/03/20
  @SetUp
  Scenario: [CR-T313] PENDING I want to request an UAC for a CE Individual Respondent in NI via Post
    Given the CC advisor has provided a valid UPRN "1347459993"
    Then the Case endpoint returns a case, associated with UPRN "1347459993", which has caseType "CE"
    Given a list of available fulfilment product codes is presented for a caseType = "CE" where individual flag = "true" and region = "N"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "eng", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459993" addressType = "CE" individual = "true" and region = "N"

  #Scenario CR-T316 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "UAC",  language "eng", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 30/03/20
  @SetUp
  Scenario: [CR-T316] PENDING I want to request an UAC for a CE Manager in NI via Post
    Given the CC advisor has provided a valid UPRN "1347459993"
    Then the Case endpoint returns a case, associated with UPRN "1347459993", which has caseType "CE"
    Given a list of available fulfilment product codes is presented for a caseType = "CE" where individual flag = "false" and region = "N"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "eng", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459993" addressType = "CE" individual = "false" and region = "N"

  @SetUp
  Scenario: [CR-T323] I want to request a Paper Questionnaire for a SPG Individual Respondent in NI
    Given the CC advisor has provided a valid UPRN "1347459994"
    Then the Case endpoint returns a case, associated with UPRN "1347459994", which has caseType "SPG" and addressLevel "U" and handDelivery "false"
    Given a list of available fulfilment product codes is presented for a caseType = "SPG" where individual flag = "true" and region = "N"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "QUESTIONNAIRE",  language "eng", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459994" addressType = "SPG" individual = "true" and region = "N"

  #Scenario CR-T334 throws a pending exception in the step 'When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"'
  #because the product reference library does not currently contain the required product - Ella Cook, 30/03/2
  @SetUp
  Scenario: [CR-T334] PENDING I want to request an UAC for a SPG Respondent in Wales in welsh language via Post
    Given the CC advisor has provided a valid UPRN "1347459994"
    Then the Case endpoint returns a case, associated with UPRN "1347459994", which has caseType "SPG"
    Given a list of available fulfilment product codes is presented for a caseType = "SPG" where individual flag = "false" and region = "W"
    And an empty queue exists for sending Fulfilment Requested events
    When CC Advisor selects the product code for productGroup "UAC",  language "wel", deliveryChannel "POST"
    Then a fulfilment request event is emitted to RM for UPRN = "1347459994" addressType = "SPG" individual = "false" and region = "W"

  Scenario Outline: [CR-T269, CR-T273, CR-T293, CR-T306, CR-T319, CR-T322] PENDING I want to verify that Fulfilments
                    are provided from a CC addvisor UPRN
    Given I have a valid UPRN provided by a CC advisor <uprn>
    When I Search cases By UPRN
    Then I have a valid case from my search UPRN <case_id>
    When I Search fulfilments <delivery_channel> <individual>
    Then a fulfilment request event is emitted to RM for UPRN = <uprn> addressType = <case_type> individual = <individual> and region = <region>
    And the correct fulfilments are returned for my case <case_type> <region> <delivery_channel> <individual>

    Examples:
      | uprn           | case_id                                  | case_type | region | delivery_channel | individual |
      |"100140222798"  | "3305e937-6fb2-4ce1-9d4c-077f147789de"   | "HH"      | "E"    | "SMS"            | "false"    |
      |"100240222798"  | "3305e937-6fb3-4ce1-9d4c-077f147789de"   | "CE"      | "E"    | "SMS"            | "true"     |
      |"100340222798"  | "3305e937-6fb4-4ce1-9d4c-077f147789de"   | "HH"      | "W"    | "SMS"            | "true"     |
      |"100440222798"  | "3305e937-6fb5-4ce1-9d4c-077f147789de"   | "CE"      | "W"    | "SMS"            | "false"    |
      |"100540222798"  | "3305e937-6fb6-4ce1-9d4c-077f147789de"   | "SPG"     | "N"    | "SMS"            | "false"    |
      |"100640222798"  | "3305e937-6fb7-4ce1-9d4c-077f147789de"   | "SPG"     | "N"    | "SMS"            | "true"     |
