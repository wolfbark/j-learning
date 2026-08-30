package dev.vlearning.apisecurity.expense;

import java.util.List;

import dev.vlearning.apisecurity.audit.AuditLogger;
import dev.vlearning.apisecurity.expense.ExpenseRequests.CreateExpense;
import dev.vlearning.apisecurity.expense.ExpenseRequests.ReceiptFromUrl;
import dev.vlearning.apisecurity.expense.ExpenseRequests.UpdateExpense;
import dev.vlearning.apisecurity.receipt.FetchedReceipt;
import dev.vlearning.apisecurity.receipt.ReceiptFetcher;
import dev.vlearning.apisecurity.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseRepository repository;
    private final ReceiptFetcher receiptFetcher;
    private final AuditLogger audit;

    public ExpenseController(ExpenseRepository repository, ReceiptFetcher receiptFetcher, AuditLogger audit) {
        this.repository = repository;
        this.receiptFetcher = receiptFetcher;
        this.audit = audit;
    }

    @GetMapping
    public List<ExpenseReport> list(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) String userId,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String sort) {
        audit.record(CurrentUser.username(jwt), "list", userId, "ALLOW", null, null);
        return repository.search(userId, q, sort);
    }

    @GetMapping("/{id}")
    public ExpenseReport get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        ExpenseReport report = repository.findById(id).orElseThrow(() -> notFound(id));
        audit.record(CurrentUser.username(jwt), "read", id, "ALLOW", null, null);
        return report;
    }

    @PostMapping
    public ExpenseReport create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateExpense request) {
        audit.record(CurrentUser.username(jwt), "create", null, "ALLOW", request, null);
        long id = repository.insert(request.ownerUsername(), request.team(), request.merchant(),
                request.amountCents(), request.currency(), request.category(), last4(request.cardNumber()));
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PutMapping("/{id}")
    public ExpenseReport update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                @RequestBody UpdateExpense request) {
        ExpenseReport existing = repository.findById(id).orElseThrow(() -> notFound(id));
        if (ExpenseReport.APPROVED.equals(existing.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "an approved report is frozen");
        }
        repository.update(id, request.merchant(), request.amountCents(), request.category());
        audit.record(CurrentUser.username(jwt), "update", id, "ALLOW", request, null);
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping("/{id}/submit")
    public ExpenseReport submit(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        repository.findById(id).orElseThrow(() -> notFound(id));
        repository.updateStatus(id, ExpenseReport.SUBMITTED);
        audit.record(CurrentUser.username(jwt), "submit", id, "ALLOW", null, null);
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping("/{id}/approve")
    public ExpenseReport approve(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                 @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ExpenseReport report = repository.findById(id).orElseThrow(() -> notFound(id));
        if (!ExpenseReport.SUBMITTED.equals(report.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "only a submitted report can be approved");
        }
        repository.updateStatus(id, ExpenseReport.APPROVED);
        audit.record(CurrentUser.username(jwt), "approve", id, "ALLOW", null, authorization);
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @PostMapping("/{id}/receipt-from-url")
    public ExpenseReport receiptFromUrl(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                        @RequestBody ReceiptFromUrl request) {
        repository.findById(id).orElseThrow(() -> notFound(id));
        FetchedReceipt receipt = receiptFetcher.fetch(request.url());
        repository.attachReceipt(id, request.url(), receipt.sizeBytes());
        audit.record(CurrentUser.username(jwt), "attach-receipt", id, "ALLOW", request, null);
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "no expense report " + id);
    }

    private static String last4(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return null;
        }
        return cardNumber.substring(cardNumber.length() - 4);
    }
}
