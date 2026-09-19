package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IciciSmsParser implements MessageParser {

    public static final String SENDER = "VM-ICICIB-T";

    // Format 1: "Dear Customer, Acct XX9075 is debited with INR 22.50 on 01/07/2026 10:22. Info: UPI/VEGETABLE VENDOR."
    private static final Pattern V1 = Pattern.compile(
            "Acct XX(?<acct>\\d{4}) is (?<dir>debited|credited) with .*? "
                    + "on (?<when>\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2})\\. "
                    + "Info: (?<merchant>[^.]+)\\.");

    // Format 2: "ICICI Bank Acct XX9075 Dr INR 5 on 23-Jul-2026 18:41; UPI/BARBER ref no 154245459403."
    private static final Pattern V2 = Pattern.compile(
            "Acct XX(?<acct>\\d{4}) (?<dir>Dr|Cr) .*? "
                    + "on (?<when>\\d{2}-\\w{3}-\\d{4} \\d{2}:\\d{2}); "
                    + "(?<merchant>[^;]+?)\\s+ref no");

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        // Try Format 1
        Matcher v1 = V1.matcher(m.body());
        if (v1.find()) {
            BigDecimal amount = Amounts.first(m.body());
            OffsetDateTime at = Dates.ist(v1.group("when"));
            if (amount == null || at == null) return Optional.empty();
            Direction d = "debited".equals(v1.group("dir"))
                    ? Direction.DEBIT : Direction.CREDIT;
            return Optional.of(new ParsedTxn(v1.group("acct"), at, d, amount,
                    v1.group("merchant").trim(),
                    Amounts.statedBalance(m.body()),
                    m.messageId()));
        }

        // Try Format 2
        Matcher v2 = V2.matcher(m.body());
        if (v2.find()) {
            BigDecimal amount = Amounts.first(m.body());
            OffsetDateTime at = Dates.ist(v2.group("when"));
            if (amount == null || at == null) return Optional.empty();
            Direction d = "Dr".equals(v2.group("dir"))
                    ? Direction.DEBIT : Direction.CREDIT;
            return Optional.of(new ParsedTxn(v2.group("acct"), at, d, amount,
                    v2.group("merchant").trim(),
                    Amounts.statedBalance(m.body()),
                    m.messageId()));
        }

        return Optional.empty();
    }
}