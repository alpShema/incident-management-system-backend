-- Encryption at rest for Incident.title (see com.amalitech.hilfe.crypto.EncryptedStringConverter)
-- stores base64 ciphertext that exceeds the current VARCHAR(255) cap for longer titles.
-- Incident.description/InternalNote.body/Message.content are already TEXT, no change needed there.
ALTER TABLE "Incident" ALTER COLUMN title TYPE TEXT;
