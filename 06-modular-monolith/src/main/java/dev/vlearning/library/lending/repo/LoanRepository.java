package dev.vlearning.library.lending.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import dev.vlearning.library.lending.domain.Loan;

public interface LoanRepository extends Repository<Loan, Long> {

    Loan save(Loan loan);

    Optional<Loan> findById(Long id);

    List<Loan> findByReturnedAtIsNullAndDueDateBefore(LocalDate date);
}
