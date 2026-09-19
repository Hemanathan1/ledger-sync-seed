package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.RawMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HdfcSmsParser implements MessageParser {

    public static final String SENDER = "AD-HDFCBK-S";

    private static final Pattern V1 = Pattern.compile(
            "(?<dir>debited from|credited to) a/c \\*\\*(?<acct>\\d{4}) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} at \\d{2}:\\d{2}) "
                    + "(?:to|by) (?<merchant>[^.]+)\\.");

    private static final Pattern V2 = Pattern.compile(
        "(?<dir>Sent|Received)[\\s]+(?:INR|Rs\\.?)\\s*[0-9,]+(?:\\.[0-9]{1,2})?\\s*\\n"
                + "(?:To|From):\\s*(?<merchant>.+?)\\n"
                + "On:\\s*(?<when>\\d{2} \\w{3} \\d{2} \\d{2}:\\d{2})\\n"
                + "A/c:\\s*XX(?<acct>\\d{4})",
        Pattern.DOTALL);

    private static final Pattern CARD = Pattern.compile(
            "spent on HDFC Bank Card x(?<acct>\\d{4}) at (?<merchant>.+?) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\.");

    private static final Pattern EMANDATE = Pattern.compile(
            "(?:Rs\\.?|INR)\\s*[0-9,]+(?:\\.[0-9]{1,2})? will be deducted "
                    + "from your HDFC Bank A/c XX(?<acct>\\d{4}) "
                    + "on (?<when>\\d{2}-\\d{2}-\\d{2} at \\d{2}:\\d{2}) "
                    + "for (?<merchant>.+)");

    @Override
    public boolean supports(RawMessage m) {
        return "sms".equals(m.channel()) && SENDER.equals(m.sender());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        String body = m.body();

        // Skip non-transaction messages
        if (body.contains("is your OTP")
                || body.contains("Avl Bal in a/c")
                || body.contains("MobileBanking")
                || body.contains("pre-approved")) {
            return Optional.empty();
        }

        // V1
        Matcher v1 = V1.matcher(body);
        if (v1.find()) {
            Direction d = v1.group("dir").startsWith("debited")
                    ? Direction.DEBIT : Direction.CREDIT;
            return build(m, v1.group("acct"),
                    v1.group("when").replace(" at ", " "),
                    d, v1.group("merchant"));
        }

        // V2
                // V2
        Matcher v2 = V2.matcher(body);
        
        if (v2.find()) {
            Direction d = "Sent".equals(v2.group("dir"))
                    ? Direction.DEBIT : Direction.CREDIT;
            return build(m, v2.group("acct"),
                    v2.group("when"), d, v2.group("merchant"));
        }

        // CARD
        Matcher card = CARD.matcher(body);
        if (card.find()) {
            return build(m, card.group("acct"),
                    card.group("when").replace("-", " "),
                    Direction.DEBIT, card.group("merchant"));
        }

        // E-MANDATE
        Matcher em = EMANDATE.matcher(body);
        if (em.find()) {
            return build(m, em.group("acct"),
                    em.group("when").replace(" at ", " "),
                    Direction.DEBIT, em.group("merchant").trim());
        }

        return Optional.empty();
    }

    private Optional<ParsedTxn> build(RawMessage m, String acct, String when,
                                       Direction dir, String merchant) {
        BigDecimal amount = Amounts.first(m.body());
        OffsetDateTime at = Dates.ist(when);
        if (amount == null || at == null) return Optional.empty();
        return Optional.of(new ParsedTxn(acct, at, dir, amount,
                merchant.trim(),
                Amounts.statedBalance(m.body()),
                m.messageId()));
    }
}