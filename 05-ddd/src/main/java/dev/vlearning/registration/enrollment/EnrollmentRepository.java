package dev.vlearning.registration.enrollment;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Repository for the Enrollment aggregate. Narrow on purpose: an aggregate
 * repository mimics a collection of aggregates, it is not a query toolbox.
 */
public interface EnrollmentRepository extends Repository<Enrollment, Long> {

    Enrollment save(Enrollment enrollment);

    Optional<Enrollment> findById(Long id);
}
