package dev.vlearning.banking.report;

import dev.vlearning.banking.account.Account.Kind;
import dev.vlearning.banking.account.AccountRepository;
import dev.vlearning.banking.support.Interleaving;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A two-query read. Every reporting endpoint you have ever written looks like
 * this: several SELECTs, one answer.
 *
 * <p>Under READ COMMITTED each statement gets a <em>fresh</em> snapshot, so a
 * transfer that commits between the two queries is half-visible: the money has
 * left checking and not yet arrived in savings. The report is internally
 * inconsistent even though every individual row it read was committed. That is
 * <em>read skew</em>, and {@code readOnly = true} does nothing about it.
 */
@Service
public class ReportingService {

    private final AccountRepository accounts;
    private final Interleaving interleaving;

    ReportingService(AccountRepository accounts, Interleaving interleaving) {
        this.accounts = accounts;
        this.interleaving = interleaving;
    }

    @Transactional(readOnly = true)
    public Statement statementFor(String customer) {
        long checking = accounts.balanceOf(customer, Kind.CHECKING);
        interleaving.afterRead();
        long savings = accounts.balanceOf(customer, Kind.SAVINGS);
        return new Statement(customer, checking, savings);
    }

    public record Statement(String customer, long checking, long savings) {

        public long total() {
            return checking + savings;
        }
    }
}
