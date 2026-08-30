package dev.vlearning.ticketing.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    int countByTicketTypeId(Long ticketTypeId);
}
