package az.aqroinvest.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Sadə siyahıları (məs. tövsiyə olunan bitkilər) vergüllə ayrılmış mətn kimi saxlayır. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        return String.join("|", attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(dbData.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
