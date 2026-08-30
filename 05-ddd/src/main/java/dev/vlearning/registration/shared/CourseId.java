package dev.vlearning.registration.shared;

/**
 * Identifier of a course — the one concept the enrollment and catalog contexts
 * agree to share (a deliberately tiny shared kernel; see the lesson's step 7 on
 * why shared kernels are kept on a short leash).
 *
 * <p>Currently a bare shell so the checkpoint tests compile. Checkpoint 2 makes
 * it a real value object: course codes look like {@code DDD-101} — two to five
 * uppercase letters, a dash, three digits — and arrive from the web in whatever
 * casing the user typed.
 */
public record CourseId(String value) {
}
