package dev.vlearning.ticketing.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A kind of ticket and how many of it are left.
 *
 * <p>The {@code version} column exists in the schema from the start; what is
 * missing is the one annotation that makes JPA <em>use</em> it. Until step 2
 * adds it, this entity is written back with a plain
 * {@code UPDATE … WHERE id = ?}, and whoever writes last wins.
 */
@Entity
@Table(name = "ticket_type")
public class TicketType {

    @Id
    private Long id;

    private String name;

    private long price;

    private int available;

    @Column(name = "version")
    private long version;

    protected TicketType() {
        // for JPA
    }

    public TicketType(Long id, String name, long price, int available) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.available = available;
    }

    /**
     * The read-modify-write at the heart of steps 1-3: the decision is made in
     * Java, from a value that was true when it was read.
     */
    public void reserve(int quantity) {
        if (available < quantity) {
            throw new SoldOutException(name, available, quantity);
        }
        available -= quantity;
    }

    /** Step 8's subject: an edit made by a human staring at a form. */
    public void changePriceTo(long newPrice) {
        this.price = newPrice;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public long price() {
        return price;
    }

    public int available() {
        return available;
    }

    public long version() {
        return version;
    }
}
