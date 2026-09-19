**What broke:** The amount regex in Amounts.java matched only values with decimal places, so "Rs.5" was skipped and the next rupee figure — the available balance "Rs.92,213.10" — was parsed as the transaction amount instead.

**How we found it:** Reproduced by running the corpus through the parser and tracing the water can transaction (m-00022); added debug logging to confirm Amounts.first() was returning the balance, not the debit amount.

**Who was affected:** Any transaction where the bank omits decimal places on the amount — whole-rupee debits like Rs.5, Rs.35, Rs.99 — across both HDFC and ICICI SMS formats throughout corpus-a.

**The fix:** Updated the AMOUNT regex pattern in Amounts.java from `[0-9,]+\.[0-9]{2}` to `[0-9,]+(?:\.[0-9]{1,2})?` to match amounts with or without decimals, and updated toDecimal() to append ".00" when no decimal point is present.

**Why it cannot recur:** The fix is covered by a unit test that asserts Amounts.first("Rs.5 debited...Avl Bal: Rs.92,213.10") returns 5.00, not 92213.10. The existing test suite passed because it only tested well-formed amounts with decimal places.
