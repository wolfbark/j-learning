package dev.vlearning.registration.enrollment;

/**
 * Shell of a value object — it exists so the checkpoint tests compile, but it
 * accepts anything and normalizes nothing. Checkpoint 2 turns it into the real
 * thing: an {@code Email} that can only ever hold a valid, normalized address,
 * so nobody downstream has to re-check it.
 */
public record Email(String value) {
}
