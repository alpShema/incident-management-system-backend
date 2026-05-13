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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArmsClientImpl implements ArmsClient {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String API_KEY_HEADER = "x-api-key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String GET_EMPLOYEE_BIO_QUERY = """
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

    private static final String LIST_EMPLOYEE_INFOS_QUERY = """
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

    private static final String GET_EMPLOYEE_BIO_FOR_EXTERNAL_SERVICE_QUERY = """
                query GetEmployeeBioForExternalService($id: ID!) {
                    getEmployeeBioForExternalService(id: $id) {
                        user_id full_name email profile_image deleted
                    }
                }
            """;

    @Qualifier("armsRestClient")
    private final RestClient restClient;
    private final ArmsProperties properties;

    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        String userId = extractUserIdFromToken(armsToken);

        try {
            EmployeeBioResponse response = postGraphQlWithBearerToken(
                properties.ssoUrl(),
                armsToken,
                new GraphQlRequest(GET_EMPLOYEE_BIO_QUERY, Map.of("id", userId)),
                EmployeeBioResponse.class
            );
            return toArmsUserInfo(requireEmployeeBio(response, userId));
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException exception) {
            throw handleTokenRequestFailure(userId, exception);
        }
    }

    @Override
    public List<ArmsEmployeeInfo> getAllUsers() {
        try {
            EmployeeListResponse response = postGraphQlWithApiKey(
                properties.employeeInfoUrl(),
                new GraphQlRequest(LIST_EMPLOYEE_INFOS_QUERY, null),
                EmployeeListResponse.class
            );
            return requireEmployeeList(response);
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException exception) {
            throw handleApiKeyRequestFailure("employee list request", exception);
        }
    }

    @Override
    public ArmsUserInfo getUserById(String userId) {
        try {
            UserByIdResponse response = postGraphQlWithApiKey(
                properties.employeeInfoUrl(),
                new GraphQlRequest(GET_EMPLOYEE_BIO_FOR_EXTERNAL_SERVICE_QUERY, Map.of("id", userId)),
                UserByIdResponse.class
            );
            return toArmsUserInfo(requireUserById(response, userId));
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException exception) {
            throw handleApiKeyRequestFailure("user lookup request", exception);
        }
    }

    private <T> T postGraphQlWithBearerToken(
        String url,
        String bearerToken,
        GraphQlRequest request,
        Class<T> responseType
    ) {
        return restClient
            .post()
            .uri(url)
            .header(AUTHORIZATION_HEADER, "Bearer " + bearerToken)
            .body(request)
            .retrieve()
            .body(responseType);
    }

    private <T> T postGraphQlWithApiKey(
        String url,
        GraphQlRequest request,
        Class<T> responseType
    ) {
        return restClient
            .post()
            .uri(url)
            .header(AUTHORIZATION_HEADER, "Bearer " + properties.apiKey())
            .header(API_KEY_HEADER, properties.apiKey())
            .body(request)
            .retrieve()
            .body(responseType);
    }

    private EmployeeBio requireEmployeeBio(EmployeeBioResponse response, String userId) {
        if (response != null && response.errors() != null && !response.errors().isEmpty()) {
            log.warn("ARMS rejected token for user {}: {}", userId, response.errors().get(0).message());
            throw new ArmsAuthException("Invalid or expired ARMS token", 401);
        }
        if (response == null || response.data() == null || response.data().employeeBio() == null) {
            log.warn("ARMS returned no employee data for user {}", userId);
            throw new ArmsAuthException("Invalid or expired ARMS token", 401);
        }
        return response.data().employeeBio();
    }

    private List<ArmsEmployeeInfo> requireEmployeeList(EmployeeListResponse response) {
        if (response == null
            || response.data() == null
            || response.data().listEmployeeInfosWithFilters() == null
            || response.data().listEmployeeInfosWithFilters().employeeInfo() == null) {
            throw new ArmsAuthException("ARMS returned empty employee list");
        }
        return response.data().listEmployeeInfosWithFilters().employeeInfo();
    }

    private UserByIdRaw requireUserById(UserByIdResponse response, String userId) {
        if (response == null || response.data() == null || response.data().user() == null) {
            throw new ArmsAuthException("User not found in ARMS: " + userId);
        }
        return response.data().user();
    }

    private ArmsUserInfo toArmsUserInfo(EmployeeBio bio) {
        String email = bio.user() != null ? bio.user().email() : "";
        return new ArmsUserInfo(
            bio.userId(),
            bio.firstName(),
            bio.lastName(),
            email,
            bio.profileImage()
        );
    }

    private ArmsUserInfo toArmsUserInfo(UserByIdRaw raw) {
        String[] nameParts = (raw.fullName() != null ? raw.fullName() : " ").split(" ", 2);
        return new ArmsUserInfo(
            raw.userId(),
            nameParts[0],
            nameParts.length > 1 ? nameParts[1] : "",
            raw.email(),
            raw.profileImage()
        );
    }

    private String extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new ArmsAuthException("Invalid ARMS token: expected JWT format", 401);
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]); // NOSONAR java:S5659 - reading user_id from ARMS token only; signature verification is the ARMS server's responsibility
            JsonNode claims = OBJECT_MAPPER.readTree(payloadBytes); // NOSONAR java:S5659
            String userId = claims.path("user_id").asText(null);

            if (userId == null || userId.isBlank()) {
                throw new ArmsAuthException("ARMS token is missing user_id claim", 401);
            }
            return userId;
        } catch (ArmsAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArmsAuthException("Failed to decode ARMS token", 401, exception);
        }
    }

    private ArmsAuthException handleTokenRequestFailure(String userId, RuntimeException exception) {
        if (exception instanceof HttpClientErrorException clientErrorException) {
            log.error("ARMS rejected request for user {}: {}", userId, clientErrorException.getMessage());
            return new ArmsAuthException("Invalid or expired ARMS token", 401, clientErrorException);
        }
        if (exception instanceof HttpServerErrorException serverErrorException) {
            log.error("ARMS service error: {}", serverErrorException.getMessage());
            return new ArmsAuthException("ARMS service is unavailable", serverErrorException);
        }

        ResourceAccessException resourceAccessException = (ResourceAccessException) exception;
        log.error("ARMS service unreachable: {}", resourceAccessException.getMessage());
        return new ArmsAuthException("ARMS service is unreachable", resourceAccessException);
    }

    private ArmsAuthException handleApiKeyRequestFailure(String requestName, RuntimeException exception) {
        if (exception instanceof HttpClientErrorException clientErrorException) {
            log.error("ARMS rejected {}: {}", requestName, clientErrorException.getMessage());
            return new ArmsAuthException("ARMS rejected the " + requestName, clientErrorException);
        }
        if (exception instanceof HttpServerErrorException serverErrorException) {
            log.error("ARMS service error: {}", serverErrorException.getMessage());
            return new ArmsAuthException("ARMS service is unavailable", serverErrorException);
        }

        ResourceAccessException resourceAccessException = (ResourceAccessException) exception;
        log.error("ARMS service unreachable: {}", resourceAccessException.getMessage());
        return new ArmsAuthException("ARMS service is unreachable", resourceAccessException);
    }

    private record GraphQlRequest(String query, Object variables) {
    }

    private record GraphQlError(@JsonProperty("message") String message) {}

    private record EmployeeBioResponse(
        @JsonProperty("data") EmployeeBioData data,
        @JsonProperty("errors") List<GraphQlError> errors
    ) {}


    private record EmployeeBioData(@JsonProperty("getEmployeeBio") EmployeeBio employeeBio) {
    }

    private record EmployeeBio(
        @JsonProperty("user_id") String userId,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("profile_image") String profileImage,
        @JsonProperty("user") EmployeeBioUser user
    ) {
    }

    private record EmployeeBioUser(@JsonProperty("email") String email) {
    }

    private record EmployeeListResponse(@JsonProperty("data") EmployeeListData data) {
    }

    private record EmployeeListData(
        @JsonProperty("listEmployeeInfosWithFilters") EmployeeListWrapper listEmployeeInfosWithFilters
    ) {
    }

    private record EmployeeListWrapper(
        @JsonProperty("EmployeeInfo") List<ArmsEmployeeInfo> employeeInfo
    ) {
    }

    private record UserByIdResponse(@JsonProperty("data") UserByIdData data) {
    }

    private record UserByIdData(
        @JsonProperty("getEmployeeBioForExternalService") UserByIdRaw user
    ) {
    }

    private record UserByIdRaw(
        @JsonProperty("user_id") String userId,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("email") String email,
        @JsonProperty("profile_image") String profileImage
    ) {
    }
}
