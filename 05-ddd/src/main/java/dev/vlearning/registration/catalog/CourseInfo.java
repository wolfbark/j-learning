package dev.vlearning.registration.catalog;

import org.jmolecules.ddd.annotation.ValueObject;

import dev.vlearning.registration.shared.CourseId;

/**
 * Published language of the catalog context: what a course looks like to the
 * outside world. Deliberately not the {@code Course} entity — consumers get a
 * value object snapshot, never a live reference into another context's model.
 */
@ValueObject
public record CourseInfo(CourseId id, String title, int capacity) {
}
