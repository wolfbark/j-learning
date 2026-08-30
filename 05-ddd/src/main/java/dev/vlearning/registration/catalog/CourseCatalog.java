package dev.vlearning.registration.catalog;

import java.util.Optional;

import dev.vlearning.registration.shared.CourseId;

/**
 * The catalog context's <em>published interface</em> — the only doorway other
 * contexts are supposed to use. In context-mapping terms this is an open host
 * service, and {@link CourseInfo} is its published language.
 *
 * <p>Nothing behind {@code catalog.internal} is anyone else's business.
 */
public interface CourseCatalog {

    Optional<CourseInfo> findBy(CourseId courseId);
}
