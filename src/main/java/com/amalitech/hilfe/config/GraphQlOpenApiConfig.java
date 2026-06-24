package com.amalitech.hilfe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Not a Spring bean — instantiated directly inside SwaggerConfig.graphQlApiGroup()
 * so it scopes only to the "GraphQL API" Swagger group and doesn't pollute the REST group.
 */
public class GraphQlOpenApiConfig implements OpenApiCustomizer {

    private static final String K_PAGE = "page";
    private static final String K_SIZE = "size";
    private static final String K_INPUT = "input";
    private static final String K_ID = "id";
    private static final String K_QUERY = "query";
    private static final String K_NAME = "name";
    private static final String K_STATUS = "status";
    private static final String K_DESCRIPTION = "description";
    private static final String K_DEPARTMENT_ID = "departmentId";
    private static final String K_CATEGORY_ID = "categoryId";
    private static final String K_INCIDENT_ID = "incidentId";
    private static final String K_MESSAGE = "message";
    private static final String K_ACTIVE = "active";
    private static final String K_VARIABLES = "variables";
    private static final String K_USER_ID = "userId";
    private static final String K_SEVERITY_ID = "severityId";
    private static final String K_ROLE_CODE = "roleCode";
    private static final String K_AGENT_ID = "agentId";
    private static final String ROLE_AGENT = "AGENT";

    private static final String TAG_GRAPHQL_API = "GraphQL API";
    private static final String INCIDENT_UUID = "incident-uuid-here";
    private static final String DEPT_UUID = "dept-uuid-here";
    private static final String CAT_UUID = "cat-uuid-here";
    private static final String TOPIC_UUID = "topic-uuid-here";
    private static final String SEVERITY_UUID = "severity-uuid-here";
    private static final String LOCATION_UUID = "location-uuid-here";
    private static final String GROUP_UUID = "group-uuid-here";
    private static final String FAQ_UUID = "faq-uuid-here";
    private static final String AGENT_UUID = "agent-uuid-here";

    private static final String DESCRIPTION = """
            All GraphQL operations go through a **single HTTP endpoint**: `POST /graphql`.

            **Request format**
            ```json
            {
              "query":         "...",
              "variables":     { ... },
              "operationName": "..."
            }
            ```
            - `query` — required. The GraphQL operation string (query or mutation).
            - `variables` — optional. JSON object of named variable values.
            - `operationName` — optional. Disambiguates when the document contains multiple named operations.

            **Response envelope**
            Every response is wrapped by the server in `{ message, data, errors }`:
            ```json
            {
              "message": "Success",
              "data": { "incidents": { "items": [...], "totalElements": 12 } },
              "errors": null
            }
            ```
            On failure the HTTP status is still **200** — check `errors[].extensions.status` for the semantic error code.

            **Authentication**
            Protected operations require a valid `access_token` HttpOnly cookie.
            1. Call the `login` mutation — the server sets the cookie automatically.
            2. All subsequent requests must include cookies (`credentials: include` / `withCredentials: true`).
            3. In GraphiQL: Settings → **Request credentials** → `include`.

            **Interactive exploration**
            Use **GraphiQL** at `/graphiql` for live schema browsing and query execution.
            """;

    @Override
    public void customise(OpenAPI openApi) {
        openApi.addTagsItem(new Tag().name(TAG_GRAPHQL_API).description(DESCRIPTION));
        if (openApi.getPaths() == null) {
            openApi.setPaths(new Paths());
        }
        openApi.getPaths().addPathItem("/graphql", buildPath());
    }

    // ──────────────────────────────────────
    // Path item
    // ──────────────────────────────────────

