package dev.vlearning.banking.pool;

import dev.vlearning.banking.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the fix in step 8: short, purely database work.
 *
 * <p>It lives in its own bean for a reason you met in step 6 — a
 * {@code @Transactional} method called through {@code this} from a neighbouring
 * method in the same class is not transactional at all.
 */
@Service
public class DepositWriter {

    private final AccountRepository accounts;

    DepositWriter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public void applyDeposit(long accountId, long amount, int fraudScore) {
        accounts.addToBalance(accountId, fraudScore > FraudCheckClient.REJECT_ABOVE ? 0 : amount);
    }
}
