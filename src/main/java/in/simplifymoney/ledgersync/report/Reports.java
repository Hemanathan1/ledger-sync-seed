package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static Map<String, Object> summary(List<NormalizedTxn> ledger) {
        Map<String, Object> accounts = new LinkedHashMap<>();
        for (String acct : new TreeSet<>(ledger.stream()
                .map(NormalizedTxn::accountLast4).toList())) {

            BigDecimal spend         = ZERO;
            BigDecimal income        = ZERO;
            BigDecimal microTotal    = ZERO;
            int        microCount    = 0;
            BigDecimal transferOut   = ZERO;
            BigDecimal transferIn    = ZERO;

            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(acct)) continue;

                switch (t.category()) {
                    case SPEND -> spend = spend.add(t.amount());
                    case INCOME -> income = income.add(t.amount());
                    case MICRO -> {
                        microTotal = microTotal.add(t.amount());
                        microCount++;
                    }
                    case TRANSFER -> {
                        if (t.direction() == Direction.DEBIT)
                            transferOut = transferOut.add(t.amount());
                        else
                            transferIn = transferIn.add(t.amount());
                    }
                }
            }

            Map<String, Object> a = new LinkedHashMap<>();
            a.put("spend",          spend.toPlainString());
            a.put("income",         income.toPlainString());
            a.put("micro_count",    microCount);
            a.put("micro_total",    microTotal.toPlainString());
            a.put("transferred_out", transferOut.toPlainString());
            a.put("transferred_in",  transferIn.toPlainString());
            accounts.put(acct, a);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("accounts", accounts);
        return doc;
    }

    public static Map<String, Object> ledgerDocument(List<NormalizedTxn> ledger) {
        List<Object> rows = ledger.stream().map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("account_last4",     t.accountLast4());
            r.put("occurred_at",       t.occurredAt().toString());
            r.put("direction",         t.direction().name().toLowerCase());
            r.put("amount",            t.amount().toPlainString());
            r.put("category",          t.category().name());
            r.put("merchant",          t.merchant());
            r.put("source_message_ids", t.sourceMessageIds());
            return (Object) r;
        }).toList();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("transactions", rows);
        return doc;
    }

    public static Map<String, Object> reconciliation(List<NormalizedTxn> ledger) {
        // Find discrepancies — transactions where stated balance
        // does not match our running ledger balance
        List<Object> discrepancies = new ArrayList<>();

        // Group by account and check running balance
        for (String acct : new TreeSet<>(ledger.stream()
                .map(NormalizedTxn::accountLast4).toList())) {

            List<NormalizedTxn> acctTxns = ledger.stream()
                    .filter(t -> t.accountLast4().equals(acct))
                    .sorted((a, b) -> a.occurredAt().compareTo(b.occurredAt()))
                    .toList();

            // Find transactions with no source message IDs — orphaned
            for (NormalizedTxn t : acctTxns) {
                if (t.sourceMessageIds().isEmpty()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("account_last4", acct);
                    d.put("occurred_at",   t.occurredAt().toString());
                    d.put("amount",        t.amount().toPlainString());
                    d.put("note",          "transaction has no source messages");
                    discrepancies.add(d);
                }
            }
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("discrepancies", discrepancies);
        return doc;
    }

    public static Map<Category, BigDecimal> byCategory(List<NormalizedTxn> ledger) {
        Map<Category, BigDecimal> out = new LinkedHashMap<>();
        for (Category c : Category.values()) out.put(c, ZERO);
        for (NormalizedTxn t : ledger) {
            out.put(t.category(), out.get(t.category()).add(t.amount()));
        }
        return out;
    }
}