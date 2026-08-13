package com.amalitech.hilfe.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptionService fieldEncryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptionService.decrypt(dbData);
    }
}
