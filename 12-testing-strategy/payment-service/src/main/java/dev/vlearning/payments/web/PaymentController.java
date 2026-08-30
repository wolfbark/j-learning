package dev.vlearning.payments.web;

import dev.vlearning.payments.domain.Payment;
import dev.vlearning.payments.domain.PaymentNotFoundException;
import dev.vlearning.payments.domain.PaymentService;
import dev.vlearning.payments.domain.PaymentStatus;
import dev.vlearning.payments.persistence.JdbcPaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService payments;
    private final JdbcPaymentRepository repository;

    public PaymentController(PaymentService payments, JdbcPaymentRepository repository) {
        this.payments = payments;
        this.repository = repository;
    }

    /**
     * 201 for a fresh authorization, 200 when an identical request is replayed,
     * 402 when the acquirer declines, 400 on bad input, 409 on a reused key.
     */
    @PostMapping
    public ResponseEntity<?> authorize(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                       @RequestBody AuthorizePaymentRequest request) {
        var result = payments.authorize(idempotencyKey, request.orderId(),
                request.amount(), request.currency(), request.cardToken());
        Payment payment = result.payment();

        if (payment.status() == PaymentStatus.DECLINED) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ErrorResponse.declined(payment.id(), payment.declineReason()));
        }
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PaymentResponse.of(payment));
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        var payment = repository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentResponse.of(payment);
    }
}
