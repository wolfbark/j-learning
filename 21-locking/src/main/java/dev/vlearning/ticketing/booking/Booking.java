package dev.vlearning.ticketing.booking;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ticketTypeId;

    private String attendee;

    private int quantity;

    protected Booking() {
        // for JPA
    }

    public Booking(Long ticketTypeId, String attendee, int quantity) {
        this.ticketTypeId = ticketTypeId;
        this.attendee = attendee;
        this.quantity = quantity;
    }

    public Long id() {
        return id;
    }

    public Long ticketTypeId() {
        return ticketTypeId;
    }

    public String attendee() {
        return attendee;
    }

    public int quantity() {
        return quantity;
    }
}
