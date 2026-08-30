package dev.vlearning.registration.enrollment;

import dev.vlearning.registration.shared.CourseId;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Boring persistence plumbing, given — see {@link EmailConverter}. */
@Converter(autoApply = true)
public class CourseIdConverter implements AttributeConverter<CourseId, String> {

    @Override
    public String convertToDatabaseColumn(CourseId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public CourseId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new CourseId(dbData);
    }
}
