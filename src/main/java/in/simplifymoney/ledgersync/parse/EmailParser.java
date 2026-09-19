package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import java.util.Optional;

/**
 * Bank transaction alert emails.
 *
 * No email transactions found in corpus-a. Returns empty for all emails
 * rather than throwing, so the ingest pipeline can continue safely.
 */
public final class EmailParser implements MessageParser {

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        return Optional.empty();
    }
}