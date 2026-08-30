package dev.vlearning.registration.catalog.internal;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vlearning.registration.catalog.CourseCatalog;
import dev.vlearning.registration.catalog.CourseInfo;
import dev.vlearning.registration.shared.CourseId;

/**
 * The catalog's open host service: translates the internal {@link Course}
 * aggregate into the published {@link CourseInfo} snapshot. Consumers never
 * see the entity.
 */
@Service
@Transactional(readOnly = true)
class DefaultCourseCatalog implements CourseCatalog {

    private final CourseRepository courses;

    DefaultCourseCatalog(CourseRepository courses) {
        this.courses = courses;
    }

    @Override
    public Optional<CourseInfo> findBy(CourseId courseId) {
        return courses.findByCode(courseId.value())
                .map(course -> new CourseInfo(
                        new CourseId(course.getCode()), course.getTitle(), course.getCapacity()));
    }
}
