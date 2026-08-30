package dev.vlearning.banking.audit;

import java.util.List;

import dev.vlearning.banking.account.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two propagation questions in one class.
 *
 * <p>{@link #transferAudited} needs a child transaction that <em>survives</em>
 * the parent's rollback (REQUIRES_NEW — a genuinely separate transaction on a
 * second connection).
 *
 * <p>{@link #payoutBatch} needs the opposite: one transaction with per-item
 * escape hatches, so a single bad item is undone without discarding the good
 * work before it (NESTED — a savepoint inside the same transaction).
 */
@Service
public class AuditedTransferService {

    private final AccountRepository accounts;
    private final AuditLog audit;
    private final PayoutService payouts;

    AuditedTransferService(AccountRepository accounts, AuditLog audit, PayoutService payouts) {
        this.accounts = accounts;
        this.audit = audit;
        this.payouts = payouts;
    }

    @Transactional
    public void transferAudited(long fromId, long toId, long amount) {
        audit.record("transfer %d -> %d of %d attempted".formatted(fromId, toId, amount));
        long from = accounts.balanceOf(fromId);
        if (from < amount) {
            throw new IllegalStateException("insufficient funds in " + fromId);
        }
        accounts.setBalance(fromId, from - amount);
        accounts.setBalance(toId, accounts.balanceOf(toId) + amount);
    }

    /**
     * Pay each recipient; skip the ones that fail. Every payout that succeeded
     * must still be there afterwards.
     */
    @Transactional
    public int payoutBatch(long fromId, List<Long> recipientIds, long amountEach) {
        int paid = 0;
        for (Long recipient : recipientIds) {
            try {
                payouts.payout(fromId, recipient, amountEach);
                paid++;
            } catch (RuntimeException failed) {
                // one bad recipient must not cost us the whole batch
            }
        }
        return paid;
    }
}
