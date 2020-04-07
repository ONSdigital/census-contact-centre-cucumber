#Author: andrew.johnys@ext.ons.gov.uk
#Keywords Summary : CC CONTACT CENTRE SERVICE
#Feature: Test Contact centre Case Endpoints
## (Comments)
Feature: Test Contact centre Case Endpoints
  I want to verify that all endpoints in CC-SERVICE work correctly

  Scenario Outline: [CR-T134] I want to verify that the case search by case ID works
    Given I have a valid case ID <caseId>
    When I Search cases By case ID <caseEvents>
    Then the correct case for my case ID is returned <uprn>
    And the correct number of events are returned <caseEvents> <noCaseEvents>
    And the establishment UPRN is <estabUprn>
    And the secure establishment is set to <secure>

    Examples:
      | caseId                                 | uprn       | caseEvents | noCaseEvents | estabUprn      | secure  |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | 1347459999 | "true"     |            2 | "334111111111" | "true"  |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ac" | 1347459999 | "true"     |            1 | ""             | "false" |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | 1347459999 | "false"    |            0 | "334111111111" | "true"  |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ac" | 1347459999 | "false"    |            0 | ""             | "false" |
      | "03f58cb5-9af4-4d40-9d60-c124c5bddf09" | 1347459999 | "true"     |            0 | ""             | "false" |

  Scenario Outline: [CR-T135] I want to verify that the case search by invalid case ID works
    Given I have an invalid case ID <caseId>
    When I Search for cases By case ID
    Then An error is thrown and no case is returned <httpError>

    Examples:
      | caseId                                 | httpError |
      | "40074ef9-2a0c-4a5c-bb69-d3fc5bfa10dc" | "500"     |
      | "40174ef9-2a0c-4a5c-bb69-d3fc5bfa10dc" | "500"     |
      | "40474ef9-2a0c-4a5c-bb69-d3fc5bfa10dc" | "404"     |
      | "50074ef9-2a0c-4a5c-bb69-d3fc5bfa10dc" | "500"     |

  Scenario Outline: [CR-T136] I want to verify that the case search by case UPRN works
    Given I have a valid UPRN <uprn>
    When I Search cases By UPRN
    Then the correct cases for my UPRN are returned <case_ids>

    Examples:
      | uprn         | case_ids                                                                                                         |
      | "1347459999" | "3305e937-6fb1-4ce1-9d4c-077f147789ab,3305e937-6fb1-4ce1-9d4c-077f147789ac,03f58cb5-9af4-4d40-9d60-c124c5bddf09" |

  Scenario Outline: [CR-T137] I want to verify that the case search by invalid case UPRN works
    Given I have an invalid UPRN <uprn>
    When I Search cases By invalid UPRN
    Then no cases for my UPRN are returned <httpError>

    Examples:
      | uprn         | httpError |
      | "1347459998" | "404"     |
      | "abcdefghik" | "400"     |
      | "1111111111" | "404"     |

  Scenario Outline: I want to verify that a valid Refusal is accepted
    Given I have a valid case ID <caseId>
    And I supply the Refusal information
    When I Refuse a case
    Then the call succeeded and responded with the supplied case ID

    Examples:
      | caseId                                 |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ac" |
      | "UnKnown"                              |
      | "UNKNOWN"                              |

  Scenario Outline: I want to verify that a valid reason for Refusal is accepted and event posted
    Given I have a valid case ID <caseId>
    And an empty queue exists for sending Refusal events
    And I supply a <reason> reason for Refusal
    And I supply the Refusal information
    When I Refuse a case
    Then the call succeeded and responded with the supplied case ID
    And a Refusal event is sent with type <type>

    Examples:
      | caseId                                 | reason          | type                    |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | "HARD"          | "HARD_REFUSAL"          |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | "EXTRAORDINARY" | "EXTRAORDINARY_REFUSAL" |

  Scenario Outline: I want to verify that a Refusal without a reason is rejected
    Given I have a valid case ID <caseId>
    And I supply a <reason> reason for Refusal
    And I supply the Refusal information
    When I Refuse a case
    Then An error is thrown and no case is returned <httpError>

    Examples:
      | caseId                                 | reason | httpError |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | ""     | "400"     |

  Scenario Outline: I want to verify that a Refusal without a valid agentId is rejected
    Given I have a valid case ID <caseId>
    And I supply an <agentId> agentId for Refusal
    And I supply the Refusal information
    When I Refuse a case
    Then An error is thrown and no case is returned <httpError>

    Examples:
      | caseId                                 | agentId  | httpError |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | ""       | "400"     |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | "ABC"    | "400"     |
      | "3305e937-6fb1-4ce1-9d4c-077f147789ab" | "123456" | "400"     |

  Scenario Outline: I want to verify that an invalid Case ID for Refusal is rejected
    Given I have an invalid case ID <caseId>
    And I supply the Refusal information
    When I Refuse a case
    Then An error is thrown and no case is returned <httpError>

    Examples:
      | caseId                                 | httpError |
      | "NOTKNOWN"                             | "400"     |
      | "XX474ef9-2a0c-4a5c-bb69-d3fc5bfa10dc" | "400"     |
