package dev.vlearning.ledger.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.ledger.application.AccountNotFoundException;
import dev.vlearning.ledger.application.AccountService;
import dev.vlearning.ledger.application.AccountService.AccountView;
import dev.vlearning.ledger.domain.AccountCommand.CloseAccount;
import dev.vlearning.ledger.domain.AccountCommand.Deposit;
import dev.vlearning.ledger.domain.AccountCommand.OpenAccount;
import dev.vlearning.ledger.domain.AccountCommand.Withdraw;
import dev.vlearning.ledger.projection.BalancesReadModel;
import dev.vlearning.ledger.projection.BalancesReadModel.BalanceRow;

/**
 * Given: a thin HTTP skin over commands and two DELIBERATELY different reads:
 * GET /accounts/{id} rehydrates (fold the stream — always current, costs a replay), while
 * GET /accounts/{id}/balance hits the read model (one row — fast, possibly stale, empty
 * until the projector has run). Having both side by side is the CQRS lesson in miniature.
 */
@RestController
public class AccountController {

    private final AccountService accounts;
    private final BalancesReadModel balances;

    public AccountController(AccountService accounts, BalancesReadModel balances) {
        this.accounts = accounts;
        this.balances = balances;
    }

    @PostMapping("/accounts")
    ResponseEntity<Void> open(@RequestBody OpenRequest request) {
        accounts.handle(new OpenAccount(request.accountId(), request.owner()));
        return ResponseEntity.created(URI.create("/accounts/" + request.accountId())).build();
    }

    @PostMapping("/accounts/{accountId}/deposits")
    ResponseEntity<Void> deposit(@PathVariable String accountId, @RequestBody AmountRequest request) {
        accounts.handle(new Deposit(accountId, request.amountCents(), request.description()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    ResponseEntity<Void> withdraw(@PathVariable String accountId, @RequestBody AmountRequest request) {
        accounts.handle(new Withdraw(accountId, request.amountCents(), request.description()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accounts/{accountId}/close")
    ResponseEntity<Void> close(@PathVariable String accountId) {
        accounts.handle(new CloseAccount(accountId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts/{accountId}")
    AccountView get(@PathVariable String accountId) {
        return accounts.get(accountId);
    }

    @GetMapping("/accounts/{accountId}/balance")
    BalanceRow balance(@PathVariable String accountId) {
        return balances.find(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId + " (in the balances read model)"));
    }

    record OpenRequest(String accountId, String owner) {
    }

    record AmountRequest(long amountCents, String description) {
    }
}
