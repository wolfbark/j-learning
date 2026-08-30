package dev.vlearning.registration.catalog.internal;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Catalog-internal persistence. Not part of the published interface — if
 * another context injects this, it is reaching across the boundary.
 */
public interface CourseRepository extends Repository<Course, String> {

    Optional<Course> findByCode(String code);

    Course save(Course course);
}
