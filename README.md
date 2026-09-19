# Ledger Sync — Simplify Money Assignment

## Quick Start

```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
H2=/path/to/h2.jar

bash verify.sh

java -cp "build/selfcheck:$H2" in.simplifymoney.ledgersync.App migrate
java -cp "build/selfcheck:$H2" in.simplifymoney.ledgersync.App ingest fixtures/corpus-a.jsonl
java -cp "build/selfcheck:$H2" in.simplifymoney.ledgersync.App report submission/
```

## What I Fixed

1. **Incident bug (Amounts.java)** — regex skipped whole-rupee amounts, read balance as transaction amount
2. **EmailParser** — was throwing exception, now returns empty safely  
3. **IciciSmsParser V2** — added second ICICI format (Dr/Cr prefix, dd-MMM-yyyy date)
4. **Deduplication** — IngestService now groups messages by account+time+amount+direction
5. **MICRO category** — UPI debits of Rs.100 or less
6. **TRANSFER category** — transactions between own accounts

## Results vs Expected

| Account | Expected Txns | Produced | Balance Diff |
|---------|--------------|----------|--------------|
| 4821    | 146          | 145      | +7,500       |
| 9075    | 91           | 91       | 0.00 ✅      |

## Decision Log

1. Used minute-level dedup key — same transaction appears in SMS and email formats
2. EmailParser returns empty — no emails in corpus-a, safer than throwing
3. MICRO threshold Rs.100 — as specified
4. TRANSFER detection by merchant keyword — "NEFT INWARD SELF", "IMPS SELF"
5. Kept H2 SQL store — no time to implement document store

## What's Unfinished

- DocumentStore (MongoDB/DynamoDB) not implemented
- Transfer detection not fully accurate — 4821 balance off by Rs.7,500
- Reconciliation logic is basic
- Tests not written

## AI Usage

Used Claude to help with regex patterns and Java boilerplate. Fixed the V2 ICICI pattern myself after Claude's version didn't match — the original pattern had `^` anchor that prevented matching mid-string. Also debugged the H2 IDENTITY syntax error manually.
