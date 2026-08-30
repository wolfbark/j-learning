package dev.vlearning.parcels.schema;

import java.util.Optional;

/**
 * One field of a message contract. {@code defaultValue} is the entire difference between a
 * compatible and a breaking addition: a reader that meets old data can fill a missing field
 * from the default, and cannot invent one that has none.
 */
public record FieldSpec(String name, FieldType type, boolean required, String defaultValue) {

    public static FieldSpec required(String name, FieldType type) {
        return new FieldSpec(name, type, true, null);
    }

    public static FieldSpec optional(String name, FieldType type, String defaultValue) {
        return new FieldSpec(name, type, false, defaultValue);
    }

    public Optional<String> defaultIfAny() {
        return Optional.ofNullable(defaultValue);
    }
}
