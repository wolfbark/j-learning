package dev.vlearning.banking.audit;

import dev.vlearning.banking.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutService {

    private final AccountRepository accounts;

    PayoutService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Checkpoint 7b: this must be undoable on its own, without abandoning the
     * batch that contains it.
     */
    @Transactional
    public void payout(long fromId, long toId, long amount) {
        accounts.setBalance(fromId, accounts.balanceOf(fromId) - amount);
        if (!accounts.exists(toId)) {
            throw new IllegalArgumentException("no such recipient: " + toId);
        }
        accounts.setBalance(toId, accounts.balanceOf(toId) + amount);
    }
}
