package dev.vlearning.banking.pool;

import dev.vlearning.banking.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The most expensive line in this project is {@code fraudCheck.score(...)} — and
 * it is inside a transaction.
 *
 * <p>An open transaction pins a connection for its whole lifetime, so the pool
 * size is really a cap on <em>concurrent transactions</em>, and transaction
 * duration is what decides your throughput ceiling. Put a 400 ms HTTP call in
 * the middle and each connection now serves at most ~2 requests/second,
 * regardless of how fast your queries are. Callers queue on the pool and then
 * fail with a connection-acquisition timeout — an error that points at the
 * database, which is not where the problem is.
 */
@Service
public class EnrichmentService {

    private final AccountRepository accounts;
    private final FraudCheckClient fraudCheck;
    private final DepositWriter writer;

    EnrichmentService(AccountRepository accounts, FraudCheckClient fraudCheck, DepositWriter writer) {
        this.accounts = accounts;
        this.fraudCheck = fraudCheck;
        this.writer = writer;
    }

    @Transactional
    public void depositWithFraudCheck(long accountId, long amount) {
        long balance = accounts.balanceOf(accountId);
        int score = fraudCheck.score(accountId, amount);
        accounts.setBalance(accountId, balance + (score > FraudCheckClient.REJECT_ABOVE ? 0 : amount));
    }

    /**
     * Checkpoint 8: same work, same guarantees, but the transaction opens after
     * the slow call rather than around it. Nothing about the fraud check needs a
     * database connection — see {@link DepositWriter}.
     */
    public void depositWithFraudCheckOutsideTransaction(long accountId, long amount) {
        throw new UnsupportedOperationException(
                "Checkpoint 8: score first, then hand the result to DepositWriter");
    }
}
