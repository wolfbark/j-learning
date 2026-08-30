package dev.vlearning.registration.enrollment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Boring persistence plumbing, given so the JPA mapping never blocks the
 * domain work: once {@code Enrollment} holds an {@link Email}, this converter
 * makes it a plain VARCHAR column. Value objects exist in the model, not in
 * the schema.
 */
@Converter(autoApply = true)
public class EmailConverter implements AttributeConverter<Email, String> {

    @Override
    public String convertToDatabaseColumn(Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Email convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Email(dbData);
    }
}
