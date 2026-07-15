package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ArmsProperties;
import com.amalitech.hilfe.constants.ApiMessages;
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

    private static final String AUTH_SERVICE_UNAVAILABLE_MESSAGE =
            "The authentication service is temporarily unavailable. Please try again shortly.";

    private static final String GET_EMPLOYEE_ACTIVE_INFO_QUERY = """
                query GetEmployeeActiveInfo($userId: ID!) {
                    getEmployeeActiveInfo(user_id: $userId) {
                        user_id
                        user {
                            first_name
                            last_name
                            other_name
                            email
                        }
                        employee_bio {
                            profile_image
                        }
                        position {
                            position_name
                        }
                        office {
                            id
                            name
                            archive
                            organization_id
                            organization {
                                offices {
                                    id
                                    name
                                    archive
                                    organization_id
                                }
                            }
                        }
                    }
                    getEmployeeContact(id: $userId) {
                        id
                        user_id
                        work_email
                        personal_email
                        phone_number_1
                        created_by
                        postal_address
                        street_address
                        province_state
                        country
                        city
                        digital_address
                        created_at
                        updated_at
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
            EmployeeActiveInfoResponse response = postGraphQlWithBearerToken(
                properties.ssoUrl(),
                armsToken,
                new GraphQlRequest(GET_EMPLOYEE_ACTIVE_INFO_QUERY, Map.of("userId", userId)),
                EmployeeActiveInfoResponse.class
            );
            log.debug("ARMS getEmployeeActiveInfo response for user {}: {}", userId, response);
            return toArmsUserInfo(requireEmployeeActiveInfo(response, userId), response.data().contact());
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
            log.debug("ARMS listEmployeeInfos response: {} entries",
                    response != null && response.data() != null
                            && response.data().listEmployeeInfosWithFilters() != null
                            && response.data().listEmployeeInfosWithFilters().employeeInfo() != null
                            ? response.data().listEmployeeInfosWithFilters().employeeInfo().size() : "null");
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
            log.debug("ARMS getUserById response for user {}: {}", userId, response);
            return toArmsUserInfo(requireUserById(response));
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

    private EmployeeActiveInfo requireEmployeeActiveInfo(EmployeeActiveInfoResponse response, String userId) {
        if (response != null && response.errors() != null && !response.errors().isEmpty()) {
            log.warn("ARMS rejected token for user {}: {}", userId, response.errors().get(0).message());
            throw new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401);
        }
        if (response == null || response.data() == null || response.data().activeInfo() == null) {
            log.warn("ARMS returned no employee data for user {}", userId);
            throw new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401);
        }
        return response.data().activeInfo();
    }

    private List<ArmsEmployeeInfo> requireEmployeeList(EmployeeListResponse response) {
        if (response == null
            || response.data() == null
            || response.data().listEmployeeInfosWithFilters() == null
            || response.data().listEmployeeInfosWithFilters().employeeInfo() == null) {
            throw new ArmsAuthException("Unable to retrieve the employee list at this time. Please try again later.");
        }
        return response.data().listEmployeeInfosWithFilters().employeeInfo();
    }

    private UserByIdRaw requireUserById(UserByIdResponse response) {
        if (response == null || response.data() == null || response.data().user() == null) {
            throw new ArmsAuthException("The requested user could not be found.");
        }
        return response.data().user();
    }

    private ArmsUserInfo toArmsUserInfo(EmployeeActiveInfo activeInfo, EmployeeContact contact) {
        EmployeeActiveUser user = activeInfo.user();
        String email = firstNonBlank(
                contact != null ? contact.personalEmail() : null,
                contact != null ? contact.workEmail() : null,
                user != null ? user.email() : null
        );
        return new ArmsUserInfo(
            activeInfo.userId(),
            user != null ? user.firstName() : "",
            user != null ? user.lastName() : "",
            user != null ? user.otherName() : null,
            email,
            activeInfo.employeeBio() != null ? activeInfo.employeeBio().profileImage() : null,
            activeInfo.position() != null ? activeInfo.position().positionName() : null,
            activeInfo.office() != null ? activeInfo.office().name() : null,
            contact != null ? contact.workEmail() : null,
            contact != null ? contact.personalEmail() : null,
            contact != null ? contact.phoneNumber1() : null
        );
    }

    private ArmsUserInfo toArmsUserInfo(UserByIdRaw raw) {
        String[] nameParts = (raw.fullName() != null ? raw.fullName() : " ").split(" ", 2);
        return new ArmsUserInfo(
            raw.userId(),
            nameParts[0],
            nameParts.length > 1 ? nameParts[1] : "",
            raw.email(),
            raw.profileImage(),
            null
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                throw new ArmsAuthException(ApiMessages.SESSION_UNVERIFIABLE, 401);
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]); // NOSONAR java:S5659 - reading user_id from ARMS token only; signature verification is the ARMS server's responsibility
            JsonNode claims = OBJECT_MAPPER.readTree(payloadBytes); // NOSONAR java:S5659
            String userId = claims.path("user_id").asText(null);

            if (userId == null || userId.isBlank()) {
                throw new ArmsAuthException(ApiMessages.SESSION_UNVERIFIABLE, 401);
            }
            return userId;
        } catch (ArmsAuthException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArmsAuthException(ApiMessages.SESSION_UNVERIFIABLE, 401, exception);
        }
    }

    private ArmsAuthException handleTokenRequestFailure(String userId, RuntimeException exception) {
        if (exception instanceof HttpClientErrorException clientErrorException) {
            log.error("ARMS rejected request for user {}: {}", userId, clientErrorException.getMessage());
            return new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401, clientErrorException);
        }
        return handleServiceUnreachable(exception);
    }

    private ArmsAuthException handleApiKeyRequestFailure(String requestName, RuntimeException exception) {
        if (exception instanceof HttpClientErrorException clientErrorException) {
            log.error("ARMS rejected {}: {}", requestName, clientErrorException.getMessage());
            return new ArmsAuthException("The request could not be completed. Please try again later.", clientErrorException);
        }
        return handleServiceUnreachable(exception);
    }

    private ArmsAuthException handleServiceUnreachable(RuntimeException exception) {
        if (exception instanceof HttpServerErrorException serverErrorException) {
            log.error("ARMS service error: {}", serverErrorException.getMessage());
            return new ArmsAuthException(AUTH_SERVICE_UNAVAILABLE_MESSAGE, serverErrorException);
        }

        ResourceAccessException resourceAccessException = (ResourceAccessException) exception;
        log.error("ARMS service unreachable: {}", resourceAccessException.getMessage());
        return new ArmsAuthException(AUTH_SERVICE_UNAVAILABLE_MESSAGE, resourceAccessException);
    }

    private record GraphQlRequest(String query, Object variables) {
    }

    private record GraphQlError(@JsonProperty("message") String message) {}

    private record EmployeeActiveInfoResponse(
        @JsonProperty("data") EmployeeActiveInfoData data,
        @JsonProperty("errors") List<GraphQlError> errors
    ) {}

    private record EmployeeActiveInfoData(
        @JsonProperty("getEmployeeActiveInfo") EmployeeActiveInfo activeInfo,
        @JsonProperty("getEmployeeContact") EmployeeContact contact
    ) {}

    private record EmployeeActiveInfo(
        @JsonProperty("user_id") String userId,
        @JsonProperty("user") EmployeeActiveUser user,
        @JsonProperty("employee_bio") EmployeeActiveBio employeeBio,
        @JsonProperty("position") EmployeePosition position,
        @JsonProperty("office") EmployeeOffice office
    ) {}

    private record EmployeeActiveUser(
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("other_name") String otherName,
        @JsonProperty("email") String email
    ) {}

    private record EmployeeActiveBio(@JsonProperty("profile_image") String profileImage) {}

    private record EmployeePosition(@JsonProperty("position_name") String positionName) {}

    private record EmployeeOffice(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("archive") Boolean archive,
        @JsonProperty("organization_id") String organizationId,
        @JsonProperty("organization") EmployeeOrganization organization
    ) {}

    private record EmployeeOrganization(@JsonProperty("offices") List<EmployeeOffice> offices) {}

    private record EmployeeContact(
        @JsonProperty("id") String id,
        @JsonProperty("user_id") String userId,
        @JsonProperty("work_email") String workEmail,
        @JsonProperty("personal_email") String personalEmail,
        @JsonProperty("phone_number_1") String phoneNumber1,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("postal_address") String postalAddress,
        @JsonProperty("street_address") String streetAddress,
        @JsonProperty("province_state") String provinceState,
        @JsonProperty("country") String country,
        @JsonProperty("city") String city,
        @JsonProperty("digital_address") String digitalAddress,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt
    ) {}

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
