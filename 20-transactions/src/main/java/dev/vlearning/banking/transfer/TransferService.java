package dev.vlearning.banking.transfer;

import dev.vlearning.banking.account.AccountRepository;
import dev.vlearning.banking.support.Interleaving;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money movement, written the way most application code is written: read the
 * current value into memory, compute a new one, write it back.
 *
 * <p>That shape — read-modify-write across two statements — is the single most
 * common concurrency bug in business software. A transaction does not save you
 * from it: at READ COMMITTED both transactions are perfectly legal, and one
 * simply overwrites the other's result. Step 1 proves it; step 2 fixes it.
 */
@Service
public class TransferService {

    private final AccountRepository accounts;
    private final Interleaving interleaving;

    TransferService(AccountRepository accounts, Interleaving interleaving) {
        this.accounts = accounts;
        this.interleaving = interleaving;
    }

    @Transactional
    public void deposit(long accountId, long amount) {
        long balance = accounts.balanceOf(accountId);
        interleaving.afterRead();
        accounts.setBalance(accountId, balance + amount);
    }

    @Transactional
    public void transfer(long fromId, long toId, long amount) {
        long from = accounts.balanceOf(fromId);
        long to = accounts.balanceOf(toId);
        interleaving.afterRead();
        accounts.setBalance(fromId, from - amount);
        accounts.setBalance(toId, to + amount);
    }

    /**
     * Checkpoint 2a: the same deposit, expressed so that the database — not your
     * heap — computes the new value.
     */
    @Transactional
    public void depositAtomically(long accountId, long amount) {
        throw new UnsupportedOperationException(
                "Checkpoint 2a: implement AccountRepository.addToBalance and call it from here");
    }

    /**
     * Checkpoint 2b: the same deposit, but claim the row before deciding anything.
     */
    @Transactional
    public void depositWithRowLock(long accountId, long amount) {
        throw new UnsupportedOperationException(
                "Checkpoint 2b: implement AccountRepository.balanceForUpdate and use it here");
    }
}
