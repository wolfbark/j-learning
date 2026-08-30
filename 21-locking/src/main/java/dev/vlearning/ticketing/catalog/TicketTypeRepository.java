package dev.vlearning.ticketing.catalog;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {

    /**
     * Checkpoint 4a: the same lookup, but it must also claim the row, so that a
     * second transaction asking for it waits instead of reading a value that is
     * about to be wrong.
     *
     * <p>Two annotations, no change to the query. Watch the SQL Hibernate emits
     * once you have it ({@code spring.jpa.show-sql=true}).
     */
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") Long id);

    /**
     * Checkpoint 4b: the same again, but refusing to wait at all — fail
     * immediately if somebody else holds the row. An unbounded lock wait is a
     * request thread you have handed over with no way to get it back.
     */
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdateNoWait(@Param("id") Long id);

    /**
     * Checkpoint 7: sell a ticket without locking anything and without reading
     * anything first. The database decides in one statement and tells you whether
     * it happened, by returning 1 or 0.
     *
     * <p>Replace this default method with a real query method — it wants
     * {@code @Modifying} and a {@code @Query} whose {@code WHERE} clause carries
     * the business rule.
     */
    default int reserveIfAvailable(Long id, int quantity) {
        throw new UnsupportedOperationException(
                "Checkpoint 7: UPDATE ticket_type SET available = available - :quantity "
                        + "WHERE id = :id AND available >= :quantity");
    }
}
