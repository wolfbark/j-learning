package dev.vlearning.parcels.schema;

import java.util.List;
import java.util.Optional;

public record RecordSchema(String name, int version, List<FieldSpec> fields) {

    public RecordSchema {
        fields = List.copyOf(fields);
    }

    public Optional<FieldSpec> field(String fieldName) {
        return fields.stream().filter(f -> f.name().equals(fieldName)).findFirst();
    }

    /** Same schema, plus one field — the shape of every "we just need one more attribute" ticket. */
    public RecordSchema plus(FieldSpec field, int newVersion) {
        var extended = new java.util.ArrayList<>(fields);
        extended.add(field);
        return new RecordSchema(name, newVersion, extended);
    }

    public RecordSchema without(String fieldName, int newVersion) {
        return new RecordSchema(name, newVersion, fields.stream().filter(f -> !f.name().equals(fieldName)).toList());
    }

    public RecordSchema retyped(String fieldName, FieldType type, int newVersion) {
        return new RecordSchema(name, newVersion, fields.stream()
                .map(f -> f.name().equals(fieldName) ? new FieldSpec(f.name(), type, f.required(), f.defaultValue()) : f)
                .toList());
    }
}
