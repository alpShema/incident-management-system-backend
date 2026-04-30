package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsClient;
import com.amalitech.hilfe.auth.ArmsProperties;
import com.amalitech.hilfe.auth.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealArmsClient implements ArmsClient {
    @Qualifier("armsRestClient")
    private final RestClient restClient;
    private final ArmsProperties properties;

    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        try {
            ArmsRawResponse raw = restClient
                    .get()
                    .uri(properties.ssoUrl())
                    .header("Authorization", "Bearer " + armsToken)
                    .retrieve()
                    .body(ArmsRawResponse.class);

            if (raw == null) {
                throw new ArmsAuthException("ARMS returned empty response");
            }

            return new ArmsUserInfo(
                    raw.userId(),
                    raw.firstName(),
                    raw.lastName(),
                    raw.email(),
                    raw.profileImage()
            );

        } catch (HttpClientErrorException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("Invalid or expired ARMS token", e);

        } catch (ResourceAccessException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        }catch (HttpServerErrorException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        }


    }

    @Override
    public List<ArmsEmployeeInfo> getAllUsers() {
        // 1. Build the GraphQL query string
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

        // 2. POST to employeeInfoUrl with x-api-key header
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
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS rejected the employee list request", e);
        } catch (HttpServerErrorException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        } catch (ResourceAccessException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        }

    }

    @Override
    public ArmsUserInfo getUserById(String userId) {
        // 1. Build GraphQL query with userId as variable
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
                    .body(new GraphQlRequest(query, java.util.Map.of("id", userId)))
                    .retrieve()
                    .body(UserByIdResponse.class);

            if (response == null || response.data() == null || response.data().user() == null) {
                throw new ArmsAuthException("User not found in ARMS: " + userId);
            }

            ArmsRawResponse raw = response.data().user();
            return new ArmsUserInfo(
                    raw.userId(),
                    raw.firstName(),
                    raw.lastName(),
                    raw.email(),
                    raw.profileImage()
            );

        } catch (HttpClientErrorException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS rejected the user lookup request", e);
        } catch (HttpServerErrorException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unavailable", e);
        } catch (ResourceAccessException e) {
            log.error("Invalid or expired ARMS token: {}", e.getMessage());
            throw new ArmsAuthException("ARMS service is unreachable", e);
        }

    }

    private record ArmsRawResponse(
            @JsonProperty("user_id")       String userId,
            @JsonProperty("first_name")    String firstName,
            @JsonProperty("last_name")     String lastName,
            @JsonProperty("email")         String email,
            @JsonProperty("profile_image") String profileImage
    ) {}
    private record GraphQlRequest(
            String query,
            Object variables
    ) {}

    private record EmployeeListResponse(
            @JsonProperty("data") EmployeeListData data
    ) {}

    private record EmployeeListData(
            @JsonProperty("listEmployeeInfosWithFilters") EmployeeListWrapper listEmployeeInfosWithFilters
    ) {}

    private record EmployeeListWrapper(
            @JsonProperty("EmployeeInfo") List<ArmsEmployeeInfo> employeeInfo
    ) {}
    private record UserByIdResponse(
            @JsonProperty("data") UserByIdData data
    ) {}

    private record UserByIdData(
            @JsonProperty("getEmployeeBioForExternalService") ArmsRawResponse user
    ) {}
}
