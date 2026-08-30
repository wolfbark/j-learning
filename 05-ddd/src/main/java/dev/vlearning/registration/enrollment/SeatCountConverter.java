package dev.vlearning.registration.enrollment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Boring persistence plumbing, given — see {@link EmailConverter}. */
@Converter(autoApply = true)
public class SeatCountConverter implements AttributeConverter<SeatCount, Integer> {

    @Override
    public Integer convertToDatabaseColumn(SeatCount attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SeatCount convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : new SeatCount(dbData);
    }
}
