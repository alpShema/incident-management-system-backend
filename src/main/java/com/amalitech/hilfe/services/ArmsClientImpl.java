package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ArmsProperties;
import com.amalitech.hilfe.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArmsClientImpl implements ArmsClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Qualifier("armsRestClient")
    private final RestClient restClient;
    private final ArmsProperties properties;

    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        String userId = extractUserIdFromToken(armsToken);
        log.debug("Extracted user_id from ARMS token: {}", userId);

        String query = """
            query GetEmployeeBio($id: ID!) {
                getEmployeeBio(id: $id) {
                    user_id
                    first_name
                    last_name
                    profile_image
                    user {
                        email
                    }
                }
            }
        """;

        try {
            String raw = restClient
                    .post()
                    .uri(properties.ssoUrl())
                    .header("Authorization", "Bearer " + armsToken)
                    .body(new GraphQlRequest(query, Map.of("id", userId)))
                    .retrieve()
                    .body(String.class);

            log.info("ARMS getEmployeeBio raw response: {}", raw);

            EmployeeBioResponse response;
            try {
                response = OBJECT_MAPPER.readValue(raw, EmployeeBioResponse.class);
            } catch (Exception e) {
                throw new ArmsAuthException("Failed to parse ARMS response", e);
            }

            if (response == null || response.data() == null || response.data().employeeBio() == null) {
                throw new ArmsAuthException("ARMS returned empty employee bio for user: " + userId);
            }

            EmployeeBio bio = response.data().employeeBio();
            String email = bio.user() != null ? bio.user().email() : "";

            return new ArmsUserInfo(
                    bio.userId(),
                    bio.firstName(),
                    bio.lastName(),
                    email,
                    bio.profileImage()
            );

        } catch (HttpClientErrorException e) {
            log.error("ARMS rejected request for user {}: {}", userId, e.getMessage());
            throw new ArmsAuthException("Invalid or expired ARMS token", e);
        } catch (ResourceAccessException e) {
            log.error("ARMS service unreachable: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        } catch (HttpServerErrorException e) {
            log.error("ARMS service error: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        }
    }

    private String extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new ArmsAuthException("Invalid ARMS token: expected JWT format");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = OBJECT_MAPPER.readTree(payloadBytes);

            String userId = claims.path("user_id").asText(null);
            if (userId == null || userId.isBlank()) {
                throw new ArmsAuthException("ARMS token is missing user_id claim");
            }
            return userId;
        } catch (ArmsAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new ArmsAuthException("Failed to decode ARMS token", e);
        }
    }

    @Override
    public List<ArmsEmployeeInfo> getAllUsers() {
        String query = """
            query ListEmployeeInfosWithFilters {
                listEmployeeInfosWithFilters {
                    EmployeeInfo {
                        user_id
                        active
                        employee_bio { full_name profile_image
                            employee_contacts { work_email }
                        }
                        position { position_name }
                        location { town }
                    }
                }
            }
        """;

        try {
            EmployeeListResponse response = restClient
                    .post()
                    .uri(properties.employeeInfoUrl())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("x-api-key", properties.apiKey())
                    .body(new GraphQlRequest(query, null))
                    .retrieve()
                    .body(EmployeeListResponse.class);

            if (response == null || response.data() == null) {
                throw new ArmsAuthException("ARMS returned empty employee list");
            }

            return response.data().listEmployeeInfosWithFilters().employeeInfo();

        } catch (HttpClientErrorException e) {
            log.error("ARMS rejected employee list request: {}", e.getMessage());
            throw new ArmsAuthException("ARMS rejected the employee list request", e);
        } catch (HttpServerErrorException e) {
            log.error("ARMS service error: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        } catch (ResourceAccessException e) {
            log.error("ARMS service unreachable: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        }
    }

    @Override
    public ArmsUserInfo getUserById(String userId) {
        String query = """
            query GetEmployeeBioForExternalService($id: ID!) {
                getEmployeeBioForExternalService(id: $id) {
                    user_id full_name email profile_image deleted
                }
            }
        """;

        try {
            UserByIdResponse response = restClient
                    .post()
                    .uri(properties.employeeInfoUrl())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("x-api-key", properties.apiKey())
                    .body(new GraphQlRequest(query, Map.of("id", userId)))
                    .retrieve()
                    .body(UserByIdResponse.class);

            if (response == null || response.data() == null || response.data().user() == null) {
                throw new ArmsAuthException("User not found in ARMS: " + userId);
            }

            UserByIdRaw raw = response.data().user();
            String[] nameParts = (raw.fullName() != null ? raw.fullName() : " ").split(" ", 2);
            return new ArmsUserInfo(
                    raw.userId(),
                    nameParts[0],
                    nameParts.length > 1 ? nameParts[1] : "",
                    raw.email(),
                    raw.profileImage()
            );

        } catch (HttpClientErrorException e) {
            log.error("ARMS rejected user lookup: {}", e.getMessage());
            throw new ArmsAuthException("ARMS rejected the user lookup request", e);
        } catch (HttpServerErrorException e) {
            log.error("ARMS service error: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        } catch (ResourceAccessException e) {
            log.error("ARMS service unreachable: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        }
    }

    // ── GraphQL request wrapper ───────────────────────────────────────────────

    private record GraphQlRequest(String query, Object variables) {}

    // ── getEmployeeBio response ───────────────────────────────────────────────

    private record EmployeeBioResponse(@JsonProperty("data") EmployeeBioData data) {}
    private record EmployeeBioData(@JsonProperty("getEmployeeBio") EmployeeBio employeeBio) {}
    private record EmployeeBio(
            @JsonProperty("user_id")       String userId,
            @JsonProperty("first_name")    String firstName,
            @JsonProperty("last_name")     String lastName,
            @JsonProperty("profile_image") String profileImage,
            @JsonProperty("user")          EmployeeBioUser user
    ) {}
    private record EmployeeBioUser(@JsonProperty("email") String email) {}

    // ── getAllUsers response ───────────────────────────────────────────────────

    private record EmployeeListResponse(@JsonProperty("data") EmployeeListData data) {}
    private record EmployeeListData(
            @JsonProperty("listEmployeeInfosWithFilters") EmployeeListWrapper listEmployeeInfosWithFilters
    ) {}
    private record EmployeeListWrapper(
            @JsonProperty("EmployeeInfo") List<ArmsEmployeeInfo> employeeInfo
    ) {}

    // ── getUserById response ──────────────────────────────────────────────────

    private record UserByIdResponse(@JsonProperty("data") UserByIdData data) {}
    private record UserByIdData(
            @JsonProperty("getEmployeeBioForExternalService") UserByIdRaw user
    ) {}
    private record UserByIdRaw(
            @JsonProperty("user_id")       String userId,
            @JsonProperty("full_name")     String fullName,
            @JsonProperty("email")         String email,
            @JsonProperty("profile_image") String profileImage
    ) {}
}
