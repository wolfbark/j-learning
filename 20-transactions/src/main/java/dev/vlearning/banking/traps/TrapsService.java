package dev.vlearning.banking.traps;

import dev.vlearning.banking.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Three ways a {@code @Transactional} annotation quietly does nothing like what
 * you assumed. All three are proxy mechanics, not database behaviour, and all
 * three fail <em>silently</em> — the code looks transactional in review.
 */
@Service
public class TrapsService {

    private final AccountRepository accounts;
    private final InnerService inner;

    TrapsService(AccountRepository accounts, InnerService inner) {
        this.accounts = accounts;
        this.inner = inner;
    }

    /**
     * Trap 1 — self-invocation. The annotation is on {@link #depositThenFail},
     * but this calls it through {@code this}, not through the proxy, so no
     * transaction is ever started and the deposit is not rolled back.
     */
    public void selfInvoked(long accountId, long amount) {
        depositThenFail(accountId, amount);
    }

    @Transactional
    public void depositThenFail(long accountId, long amount) {
        accounts.setBalance(accountId, accounts.balanceOf(accountId) + amount);
        throw new IllegalStateException("business rule violated after the write");
    }

    /**
     * Trap 2 — the default rollback rule is "unchecked exceptions only". A
     * checked exception propagates out of a {@code @Transactional} method and
     * the transaction <em>commits</em> on the way out.
     */
    @Transactional
    public void depositThenFailChecked(long accountId, long amount) throws PaperworkMissingException {
        accounts.setBalance(accountId, accounts.balanceOf(accountId) + amount);
        throw new PaperworkMissingException("signature required");
    }

    /**
     * Trap 3 — rollback-only poisoning. The inner call joins this transaction
     * (propagation REQUIRED, the default), fails, and marks the shared
     * transaction rollback-only. Catching the exception looks like recovery, but
     * the transaction is already doomed: the commit throws
     * {@code UnexpectedRollbackException} from a line that never calls the
     * database.
     */
    @Transactional
    public void depositAndSwallowInnerFailure(long accountId, long amount) {
        accounts.setBalance(accountId, accounts.balanceOf(accountId) + amount);
        try {
            inner.alwaysFails();
        } catch (RuntimeException swallowed) {
            // "handled" — a log line and carry on. See the class javadoc.
        }
    }
    /**
     * Checkpoint 6a: same deposit, same failure, but actually rolled back. The
     * method body may not change — the call has to reach the proxy.
     */
    public void selfInvokedButTransactional(long accountId, long amount) {
        throw new UnsupportedOperationException(
                "Checkpoint 6a: route the call through the proxy instead of `this`");
    }

    /**
     * Checkpoint 6b: a checked exception that does roll the transaction back.
     */
    public void depositThenFailCheckedWithRollback(long accountId, long amount)
            throws PaperworkMissingException {
        throw new UnsupportedOperationException(
                "Checkpoint 6b: same body as depositThenFailChecked, but tell the proxy "
                        + "that this exception counts");
    }

    /**
     * Checkpoint 6c: an inner step that is allowed to fail without condemning
     * the caller's transaction.
     */
    public void depositAndSurviveInnerFailure(long accountId, long amount) {
        throw new UnsupportedOperationException(
                "Checkpoint 6c: give the inner call a transaction of its own so its failure "
                        + "is its own; see InnerService.failsInItsOwnTransaction");
    }
}
