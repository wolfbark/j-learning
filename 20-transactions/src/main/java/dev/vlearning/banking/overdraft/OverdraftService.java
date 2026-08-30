package dev.vlearning.banking.overdraft;

import dev.vlearning.banking.account.AccountRepository;
import dev.vlearning.banking.support.Interleaving;
import dev.vlearning.banking.transfer.InsufficientFundsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Linked overdraft protection: an individual account may go negative, as long as
 * the customer's accounts <em>together</em> stay at or above zero.
 *
 * <p>Note the shape — <b>read a set of rows, check a rule, write a different
 * row</b>. Two concurrent withdrawals never touch the same row, so there is no
 * write-write conflict for the database to serialise, and both see a snapshot
 * in which the rule still holds. Each is individually correct; together they
 * break the invariant. This is <em>write skew</em>, and REPEATABLE READ — which
 * on Postgres means snapshot isolation — does not prevent it.
 */
@Service
public class OverdraftService {

    private final AccountRepository accounts;
    private final Interleaving interleaving;

    OverdraftService(AccountRepository accounts, Interleaving interleaving) {
        this.accounts = accounts;
        this.interleaving = interleaving;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void withdraw(String customer, long accountId, long amount) {
        long combined = accounts.combinedBalance(customer);
        if (combined - amount < 0) {
            throw new InsufficientFundsException(customer, combined, amount);
        }
        interleaving.afterRead();
        accounts.setBalance(accountId, accounts.balanceOf(accountId) - amount);
    }
}
