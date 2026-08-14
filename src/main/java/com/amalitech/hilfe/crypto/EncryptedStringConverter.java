package com.amalitech.hilfe.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Only encrypted values are decrypted on read here -- {@link #convertToDatabaseColumn} is a
 * pass-through, deliberately asymmetric with {@link #convertToEntityAttribute}.
 * <p>
 * Encryption only applies to fields belonging to confidential incidents (title/description,
 * InternalNote.body, Message.content), and "is this incident confidential" is a property of the
 * linked {@code IncidentType}, not of the row this converter is attached to -- an
 * {@code AttributeConverter} only ever sees the single attribute value, with no access to sibling
 * fields or joined entities, so it cannot make that call itself. The services that already know
 * the answer (IncidentService, InternalNoteService, MessageService) call
 * {@link FieldEncryptionService#encrypt} explicitly before setting the field when the incident is
 * confidential; this converter's job is just to make the read side transparent regardless of
 * whether the stored value happens to be ciphertext or legacy/never-encrypted plaintext.
 */
@Component
@Converter(autoApply = false)
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptionService fieldEncryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptionService.decrypt(dbData);
    }
}
