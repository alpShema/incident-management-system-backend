package com.amalitech.hilfe.security.authorization;

import java.util.List;

public final class RbacPermissions {
    public static final String INCIDENT_CREATE = "incident.create";
    public static final String INCIDENT_READ_OWN = "incident.read.own";
    public static final String INCIDENT_READ_ASSIGNED = "incident.read.assigned";
    public static final String INCIDENT_ASSIGN = "incident.assign";
    public static final String INCIDENT_STATUS_CHANGE = "incident.status.change";
    public static final String INCIDENT_SEVERITY_CHANGE = "incident.severity.change";
    public static final String AGENT_CREATE = "agent.create";
    public static final String AGENT_READ = "agent.read";
    public static final String AGENT_UPDATE = "agent.update";
    public static final String AGENT_DELETE = "agent.delete";
    public static final String AGENT_GROUP_CREATE = "agent-group.create";
    public static final String AGENT_GROUP_UPDATE = "agent-group.update";
    public static final String AGENT_GROUP_DELETE = "agent-group.delete";
    public static final String STATUS_CREATE = "status.create";
    public static final String STATUS_UPDATE = "status.update";
    public static final String STATUS_DELETE = "status.delete";
    public static final String SEVERITY_CREATE = "severity.create";
    public static final String SEVERITY_UPDATE = "severity.update";
    public static final String SEVERITY_DELETE = "severity.delete";
    public static final String LOCATION_CREATE = "location.create";
    public static final String LOCATION_UPDATE = "location.update";
    public static final String LOCATION_DELETE = "location.delete";
    public static final String INCIDENT_TYPE_CREATE = "incident-type.create";
    public static final String INCIDENT_TYPE_UPDATE = "incident-type.update";
    public static final String INCIDENT_TYPE_DELETE = "incident-type.delete";
    public static final String INCIDENT_CATEGORY_CREATE = "incident-category.create";
    public static final String INCIDENT_CATEGORY_UPDATE = "incident-category.update";
    public static final String INCIDENT_CATEGORY_DELETE = "incident-category.delete";
    public static final String RBAC_ROLE_READ = "rbac.role.read";
    public static final String RBAC_ROLE_UPDATE = "rbac.role.update";
    public static final String RBAC_USER_ROLE_UPDATE = "rbac.user-role.update";
    public static final String RBAC_PERMISSION_READ = "rbac.permission.read";
    public static final String DASHBOARD_AGENT = "dashboard.agent";
    public static final String DASHBOARD_ADMIN = "dashboard.admin";
    public static final String SYSTEM_CONFIG_READ = "system.config.read";
    public static final String SYSTEM_CONFIG_UPDATE = "system.config.update";

    public static final List<String> CLIENT_DEFAULTS = List.of(
            INCIDENT_CREATE,
            INCIDENT_READ_OWN
    );

    public static final List<String> AGENT_DEFAULTS = List.of(
            INCIDENT_READ_ASSIGNED,
            INCIDENT_ASSIGN,
            INCIDENT_STATUS_CHANGE,
            INCIDENT_SEVERITY_CHANGE,
            INCIDENT_TYPE_CREATE,
            AGENT_READ,
            DASHBOARD_AGENT
    );

    public static final List<String> ADMIN_DEFAULTS = List.of(
            INCIDENT_READ_ASSIGNED,
            INCIDENT_ASSIGN,
            INCIDENT_STATUS_CHANGE,
            INCIDENT_SEVERITY_CHANGE,
            AGENT_CREATE,
            AGENT_READ,
            AGENT_UPDATE,
            AGENT_DELETE,
            AGENT_GROUP_CREATE,
            AGENT_GROUP_UPDATE,
            AGENT_GROUP_DELETE,
            STATUS_CREATE,
            STATUS_UPDATE,
            STATUS_DELETE,
            SEVERITY_CREATE,
            SEVERITY_UPDATE,
            SEVERITY_DELETE,
            LOCATION_CREATE,
            LOCATION_UPDATE,
            LOCATION_DELETE,
            INCIDENT_TYPE_CREATE,
            INCIDENT_TYPE_UPDATE,
            INCIDENT_TYPE_DELETE,
            RBAC_ROLE_READ,
            RBAC_USER_ROLE_UPDATE,
            RBAC_PERMISSION_READ,
            DASHBOARD_ADMIN,
            SYSTEM_CONFIG_READ,
            SYSTEM_CONFIG_UPDATE
    );

    public static final List<String> SUPER_ADMIN_DEFAULTS = List.of(
            INCIDENT_READ_ASSIGNED,
            INCIDENT_ASSIGN,
            INCIDENT_STATUS_CHANGE,
            INCIDENT_SEVERITY_CHANGE,
            AGENT_CREATE,
            AGENT_READ,
            AGENT_UPDATE,
            AGENT_DELETE,
            AGENT_GROUP_CREATE,
            AGENT_GROUP_UPDATE,
            AGENT_GROUP_DELETE,
            STATUS_CREATE,
            STATUS_UPDATE,
            STATUS_DELETE,
            SEVERITY_CREATE,
            SEVERITY_UPDATE,
            SEVERITY_DELETE,
            LOCATION_CREATE,
            LOCATION_UPDATE,
            LOCATION_DELETE,
            INCIDENT_TYPE_CREATE,
            INCIDENT_TYPE_UPDATE,
            INCIDENT_TYPE_DELETE,
            RBAC_ROLE_READ,
            RBAC_ROLE_UPDATE,
            RBAC_USER_ROLE_UPDATE,
            RBAC_PERMISSION_READ,
            DASHBOARD_ADMIN,
            SYSTEM_CONFIG_READ,
            SYSTEM_CONFIG_UPDATE
    );

    private RbacPermissions() {
    }
}
