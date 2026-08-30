package dev.vlearning.ledger.application;

import org.springframework.stereotype.Service;

import dev.vlearning.ledger.domain.AccountCommand;
import dev.vlearning.ledger.domain.AccountDecider;
import dev.vlearning.ledger.domain.AccountState;
import dev.vlearning.ledger.domain.AccountState.Status;

/**
 * The command handler — and the entire write path, one shape for every command:
 *
 * <pre>  load → decide → append</pre>
 *
 * Load state (a fold of history), let the pure decider produce new events or reject, append
 * them at the version the decision was based on. No UPDATE statement exists anywhere in
 * this system. If the append hits a {@code ConcurrencyException}, somebody else's events
 * landed between our load and our append — the decision was made on stale state and must
 * not be persisted blindly (their withdrawal may have emptied the balance we checked).
 */
@Service
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public void handle(AccountCommand command) {
        var loaded = repository.load(command.accountId());
        var newEvents = AccountDecider.decide(command, loaded.state());
        repository.append(command.accountId(), loaded.version(), newEvents);
    }

    /** Query by rehydration: fold the stream, return what it says. */
    public AccountView get(String accountId) {
        var loaded = repository.load(accountId);
        if (loaded.state().status() == Status.NOT_OPENED) {
            throw new AccountNotFoundException(accountId);
        }
        return AccountView.of(loaded.state(), loaded.version());
    }

    public record AccountView(String accountId, String owner, long balanceCents, String status, long version) {

        static AccountView of(AccountState state, long version) {
            return new AccountView(state.accountId(), state.owner(), state.balanceCents(),
                    state.status().name(), version);
        }
    }
}
