package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    // Known transfer merchant keywords
    private static final List<String> TRANSFER_KEYWORDS = List.of(
        "NEFT INWARD SELF", "IMPS SELF", "UPI SELF", "OWN ACCOUNT", "SWEEP"
);

    // Micro threshold
    private static final BigDecimal MICRO_THRESHOLD = new BigDecimal("100.00");

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);

        // Group messages by dedup key: account + date + amount + direction
        // Multiple messages about the same transaction get merged
        Map<String, List<ParsedTxn>> groups = new HashMap<>();

        int skipped = 0;
        for (RawMessage m : messages) {
            Optional<ParsedTxn> p = parsers.parse(m);
            if (p.isEmpty()) {
                skipped++;
                continue;
            }
            String key = dedupKey(p.get());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p.get());
        }

        // Save one transaction per group
        int parsed = 0;
        for (List<ParsedTxn> group : groups.values()) {
            store.save(toTransaction(group));
            parsed++;
        }

        return new Stats(messages.size(), parsed, skipped);
    }

    // Dedup key: same account + same minute + same amount + same direction
    private String dedupKey(ParsedTxn p) {
    String minute = p.occurredAt().toString().substring(0, 16);
    return p.accountLast4() + "|" + minute + "|"
            + p.amount().toPlainString() + "|" + p.direction().name();
}

    private NormalizedTxn toTransaction(List<ParsedTxn> group) {
        // Use first transaction as base
        ParsedTxn first = group.get(0);

        // Collect all source message IDs
        List<String> sourceIds = group.stream()
                .map(ParsedTxn::sourceMessageId)
                .distinct()
                .toList();

        // Determine category
        Category category = determineCategory(first);

        return new NormalizedTxn(
                first.accountLast4(),
                first.occurredAt(),
                first.direction(),
                first.amount(),
                category,
                first.merchant().trim(),
                sourceIds);
    }

    private Category determineCategory(ParsedTxn p) {
        // Check TRANSFER first
        String merchant = p.merchant().toUpperCase();
        for (String kw : TRANSFER_KEYWORDS) {
            if (merchant.contains(kw)) {
                return Category.TRANSFER;
            }
        }

        if (p.direction() == Direction.DEBIT) {
            // Check MICRO — UPI debit of ₹100 or less
            boolean isUpi = merchant.startsWith("UPI/") || merchant.startsWith("UPI-");
            if (isUpi && p.amount().compareTo(MICRO_THRESHOLD) <= 0) {
                return Category.MICRO;
            }
            return Category.SPEND;
        }

        return Category.INCOME;
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines
                    .filter(s -> !s.isBlank())::iterator) {
                Map<String, Object> o = Json.parseObject(line);
                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")));
            }
        }
        return out;
    }

    public record Stats(int messagesRead, int transactionsWritten,
                        int messagesSkipped) {}
}