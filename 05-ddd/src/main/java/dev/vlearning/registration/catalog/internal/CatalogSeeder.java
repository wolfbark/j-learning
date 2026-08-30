package dev.vlearning.registration.catalog.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the three courses of the training platform we are building while
 * learning on it. Yes, MOD-401 is lesson 06. Capacity three — book early.
 */
@Component
class CatalogSeeder implements ApplicationRunner {

    private final CourseRepository courses;

    CatalogSeeder(CourseRepository courses) {
        this.courses = courses;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (courses.findByCode("DDD-101").isPresent()) {
            return;
        }
        courses.save(new Course("DDD-101", "Strategic Domain-Driven Design", 12));
        courses.save(new Course("TDD-201", "Test-Driven Development, Both Schools", 8));
        courses.save(new Course("MOD-401", "Modular Monoliths with Spring Modulith", 3));
    }
}
