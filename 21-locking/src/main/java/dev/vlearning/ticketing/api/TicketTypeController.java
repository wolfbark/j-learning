package dev.vlearning.ticketing.api;

import dev.vlearning.ticketing.catalog.TicketType;
import dev.vlearning.ticketing.catalog.TicketTypeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The same conflict as steps 1-3, one layer out.
 *
 * <p>Two administrators open the price form at 09:00. One saves at 09:05, the
 * other at 09:06 — and the second one's browser is submitting a form built from
 * a version of the record that no longer exists. Neither transaction overlaps
 * the other; the "concurrent" edit is minutes wide, and a database lock is no
 * use at all across a think-time that long.
 *
 * <p>The answer is the same version number, carried to the client as an
 * {@code ETag} and returned as {@code If-Match}. HTTP has had this since 1999
 * and it is still the correct design.
 */
@RestController
@RequestMapping("/ticket-types")
public class TicketTypeController {

    private final TicketTypeRepository ticketTypes;

    TicketTypeController(TicketTypeRepository ticketTypes) {
        this.ticketTypes = ticketTypes;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<TicketTypeResponse> get(@PathVariable long id) {
        TicketType type = ticketTypes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().eTag(etagFor(type)).body(TicketTypeResponse.of(type));
    }

    /**
     * Checkpoint 8: as delivered, {@code ifMatch} is accepted and ignored, so the
     * later save wins whatever it was based on. Make a stale {@code If-Match}
     * fail with {@code 412 Precondition Failed}, and a matching one succeed and
     * return the new ETag.
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> updatePrice(@PathVariable long id,
                                            @RequestHeader(name = "If-Match", required = false) String ifMatch,
                                            @RequestBody PriceUpdate update) {
        TicketType type = ticketTypes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        type.changePriceTo(update.price());
        ticketTypes.saveAndFlush(type);
        return ResponseEntity.noContent().eTag(etagFor(type)).build();
    }

    /** An ETag is an opaque string to the client. Ours happens to be the row version. */
    static String etagFor(TicketType type) {
        return "\"" + type.version() + "\"";
    }

    public record PriceUpdate(long price) {
    }

    public record TicketTypeResponse(long id, String name, long price, int available, long version) {

        static TicketTypeResponse of(TicketType type) {
            return new TicketTypeResponse(type.id(), type.name(), type.price(), type.available(), type.version());
        }
    }
}
