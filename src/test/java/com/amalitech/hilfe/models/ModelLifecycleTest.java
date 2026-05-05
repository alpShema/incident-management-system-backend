package com.amalitech.hilfe.models;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ModelLifecycleTest {

    private static void invokeProtected(Object target, String methodName) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        m.invoke(target);
    }

    @Test
    void user_onCreate_setsTimestamps() throws Exception {
        User user = new User();
        invokeProtected(user, "onCreate");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void user_onUpdate_updatesUpdatedAt() throws Exception {
        User user = new User();
        invokeProtected(user, "onCreate");
        var first = user.getUpdatedAt();
        invokeProtected(user, "onUpdate");
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(first);
    }

    @Test
    void admin_onCreate_setsTimestamps() throws Exception {
        Admin admin = new Admin();
        invokeProtected(admin, "onCreate");
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isNotNull();
    }

    @Test
    void admin_onUpdate_updatesUpdatedAt() throws Exception {
        Admin admin = new Admin();
        invokeProtected(admin, "onCreate");
        var first = admin.getUpdatedAt();
        invokeProtected(admin, "onUpdate");
        assertThat(admin.getUpdatedAt()).isAfterOrEqualTo(first);
    }

    @Test
    void agent_onCreate_setsTimestamps() throws Exception {
        Agent agent = new Agent();
        invokeProtected(agent, "onCreate");
        assertThat(agent.getCreatedAt()).isNotNull();
        assertThat(agent.getUpdatedAt()).isNotNull();
    }

    @Test
    void agent_onUpdate_updatesUpdatedAt() throws Exception {
        Agent agent = new Agent();
        invokeProtected(agent, "onCreate");
        invokeProtected(agent, "onUpdate");
        assertThat(agent.getUpdatedAt()).isNotNull();
    }

    @Test
    void agentGroup_onCreate_setsTimestamps() throws Exception {
        AgentGroup group = new AgentGroup();
        invokeProtected(group, "onCreate");
        assertThat(group.getCreatedAt()).isNotNull();
        assertThat(group.getUpdatedAt()).isNotNull();
    }

    @Test
    void agentGroup_onUpdate_updatesUpdatedAt() throws Exception {
        AgentGroup group = new AgentGroup();
        invokeProtected(group, "onCreate");
        invokeProtected(group, "onUpdate");
        assertThat(group.getUpdatedAt()).isNotNull();
    }

    @Test
    void incident_onCreate_setsTimestamps() throws Exception {
        Incident incident = new Incident();
        invokeProtected(incident, "onCreate");
        assertThat(incident.getCreatedAt()).isNotNull();
        assertThat(incident.getUpdatedAt()).isNotNull();
    }

    @Test
    void incident_onUpdate_updatesUpdatedAt() throws Exception {
        Incident incident = new Incident();
        invokeProtected(incident, "onCreate");
        invokeProtected(incident, "onUpdate");
        assertThat(incident.getUpdatedAt()).isNotNull();
    }

    @Test
    void incidentLog_onCreate_setsTimestamps() throws Exception {
        IncidentLog log = new IncidentLog();
        invokeProtected(log, "onCreate");
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getUpdatedAt()).isNotNull();
    }

    @Test
    void incidentLog_onUpdate_updatesUpdatedAt() throws Exception {
        IncidentLog log = new IncidentLog();
        invokeProtected(log, "onCreate");
        invokeProtected(log, "onUpdate");
        assertThat(log.getUpdatedAt()).isNotNull();
    }

    @Test
    void incidentReportLog_onCreate_setsCreatedAt() throws Exception {
        IncidentReportLog log = new IncidentReportLog();
        invokeProtected(log, "onCreate");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void message_onCreate_setsTimestamps() throws Exception {
        Message message = new Message();
        invokeProtected(message, "onCreate");
        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getUpdatedAt()).isNotNull();
    }

    @Test
    void message_onUpdate_updatesUpdatedAt() throws Exception {
        Message message = new Message();
        invokeProtected(message, "onCreate");
        invokeProtected(message, "onUpdate");
        assertThat(message.getUpdatedAt()).isNotNull();
    }
}
