Feature: Free shipping

  Shipping is a €4.95 flat rate. We waive it when the order is big enough to absorb the
  cost, and always for gold members — that was the whole conversation, and it took four
  minutes. This file is the worked example: it shows the shape of a specification that
  came out of an agreement, and it shows the wiring (feature → step definitions →
  domain API) that you will reuse for the pricing rules.

  Rule: Gold members never pay for shipping

    Example: A gold member buying one cheap course
      Given the customer is a gold member
      When the order charges €12.00
      Then shipping is free
      And the shipping reason is "GOLD_MEMBER"

    Example: A gold member spending nothing but points
      Given the customer is a gold member
      When the order charges €0.00
      Then shipping is free

  Rule: Orders charging €50 or more ship free for everyone

    Scenario Outline: The threshold sits exactly on €50.00
      Given the customer is a <tier>
      When the order charges €<charged>
      Then shipping costs €<shipping>

      Examples:
        | tier   | charged | shipping |
        | guest  | 49.99   | 4.95     |
        | guest  | 50.00   | 0.00     |
        | member | 50.01   | 0.00     |
        | member | 12.00   | 4.95     |

    Example: The threshold applies to cash charged, not to the ticket price
      Given the customer is a member
      When the order charges €8.00
      Then shipping costs €4.95
      And the shipping reason is "STANDARD_RATE"
