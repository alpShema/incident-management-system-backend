package com.amalitech.hilfe.services;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class ArmsTokenExpiryService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public long getRemainingLifetimeSeconds(String armsToken) {
        if (armsToken == null || armsToken.isBlank()) {
            throw new ArmsAuthException("Your session could not be verified. Please log in again.", 401);
        }

        try {
            String[] tokenParts = armsToken.split("\\.");
            if (tokenParts.length < 2) {
                throw new ArmsAuthException("Your session could not be verified. Please log in again.", 401);
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(tokenParts[1]); // NOSONAR java:S5659 - reading ARMS token expiry only; signature verification is the ARMS server's responsibility
            JsonNode payload = OBJECT_MAPPER.readTree(new String(payloadBytes, StandardCharsets.UTF_8)); // NOSONAR java:S5659
            JsonNode expiryNode = payload.get("exp");
            if (expiryNode == null || !expiryNode.canConvertToLong()) {
                throw new ArmsAuthException("Your session could not be verified. Please log in again.", 401);
            }

            long remainingSeconds = expiryNode.asLong() - Instant.now().getEpochSecond();
            if (remainingSeconds <= 0) {
                throw new ArmsAuthException("Your session has expired. Please log in again.", 401);
            }

            return remainingSeconds;
        } catch (ArmsAuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ArmsAuthException("Your session could not be verified. Please log in again.", 401, ex);
        }
    }
}