    private PathItem buildPath() {
        Schema<?> requestSchema = new ObjectSchema()
                .addProperty(K_QUERY, new StringSchema().description("GraphQL query or mutation string"))
                .addProperty(K_VARIABLES, new ObjectSchema().description("Variable values (optional)"))
                .addProperty("operationName", new StringSchema().description("Operation name (optional)"))
                .required(List.of(K_QUERY));

        Schema<?> errorExtSchema = new ObjectSchema()
                .addProperty(K_STATUS, new Schema<Integer>().type("integer").description("HTTP-equivalent status code"))
                .addProperty("error", new StringSchema().description("Short error label"))
                .addProperty(K_MESSAGE, new StringSchema())
                .addProperty("timestamp", new StringSchema().format("date-time"))
                .addProperty("path", new StringSchema().example("/graphql"));

        Schema<?> errorSchema = new ObjectSchema()
                .addProperty(K_MESSAGE, new StringSchema())
                .addProperty("path", new Schema<>().type("array"))
                .addProperty("extensions", errorExtSchema);

        Schema<?> responseSchema = new ObjectSchema()
                .addProperty(K_MESSAGE, new StringSchema().description("'Success' or first error message"))
                .addProperty("data", new ObjectSchema().nullable(true).description("Resolved data keyed by operation name"))
                .addProperty("errors", new Schema<>().type("array").nullable(true).items(errorSchema));

        return new PathItem()
                .post(new Operation()
                        .operationId("executeGraphQL")
                        .tags(List.of(TAG_GRAPHQL_API))
                        .summary("Execute any GraphQL query or mutation")
                        .description("Select an operation from the **Examples** dropdown to pre-fill the request body.")
                        .requestBody(new RequestBody()
                                .required(true)
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(requestSchema).examples(buildAllExamples()))))
                        .responses(new ApiResponses()
                                .addApiResponse("200", new ApiResponse()
                                        .description("Always 200 — inspect `errors` for operation-level failures")
                                        .content(new Content().addMediaType("application/json",
                                                new MediaType().schema(responseSchema))))
                                .addApiResponse("400", new ApiResponse().description("Malformed request body (missing `query` or invalid JSON)"))
                                .addApiResponse("401", new ApiResponse().description("No auth cookie — call the `login` mutation first")))
                        .security(List.of(new SecurityRequirement().addList("cookieAuth"))));
    }

    // ──────────────────────────────────────
    // Examples registry
    // ──────────────────────────────────────

    private Map<String, Example> buildAllExamples() {
        Map<String, Example> ex = new LinkedHashMap<>();

        // ── Auth ──────────────────────────────────
        ex.put("[auth] login", ex(
                "Authenticate user (public)",
                "Sets access_token, refresh_token and arms_token HttpOnly cookies. No prior auth required.",
                q("mutation Login($input: LoginInput!) {\n  login(input: $input) {\n    userId\n    email\n    fullName\n    role\n    permissions\n  }\n}",
                        vars(K_INPUT, vars("armsToken", "your-arms-token")))));

        ex.put("[auth] refreshToken", ex(
                "Refresh access token (public)",
                "Reads refresh_token and arms_token cookies, issues a new access_token. No body variables required.",
                q("mutation {\n  refreshToken {\n    userId\n    email\n    fullName\n    role\n  }\n}")));

        ex.put("[auth] logout", ex(
                "Logout (public)",
                "Clears all auth cookies. Returns true on success.",
                q("mutation {\n  logout\n}")));

        ex.put("[auth] myPermissions", ex(
                "Get my permissions — requires: isAuthenticated()",
                "Returns the current user's permission codes.",
                q("query {\n  myPermissions {\n    userId\n    permissions\n  }\n}")));

        // ── Incidents ─────────────────────────────
        ex.put("[incidents] incidents (admin list)", ex(
                "List all incidents — requires: dashboard.admin",
                "Paginated incident list. Supports filter and date range. Admin only.",
                q("query Incidents($filter: IncidentFilterInput, $page: PageInput) {\n  incidents(filter: $filter, page: $page) {\n    items {\n      id incidentNo title\n      status { id name }\n      priority { id name }\n      createdBy { fullName }\n      assignedTo { fullName }\n      createdAt updatedAt\n    }\n    page size totalElements totalPages hasNext\n  }\n}",
                        vars("filter", vars("statusId", "status-open"),
                                K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[incidents] myIncidents", ex(
                "List my submitted incidents — requires: authentication",
                "Returns incidents created by the currently logged-in user.",
                q("query {\n  myIncidents(page: { page: 0, size: 20 }) {\n    items {\n      id incidentNo title\n      status { name }\n      createdAt updatedAt\n    }\n    totalElements hasNext\n  }\n}")));

        ex.put("[incidents] deptIncidents", ex(
                "List department incidents — requires: dashboard.admin or dashboard.agent",
                "Returns incidents belonging to the caller's department.",
                q("query {\n  deptIncidents(page: { page: 0, size: 20 }) {\n    items {\n      id incidentNo title\n      status { name }\n      assignedTo { fullName }\n      createdAt\n    }\n    totalElements hasNext\n  }\n}")));

        ex.put("[incidents] assignedIncidents", ex(
                "List incidents assigned to me — requires: dashboard.admin or dashboard.agent",
                "Returns incidents where the current agent is the assignee.",
                q("query {\n  assignedIncidents(page: { page: 0, size: 20 }) {\n    items {\n      id incidentNo title\n      status { name }\n      createdAt\n    }\n    totalElements\n  }\n}")));

        ex.put("[incidents] searchIncidents", ex(
                "Full-text search — requires: authentication",
                "Searches incident title and description across all incidents visible to the caller.",
                q("query SearchIncidents($query: String!, $page: PageInput) {\n  searchIncidents(query: $query, page: $page) {\n    items {\n      id incidentNo title status { name } createdAt\n    }\n    totalElements\n  }\n}",
                        vars(K_QUERY, "printer not working", K_PAGE, vars(K_PAGE, 0, K_SIZE, 10)))));

        ex.put("[incidents] incident (by ID)", ex(
                "Get single incident — requires: authentication",
                "Returns full incident details including SLA state.",
                q("query GetIncident($id: ID!) {\n  incident(id: $id) {\n    id incidentNo title description\n    status { id name }\n    priority { id name }\n    incidentTopic { id name }\n    location { id name }\n    createdBy { id fullName }\n    assignedTo { agentId fullName email }\n    read statusReason\n    resolvedAt closedAt createdAt updatedAt\n    sla {\n      responseStatus resolutionStatus\n      responseDueAt resolutionDueAt paused\n    }\n    attachments { id originalName url }\n  }\n}",
                        vars(K_ID, INCIDENT_UUID))));

        ex.put("[incidents] incidentHistory", ex(
                "Get incident activity history — requires: authentication",
                "Returns the audit trail of changes made to an incident.",
                q("query IncidentHistory($id: ID!, $page: PageInput) {\n  incidentHistory(id: $id, page: $page) {\n    items {\n      id actorName action description createdAt\n    }\n    totalElements\n  }\n}",
                        vars(K_ID, INCIDENT_UUID, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[incidents] createIncident", ex(
                "Create an incident — requires: incident.create",
                "Creates a new incident. attachments is optional.",
                q("mutation CreateIncident($input: CreateIncidentInput!) {\n  createIncident(input: $input) {\n    id incidentNo title status { name } createdAt\n  }\n}",
                        vars(K_INPUT, vars(
                                "title", "Projector not working in Room 3",
                                K_DESCRIPTION, "The projector in meeting room 3 will not turn on.",
                                "incidentTypeId", TOPIC_UUID,
                                "locationId", LOCATION_UUID,
                                K_SEVERITY_ID, SEVERITY_UUID)))));

        ex.put("[incidents] updateIncidentStatus", ex(
                "Update incident status — requires: incident.status.change",
                "Transitions an incident to a new lifecycle status.",
                q("mutation UpdateStatus($id: ID!, $input: UpdateIncidentStatusInput!) {\n  updateIncidentStatus(id: $id, input: $input) {\n    id incidentNo status { name } statusReason updatedAt\n  }\n}",
                        vars(K_ID, INCIDENT_UUID, K_INPUT, vars("statusId", "status-resolved", "reason", "Issue resolved by replacing the projector bulb.")))));

        ex.put("[incidents] updateIncidentSeverity", ex(
                "Update incident severity — requires: incident.severity.change",
                null,
                q("mutation UpdateSeverity($id: ID!, $input: UpdateIncidentSeverityInput!) {\n  updateIncidentSeverity(id: $id, input: $input) {\n    id incidentNo priority { name } updatedAt\n  }\n}",
                        vars(K_ID, INCIDENT_UUID, K_INPUT, vars(K_SEVERITY_ID, SEVERITY_UUID)))));

        ex.put("[incidents] assignIncident", ex(
                "Assign incident to agent — requires: incident.assign",
                null,
                q("mutation AssignIncident($id: ID!, $input: AssignIncidentInput!) {\n  assignIncident(id: $id, input: $input) {\n    id incidentNo assignedTo { fullName email } updatedAt\n  }\n}",
                        vars(K_ID, INCIDENT_UUID, K_INPUT, vars(K_AGENT_ID, AGENT_UUID)))));

        // ── Messages ──────────────────────────────
        ex.put("[messages] list", ex(
                "List messages for an incident — requires: authentication",
                "Returns paginated messages for the given incident.",
                q("query Messages($incidentId: ID!, $page: PageInput) {\n  messages(incidentId: $incidentId, page: $page) {\n    items {\n      id content\n      sender { userId fullName }\n      attachments { id originalName url }\n      createdAt\n    }\n    totalElements hasNext\n  }\n}",
                        vars(K_INCIDENT_ID, INCIDENT_UUID, K_PAGE, vars(K_PAGE, 0, K_SIZE, 30)))));

        ex.put("[messages] sendMessage", ex(
                "Send a message — requires: authentication",
                "Sends a text or file-only message to an incident thread.",
                q("mutation SendMessage($incidentId: ID!, $input: SendMessageInput!) {\n  sendMessage(incidentId: $incidentId, input: $input) {\n    id content sender { fullName } createdAt\n  }\n}",
                        vars(K_INCIDENT_ID, INCIDENT_UUID, K_INPUT, vars("content", "We are looking into this and will update you shortly.")))));

        ex.put("[messages] deleteMessage", ex(
                "Delete a message — requires: authentication",
                "Deletes the specified message if the caller owns it.",
                q("mutation DeleteMessage($incidentId: ID!, $messageId: ID!) {\n  deleteMessage(incidentId: $incidentId, messageId: $messageId)\n}",
                        vars(K_INCIDENT_ID, INCIDENT_UUID, "messageId", "message-uuid-here"))));

        ex.put("[messages] generatePresignedUrl", ex(
                "Generate message upload URL — requires: authentication",
                "Returns a pre-signed S3 URL for uploading a message attachment.",
                q("mutation MessagePresignedUrl($incidentId: ID!, $input: PresignedUrlInput!) {\n  generateMessagePresignedUrl(incidentId: $incidentId, input: $input) {\n    uploadUrl fileKey expiresInSeconds\n  }\n}",
                        vars(K_INCIDENT_ID, INCIDENT_UUID,
                                K_INPUT, vars("fileName", "screenshot.png", "contentType", "image/png", "fileSize", 204800L)))));

        // ── Users ─────────────────────────────────
        ex.put("[users] list", ex(
                "List users — requires: rbac.role.read",
                "Paginated list of users with their role and location.",
                q("query Users($query: String, $roleCode: String, $page: PageInput) {\n  users(query: $query, roleCode: $roleCode, page: $page) {\n    items {\n      userId email fullName roleCode roleName status officeLocation\n    }\n    totalElements hasNext\n  }\n}",
                        vars(K_QUERY, null, K_ROLE_CODE, ROLE_AGENT, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[users] updateUserRole", ex(
                "Assign a role to a user — requires: rbac.user.role.update",
                null,
                q("mutation UpdateUserRole($userId: ID!, $input: UpdateUserRoleInput!) {\n  updateUserRole(userId: $userId, input: $input) {\n    userId email roleCode roleName\n  }\n}",
                        vars(K_USER_ID, "user-uuid-here", K_INPUT, vars(K_ROLE_CODE, ROLE_AGENT)))));

        ex.put("[users] updateUserStatus", ex(
                "Activate or deactivate a user — requires: authentication",
                null,
                q("mutation UpdateUserStatus($userId: ID!, $input: UpdateUserStatusInput!) {\n  updateUserStatus(userId: $userId, input: $input) {\n    userId email status\n  }\n}",
                        vars(K_USER_ID, "user-uuid-here", K_INPUT, vars(K_STATUS, true)))));

        // ── Roles & Permissions ───────────────────
        ex.put("[roles] list", ex(
                "List roles — requires: rbac.role.read",
                null,
                q("query Roles($query: String, $page: PageInput) {\n  roles(query: $query, page: $page) {\n    items {\n      id roleCode name description systemDefined\n      permissions { code name }\n    }\n    totalElements\n  }\n}",
                        vars(K_QUERY, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[roles] permissionCatalog", ex(
                "Get all available permissions — requires: rbac.permission.read",
                "Returns every permission grouped by domain.",
                q("query {\n  permissionCatalog {\n    incidentPermissions { code name description }\n    agentAndGroupPermissions { code name description }\n    settingsPermissions { code name description }\n    userPermissions { code name description }\n    reportPermissions { code name description }\n  }\n}")));

        ex.put("[roles] createRole", ex(
                "Create a custom role — requires: ADMIN or SUPER_ADMIN + rbac.role.update",
                null,
                q("mutation CreateRole($input: CreateRoleInput!) {\n  createRole(input: $input) {\n    id roleCode name\n    permissions { code name }\n  }\n}",
                        vars(K_INPUT, vars(
                                K_NAME, "Incident Viewer",
                                K_DESCRIPTION, "Read-only access to incidents.",
                                "permissionCodes", List.of("incident.view", "dashboard.agent"))))));

        ex.put("[roles] bulkAssignRole", ex(
                "Assign a role to multiple users — requires: ADMIN or SUPER_ADMIN + rbac.user.role.update",
                null,
                q("mutation BulkAssign($roleCode: String!, $input: BulkAssignRoleInput!) {\n  bulkAssignRole(roleCode: $roleCode, input: $input) {\n    roleCode updatedCount updatedUserIds\n  }\n}",
                        vars(K_ROLE_CODE, ROLE_AGENT, K_INPUT, vars("userIds", List.of("user-uuid-1", "user-uuid-2"))))));

        // ── Departments ───────────────────────────
        ex.put("[departments] list", ex(
                "List departments — requires: department.read",
                null,
                q("query Departments($query: String, $status: Boolean, $page: PageInput) {\n  departments(query: $query, status: $status, page: $page) {\n    items { id name description status categoryCount }\n    totalElements hasNext\n  }\n}",
                        vars(K_QUERY, null, K_STATUS, true, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[departments] getById", ex(
                "Get department by ID — requires: department.read",
                null,
                q("query GetDepartment($id: ID!) {\n  department(id: $id) {\n    id name description status categoryCount\n  }\n}",
                        vars(K_ID, DEPT_UUID))));

        ex.put("[departments] departmentCategories", ex(
                "List categories in a department — requires: department.read",
                null,
                q("query DeptCategories($departmentId: ID!) {\n  departmentCategories(departmentId: $departmentId) {\n    id name status\n  }\n}",
                        vars(K_DEPARTMENT_ID, DEPT_UUID))));

        ex.put("[departments] createDepartment", ex(
                "Create a department — requires: department.create",
                null,
                q("mutation CreateDept($input: DepartmentInput!) {\n  createDepartment(input: $input) {\n    id name description status\n  }\n}",
                        vars(K_INPUT, vars(K_NAME, "IT Support", K_DESCRIPTION, "Handles all IT-related incidents.")))));

        ex.put("[departments] updateDepartment", ex(
                "Update a department — requires: department.update",
                null,
                q("mutation UpdateDept($id: ID!, $input: DepartmentInput!) {\n  updateDepartment(id: $id, input: $input) {\n    id name description\n  }\n}",
                        vars(K_ID, DEPT_UUID, K_INPUT, vars(K_NAME, "IT & Infrastructure", K_DESCRIPTION, "Updated description.")))));

        ex.put("[departments] updateStatus", ex(
                "Activate / deactivate department — requires: department.delete",
                null,
                q("mutation UpdateDeptStatus($id: ID!, $input: UpdateDepartmentStatusInput!) {\n  updateDepartmentStatus(id: $id, input: $input) {\n    id name status\n  }\n}",
                        vars(K_ID, DEPT_UUID, K_INPUT, vars(K_STATUS, false)))));

        ex.put("[departments] linkCategory", ex(
                "Link a category to a department — requires: department.update",
                null,
                q("mutation LinkCategory($departmentId: ID!, $categoryId: ID!) {\n  linkCategoryToDepartment(departmentId: $departmentId, categoryId: $categoryId) {\n    id name department { id name }\n  }\n}",
                        vars(K_DEPARTMENT_ID, DEPT_UUID, K_CATEGORY_ID, CAT_UUID))));

        ex.put("[departments] unlinkCategory", ex(
                "Unlink a category from a department — requires: department.update",
                null,
                q("mutation UnlinkCategory($departmentId: ID!, $categoryId: ID!) {\n  unlinkCategoryFromDepartment(departmentId: $departmentId, categoryId: $categoryId) {\n    id name\n  }\n}",
                        vars(K_DEPARTMENT_ID, DEPT_UUID, K_CATEGORY_ID, CAT_UUID))));

        // ── Locations ─────────────────────────────
        ex.put("[locations] list", ex(
                "List locations — open to authenticated users",
                null,
                q("query Locations($query: String, $status: Boolean, $page: PageInput) {\n  locations(query: $query, status: $status, page: $page) {\n    items { id name description status updatedAt }\n    totalElements hasNext\n  }\n}",
                        vars(K_QUERY, null, K_STATUS, true, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[locations] getById", ex(
                "Get location by ID — open to authenticated users",
                null,
                q("query GetLocation($id: ID!) {\n  location(id: $id) {\n    id name description status updatedAt\n  }\n}",
                        vars(K_ID, LOCATION_UUID))));

        ex.put("[locations] createLocation", ex(
                "Create a location — requires: location.create",
                null,
                q("mutation CreateLocation($input: CreateLocationInput!) {\n  createLocation(input: $input) {\n    id name description status\n  }\n}",
                        vars(K_INPUT, vars(K_NAME, "Head Office - Accra", K_DESCRIPTION, "Main office in Accra.")))));

        ex.put("[locations] updateLocation", ex(
                "Update a location — requires: location.update",
                null,
                q("mutation UpdateLocation($id: ID!, $input: UpdateLocationInput!) {\n  updateLocation(id: $id, input: $input) {\n    id name description\n  }\n}",
                        vars(K_ID, LOCATION_UUID, K_INPUT, vars(K_NAME, "Head Office - Accra (Updated)")))));

        ex.put("[locations] updateStatus", ex(
                "Activate / deactivate location — requires: location.update",
                null,
                q("mutation UpdateLocationStatus($id: ID!, $input: UpdateLocationStatusInput!) {\n  updateLocationStatus(id: $id, input: $input) {\n    id name status\n  }\n}",
                        vars(K_ID, LOCATION_UUID, K_INPUT, vars(K_STATUS, false)))));

        // ── Agents ────────────────────────────────
        ex.put("[agents] list (active)", ex(
                "List active agents — requires: agent.read",
                "Returns agents with status=true (available).",
                q("query Agents($departmentId: String, $query: String, $page: PageInput) {\n  agents(departmentId: $departmentId, query: $query, page: $page) {\n    items {\n      agentId userId fullName email officeLocation status\n    }\n    totalElements\n  }\n}",
                        vars(K_DEPARTMENT_ID, null, K_QUERY, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[agents] allAgents", ex(
                "List all agents (all statuses) — requires: agent.read",
                null,
                q("query AllAgents($page: PageInput) {\n  allAgents(page: $page) {\n    items {\n      agentId userId fullName email status updatedAt\n    }\n    totalElements\n  }\n}",
                        vars(K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[agents] myAgentStatus", ex(
                "Get my agent availability — requires: agent.availability.update",
                null,
                q("query {\n  myAgentStatus {\n    agentId userId fullName status updatedAt\n  }\n}")));

        ex.put("[agents] updateMyStatus", ex(
                "Update my availability — requires: agent.availability.update",
                null,
                q("mutation UpdateMyStatus($input: UpdateAvailabilityInput!) {\n  updateMyAgentStatus(input: $input) {\n    agentId status updatedAt\n  }\n}",
                        vars(K_INPUT, vars("available", true)))));

        ex.put("[agents] updateAgentStatus", ex(
                "Update any agent's availability — requires: agent.availability.update.any",
                null,
                q("mutation UpdateAgentStatus($agentId: ID!, $input: UpdateAvailabilityInput!) {\n  updateAgentStatus(agentId: $agentId, input: $input) {\n    agentId status updatedAt\n  }\n}",
                        vars(K_AGENT_ID, AGENT_UUID, K_INPUT, vars("available", false)))));

        // ── Agent Groups ──────────────────────────
        ex.put("[agentGroups] list", ex(
                "List agent groups — requires: agent-group.read",
                null,
                q("query AgentGroups($query: String, $departmentId: String, $page: PageInput) {\n  agentGroups(query: $query, departmentId: $departmentId, page: $page) {\n    items {\n      id name department { name } status memberCount\n      topics { id name }\n    }\n    totalElements\n  }\n}",
                        vars(K_QUERY, null, K_DEPARTMENT_ID, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[agentGroups] getById", ex(
                "Get agent group detail — requires: agent-group.read",
                null,
                q("query GetAgentGroup($id: ID!) {\n  agentGroup(id: $id) {\n    id name description department { name } status memberCount\n    topics { id name }\n  }\n}",
                        vars(K_ID, GROUP_UUID))));

        ex.put("[agentGroups] members", ex(
                "List members of a group — requires: agent-group.read",
                null,
                q("query AgentGroupMembers($id: ID!) {\n  agentGroupMembers(id: $id) {\n    agentId userId fullName status locationName\n  }\n}",
                        vars(K_ID, GROUP_UUID))));

        ex.put("[agentGroups] byCategory", ex(
                "List groups handling a category — requires: agent-group.read",
                null,
                q("query GroupsByCategory($categoryId: ID!) {\n  agentGroupsByCategory(categoryId: $categoryId) {\n    id name\n  }\n}",
                        vars(K_CATEGORY_ID, CAT_UUID))));

        ex.put("[agentGroups] create", ex(
                "Create an agent group — requires: agent-group.create",
                null,
                q("mutation CreateAgentGroup($input: AgentGroupInput!) {\n  createAgentGroup(input: $input) {\n    id name department { name } status memberCount\n  }\n}",
                        vars(K_INPUT, vars(
                                K_NAME, "IT Level 1",
                                K_DESCRIPTION, "First-line IT support group.",
                                K_DEPARTMENT_ID, DEPT_UUID,
                                "agentIds", List.of("agent-uuid-1", "agent-uuid-2"),
                                "topicIds", List.of(TOPIC_UUID))))));

        ex.put("[agentGroups] addMember", ex(
                "Add a member to a group — requires: agent-group.update",
                null,
                q("mutation AddMember($id: ID!, $input: AddAgentGroupMemberInput!) {\n  addAgentGroupMember(id: $id, input: $input) {\n    agentId userId fullName status\n  }\n}",
                        vars(K_ID, GROUP_UUID, K_INPUT, vars(K_AGENT_ID, AGENT_UUID)))));

        // ── Incident Categories ───────────────────
        ex.put("[categories] list (active)", ex(
                "List active incident categories — open to authenticated users",
                null,
                q("query IncidentCategories($query: String, $page: PageInput) {\n  incidentCategories(status: \"active\", query: $query, page: $page) {\n    items { id name description department { name } status }\n    totalElements\n  }\n}",
                        vars(K_QUERY, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[categories] allCategories (admin)", ex(
                "List all categories — requires: ADMIN or SUPER_ADMIN",
                "Includes both active and inactive categories.",
                q("query AllCategories($state: String, $page: PageInput) {\n  allIncidentCategories(state: $state, page: $page) {\n    items { id name status department { name } updatedAt }\n    totalElements\n  }\n}",
                        vars("state", "all", K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[categories] topics", ex(
                "List topics in a category — open to authenticated users",
                null,
                q("query CategoryTopics($categoryId: ID!, $status: String) {\n  categoryTopics(categoryId: $categoryId, status: $status) {\n    id name description visibleToGroup status\n  }\n}",
                        vars(K_CATEGORY_ID, CAT_UUID, K_STATUS, K_ACTIVE))));

        ex.put("[categories] create", ex(
                "Create an incident category — requires: incident-category.create",
                null,
                q("mutation CreateCategory($input: IncidentCategoryInput!) {\n  createIncidentCategory(input: $input) {\n    id name department { name } status\n  }\n}",
                        vars(K_INPUT, vars(K_NAME, "Facilities", K_DESCRIPTION, "Facilities and maintenance issues.", K_DEPARTMENT_ID, DEPT_UUID)))));

        ex.put("[categories] createTopic", ex(
                "Create a topic under a category — requires: incident-type.create",
                null,
                q("mutation CreateTopic($categoryId: ID!, $input: CreateTopicInput!) {\n  createCategoryTopic(categoryId: $categoryId, input: $input) {\n    id name description visibleToGroup status\n  }\n}",
                        vars(K_CATEGORY_ID, CAT_UUID,
                                K_INPUT, vars(K_NAME, "Projector Issue", K_DESCRIPTION, "Projector malfunction or unavailability.", "agentGroupId", GROUP_UUID, "visibleToGroup", true)))));

        ex.put("[categories] updateTopic", ex(
                "Update a topic under a category — requires: incident-type.update",
                null,
                q("mutation UpdateTopic($categoryId: ID!, $topicId: ID!, $input: UpdateTopicInput!) {\n  updateCategoryTopic(categoryId: $categoryId, topicId: $topicId, input: $input) {\n    id name description visibleToGroup\n  }\n}",
                        vars(K_CATEGORY_ID, CAT_UUID, "topicId", TOPIC_UUID,
                                K_INPUT, vars(K_NAME, "Projector & Display Issue")))));

        ex.put("[categories] deleteTopic", ex(
                "Delete a topic from a category — requires: incident-type.delete",
                "Deletes the topic if no incidents reference it.",
                q("mutation DeleteTopic($categoryId: ID!, $topicId: ID!) {\n  deleteCategoryTopic(categoryId: $categoryId, topicId: $topicId)\n}",
                        vars(K_CATEGORY_ID, CAT_UUID, "topicId", TOPIC_UUID))));

        // ── Incident Topics (global) ───────────────
        ex.put("[topics] list", ex(
                "List incident topics (global) — open to authenticated users",
                "Paginated, filterable list across all categories.",
                q("query IncidentTopics($categoryId: String, $departmentId: String, $status: String, $query: String, $page: PageInput) {\n  incidentTopics(categoryId: $categoryId, departmentId: $departmentId, status: $status, query: $query, page: $page) {\n    items {\n      id name description visibleToGroup status\n      category { id name }\n      agentGroup { id name }\n    }\n    totalElements hasNext\n  }\n}",
                        vars(K_CATEGORY_ID, null, K_DEPARTMENT_ID, null, K_STATUS, K_ACTIVE, K_QUERY, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[topics] updateById", ex(
                "Update topic by ID (global) — requires: incident-type.update",
                null,
                q("mutation UpdateTopicById($id: ID!, $input: UpdateTopicInput!) {\n  updateTopicById(id: $id, input: $input) {\n    id name description visibleToGroup\n  }\n}",
                        vars(K_ID, TOPIC_UUID, K_INPUT, vars(K_NAME, "Network Connectivity", "visibleToGroup", false)))));

        ex.put("[topics] updateStatus", ex(
                "Toggle topic active/inactive — requires: incident-type.delete",
                null,
                q("mutation UpdateTopicStatus($id: ID!, $input: UpdateTopicStatusInput!) {\n  updateTopicStatus(id: $id, input: $input) {\n    id name status\n  }\n}",
                        vars(K_ID, TOPIC_UUID, K_INPUT, vars(K_STATUS, false)))));

        // ── FAQs ──────────────────────────────────
        ex.put("[faqs] list", ex(
                "List FAQs — requires: faq.read",
                "Pass active=true/false to filter, or omit for all.",
                q("query Faqs($active: Boolean, $page: PageInput) {\n  faqs(active: $active, page: $page) {\n    items { id question answer active createdAt }\n    totalElements\n  }\n}",
                        vars(K_ACTIVE, true, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[faqs] getById", ex(
                "Get FAQ by ID — requires: faq.read",
                null,
                q("query GetFaq($id: ID!) {\n  faq(id: $id) {\n    id question answer active createdAt updatedAt\n  }\n}",
                        vars(K_ID, FAQ_UUID))));

        ex.put("[faqs] create", ex(
                "Create a FAQ — requires: faq.create",
                null,
                q("mutation CreateFaq($input: CreateFaqInput!) {\n  createFaq(input: $input) {\n    id question answer active createdAt\n  }\n}",
                        vars(K_INPUT, vars("question", "How do I reset my password?", "answer", "Contact the IT helpdesk to request a password reset.")))));

        ex.put("[faqs] toggleActive", ex(
                "Toggle FAQ visibility — requires: faq.update",
                null,
                q("mutation ToggleFaq($id: ID!, $active: Boolean!) {\n  toggleFaqActive(id: $id, active: $active) {\n    id question active\n  }\n}",
                        vars(K_ID, FAQ_UUID, K_ACTIVE, false))));

        ex.put("[faqs] delete", ex(
                "Delete a FAQ — requires: faq.delete",
                null,
                q("mutation DeleteFaq($id: ID!) {\n  deleteFaq(id: $id)\n}",
                        vars(K_ID, FAQ_UUID))));

        // ── Severities ────────────────────────────
        ex.put("[severities] list", ex(
                "List all severities — open to authenticated users",
                null,
                q("query {\n  severities {\n    id name description status\n    responseTimeMinutes resolutionTimeMinutes\n  }\n}")));

        ex.put("[severities] getById", ex(
                "Get severity by ID — open to authenticated users",
                null,
                q("query GetSeverity($id: ID!) {\n  severity(id: $id) {\n    id name description status\n    responseTimeMinutes resolutionTimeMinutes\n    responseTimeSeconds resolutionTimeSeconds\n  }\n}",
                        vars(K_ID, SEVERITY_UUID))));

        ex.put("[severities] create", ex(
                "Create a severity — requires: severity.create",
                null,
                q("mutation CreateSeverity($input: SeverityInput!) {\n  createSeverity(input: $input) {\n    id name description status\n  }\n}",
                        vars(K_INPUT, vars(K_NAME, "Critical", K_DESCRIPTION, "Service-down incidents requiring immediate response.")))));

        ex.put("[severities] updateSla", ex(
                "Update severity SLA thresholds — requires: severity.update",
                "Times are in seconds.",
                q("mutation UpdateSla($id: ID!, $input: UpdateSeveritySlaInput!) {\n  updateSeveritySla(id: $id, input: $input) {\n    id name responseTimeMinutes resolutionTimeMinutes\n  }\n}",
                        vars(K_ID, SEVERITY_UUID, K_INPUT, vars("responseTimeSeconds", 3600L, "resolutionTimeSeconds", 28800L)))));

        ex.put("[severities] deactivate", ex(
                "Deactivate a severity — requires: severity.delete",
                null,
                q("mutation Deactivate($id: ID!) {\n  deactivateSeverity(id: $id) {\n    id name status\n  }\n}",
                        vars(K_ID, SEVERITY_UUID))));

        ex.put("[severities] delete", ex(
                "Delete a severity — requires: severity.delete",
                "Fails if any incidents reference this severity.",
                q("mutation DeleteSeverity($id: ID!) {\n  deleteSeverity(id: $id)\n}",
                        vars(K_ID, SEVERITY_UUID))));

        // ── Statuses ──────────────────────────────
        ex.put("[statuses] list", ex(
                "List all incident statuses — open to authenticated users",
                null,
                q("query {\n  statuses {\n    id name description\n  }\n}")));

        // ── Dashboard ─────────────────────────────
        ex.put("[dashboard] stats", ex(
                "Dashboard statistics — requires: dashboard.admin or dashboard.agent",
                "Returns incident counts by lifecycle state for the caller's scope.",
                q("query {\n  dashboardStats {\n    totalIncidents\n    openCount pendingCount inProgressCount\n    resolvedCount closedCount\n  }\n}")));

        ex.put("[dashboard] charts", ex(
                "Dashboard charts — requires: dashboard.admin or dashboard.agent",
                "Pass period='month' or 'week'. Defaults to 'month'.",
                q("query DashboardCharts($period: String) {\n  dashboardCharts(period: $period) {\n    byStatus { label count }\n    trends {\n      label\n      data { month count }\n    }\n  }\n}",
                        vars("period", "month"))));

        ex.put("[dashboard] slaReport", ex(
                "SLA compliance report — requires: dashboard.admin",
                "Pass optional date range and severity filter.",
                q("query SlaReport($from: Instant, $to: Instant, $severityId: String) {\n  slaReport(from: $from, to: $to, severityId: $severityId) {\n    trackedIncidents responseBreachCount resolutionBreachCount\n    averageResponseMinutes averageResolutionMinutes\n    bySeverity {\n      severityName trackedIncidents\n      responseBreachCount resolutionBreachCount\n    }\n  }\n}",
                        vars("from", null, "to", null, K_SEVERITY_ID, null))));

        // ── Notifications ─────────────────────────
        ex.put("[notifications] list", ex(
                "List my notifications — requires: authentication",
                null,
                q("query Notifications($page: PageInput) {\n  notifications(page: $page) {\n    items {\n      id type title message read incidentId createdAt\n    }\n    totalElements hasNext\n  }\n}",
                        vars(K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        ex.put("[notifications] unreadCount", ex(
                "Get unread notification count — requires: authentication",
                null,
                q("query {\n  unreadNotificationCount\n}")));

        ex.put("[notifications] markRead", ex(
                "Mark a notification as read — requires: authentication",
                null,
                q("mutation MarkRead($id: ID!) {\n  markNotificationRead(id: $id) {\n    id read\n  }\n}",
                        vars(K_ID, "notification-uuid-here"))));

        ex.put("[notifications] markAllRead", ex(
                "Mark all notifications as read — requires: authentication",
                null,
                q("mutation {\n  markAllNotificationsRead\n}")));

        // ── Activities ────────────────────────────
        ex.put("[activities] list", ex(
                "List activity logs — requires: authentication",
                "Omit incidentId to get all activities visible to the caller.",
                q("query Activities($incidentId: ID, $page: PageInput) {\n  activities(incidentId: $incidentId, page: $page) {\n    items {\n      id actorName action subjectType subjectNo description createdAt\n    }\n    totalElements hasNext\n  }\n}",
                        vars(K_INCIDENT_ID, null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 30)))));

        // ── Chatbot ───────────────────────────────
        ex.put("[chatbot] query", ex(
                "Submit a chatbot query — requires: chatbot.query",
                "Rate-limited to 30 requests per minute per user.",
                q("mutation ChatbotQuery($input: ChatbotQueryInput!) {\n  chatbotQuery(input: $input) {\n    answer confidence outcome\n  }\n}",
                        vars(K_INPUT, vars(K_QUERY, "How do I escalate an incident?")))));

        ex.put("[chatbot] interactions", ex(
                "List chatbot interaction logs — requires: chatbot.interactions.read",
                null,
                q("query ChatbotInteractions($userId: String, $outcome: String, $page: PageInput) {\n  chatbotInteractions(userId: $userId, outcome: $outcome, page: $page) {\n    items {\n      id userId query confidence outcome createdAt\n    }\n    totalElements\n  }\n}",
                        vars(K_USER_ID, null, "outcome", null, K_PAGE, vars(K_PAGE, 0, K_SIZE, 20)))));

        // ── System Config ─────────────────────────
        ex.put("[config] autoCloseConfig", ex(
                "Get auto-close configuration — requires: system.config.read",
                null,
                q("query {\n  autoCloseConfig {\n    durationSeconds\n  }\n}")));

        ex.put("[config] slaConfig", ex(
                "Get SLA at-risk threshold config — requires: system.config.read",
                null,
                q("query {\n  slaConfig {\n    atRiskPct\n  }\n}")));

        ex.put("[config] updateAutoClose", ex(
                "Update auto-close duration — requires: system.config.update",
                "Duration in seconds. E.g. 259200 = 3 days.",
                q("mutation UpdateAutoClose($input: UpdateAutoCloseConfigInput!) {\n  updateAutoCloseConfig(input: $input) {\n    durationSeconds\n  }\n}",
                        vars(K_INPUT, vars("durationSeconds", 259200)))));

        ex.put("[config] updateSlaConfig", ex(
                "Update SLA at-risk percentage — requires: system.config.update",
                "atRiskPct defines the % of the SLA threshold at which an at-risk alert fires.",
                q("mutation UpdateSla($input: UpdateSlaConfigInput!) {\n  updateSlaConfig(input: $input) {\n    atRiskPct\n  }\n}",
                        vars(K_INPUT, vars("atRiskPct", 20)))));

        // ── Media ─────────────────────────────────
        ex.put("[media] generatePresignedUrl", ex(
                "Generate a media upload URL — requires: incident.create",
                "Returns a pre-signed S3 URL valid for a short window. Upload directly to uploadUrl, then reference fileKey in attachments.",
                q("mutation GenerateMediaUrl($input: PresignedUrlInput!) {\n  generateMediaPresignedUrl(input: $input) {\n    uploadUrl fileKey expiresInSeconds\n  }\n}",
                        vars(K_INPUT, vars("fileName", "report.pdf", "contentType", "application/pdf", "fileSize", 512000L)))));

        return ex;
    }

    // ──────────────────────────────────────
    // Builder helpers
    // ──────────────────────────────────────

    private static Example ex(String summary, String description, Object value) {
        Example e = new Example().summary(summary).value(value);
        if (description != null) {
            e.setDescription(description);
        }
        return e;
    }

    private static Map<String, Object> q(String query) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(K_QUERY, query);
        return m;
    }

    private static Map<String, Object> q(String query, Map<String, Object> variables) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(K_QUERY, query);
        m.put(K_VARIABLES, variables);
        return m;
    }

    private static Map<String, Object> vars(Object... keysAndValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            m.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return m;
    }
}
