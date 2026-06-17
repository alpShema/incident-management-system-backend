# HILFE Bot — Slack Integration Implementation Plan

**Feature:** HILFE Bot — Slack Application for Incident Reporting & Management  
**Branch Convention:** `feature/hilfe-bot-slack-integration`  
**Last Updated:** 2026-06-15  
**Author:** Engineering Team

---

## Table of Contents

1. [Overview & Architecture Decision](#1-overview--architecture-decision)
2. [Technology Stack Additions](#2-technology-stack-additions)
3. [Phase 0 — Slack App Registration & Configuration](#3-phase-0--slack-app-registration--configuration)
4. [Phase 1 — Database Schema](#4-phase-1--database-schema)
5. [Phase 2 — Slack OAuth 2.0 Account Connection](#5-phase-2--slack-oauth-20-account-connection)
6. [Phase 3 — Slack Event & Interaction Handler](#6-phase-3--slack-event--interaction-handler)
7. [Phase 4 — App Home (Block Kit UI)](#7-phase-4--app-home-block-kit-ui)
8. [Phase 5 — Incident Creation Modal](#8-phase-5--incident-creation-modal)
9. [Phase 6 — My Incidents View](#9-phase-6--my-incidents-view)
10. [Phase 7 — Slack Notifications](#10-phase-7--slack-notifications)
11. [Phase 8 — Notification Preferences](#11-phase-8--notification-preferences)
12. [Phase 9 — Security Controls](#12-phase-9--security-controls)
13. [Phase 10 — Frontend OAuth Pages](#13-phase-10--frontend-oauth-pages)
14. [Phase 11 — Audit Logging](#14-phase-11--audit-logging)
15. [Phase 12 — Testing](#15-phase-12--testing)
16. [Configuration & Secrets Reference](#16-configuration--secrets-reference)
17. [Dependency Map](#17-dependency-map)
18. [Rollout Checklist](#18-rollout-checklist)

---

## 1. Overview & Architecture Decision

### 1.1 Integration Approach

HILFE Bot will be implemented as a **module within the existing Spring Boot monolith**, not as a separate microservice. This decision is deliberate:

- Avoids cross-service HTTP calls to create incidents — the bot can call `IncidentService` directly
- Reuses the existing RBAC system, JWT infrastructure, notification event bus, activity logging, and exception handling without duplication
- Simplifies deployment and secrets management — one application, one deployment unit
- Consistent with the project's current single-tier architecture

The Slack integration is an inbound + outbound adapter around the existing domain layer. Slack events arrive via HTTP webhooks → handlers call existing services → Slack API is called to render responses.

### 1.2 High-Level Request Flows

#### Incident Creation via Slack

```
User submits Slack modal
  → POST /api/v1/slack/interactions  (Slack sends payload)
    → SlackInteractionController
      → SlackSignatureVerifier  (verify X-Slack-Signature)
        → SlackInteractionHandler
          → IncidentService.createIncident()  (existing service, reused)
            → NotificationEventPublisher (existing)
              → NotificationEventListener (existing)
                → SlackNotificationDeliveryService  (NEW — dispatches DM to relevant user)
          → SlackApiClient.postMessage()  (success confirmation to creator)
```

#### OAuth Connection Flow

```
/hilfe connect (Slack slash command)
  → POST /api/v1/slack/commands
    → SlackCommandController
      → generates state token (CSRF)
        → returns Slack ephemeral message with link to /oauth/slack/authorize
          → User clicks link, goes to HILFE OAuth page
            → User authorises → Slack redirects to /oauth/slack/callback?code=...&state=...
              → SlackOAuthController validates state, exchanges code for token
                → SlackAccountMappingService saves {hilfeUserId, slackUserId, slackAccessToken}
                  → SlackApiClient.publishHomeTab()  (refreshes app home)
```

### 1.3 Module Structure

All new code lives under `src/main/java/com/amalitech/hilfe/slack/`:

```
slack/
  api/                  # Slack API client & Block Kit builders
    SlackApiClient.java
    blocks/
      HomeTabBuilder.java
      IncidentModalBuilder.java
      IncidentListBuilder.java
  auth/                 # OAuth flow
    SlackOAuthController.java
    SlackOAuthService.java
    SlackStateTokenService.java
  commands/             # Slash commands
    SlackCommandController.java
    SlackCommandHandler.java
  interactions/         # Modal submissions, button clicks
    SlackInteractionController.java
    SlackInteractionHandler.java
  events/               # App Home opened, DM events
    SlackEventController.java
    SlackEventHandler.java
  notifications/        # Outbound Slack DMs
    SlackNotificationDeliveryService.java
    SlackNotificationPreferenceService.java
  security/             # Signature verification, rate limiting
    SlackSignatureVerifier.java
    SlackRateLimiter.java
  model/                # JPA entities (SlackAccountMapping, SlackNotificationPreference, SlackBotLog)
  repository/           # Spring Data repositories
  config/               # SlackConfig (properties binding)
  dto/                  # Slack payload DTOs (incoming)
```

---

## 2. Technology Stack Additions

### 2.1 Maven Dependencies

Add to `pom.xml`:

```xml
<!-- Slack Java SDK (Bolt for Java) -->
<dependency>
    <groupId>com.slack.api</groupId>
    <artifactId>bolt</artifactId>
    <version>1.43.0</version>
</dependency>
<dependency>
    <groupId>com.slack.api</groupId>
    <artifactId>slack-api-model</artifactId>
    <version>1.43.0</version>
</dependency>
<dependency>
    <groupId>com.slack.api</groupId>
    <artifactId>slack-api-client</artifactId>
    <version>1.43.0</version>
</dependency>

<!-- Spring Retry (for Slack API call retries) -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
</dependency>

<!-- Bucket4j (token bucket rate limiting) -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

> **Note:** The Slack Bolt SDK provides high-level abstractions for slash commands, modals, events, and Block Kit. We will use the Bolt `App` bean with a custom servlet mapping to avoid conflicts with the existing Spring Security filter chain.

---

## 3. Phase 0 — Slack App Registration & Configuration

### 3.1 Slack App Setup (Manual — Slack API Dashboard)

**Steps:**

1. Go to https://api.slack.com/apps → Create New App → From Manifest
2. Set **App Name:** `HILFE Bot`
3. Configure **App Manifest** with the following:

```yaml
display_information:
  name: HILFE Bot
  description: Report and manage HILFE incidents directly from Slack
  background_color: "#1E293B"
features:
  app_home:
    home_tab_enabled: true
    messages_tab_enabled: false
  slash_commands:
    - command: /hilfe
      url: https://<hilfe-domain>/api/v1/slack/commands
      description: Manage HILFE incidents from Slack
      usage_hint: "connect | settings"
      should_escape: false
oauth_config:
  redirect_urls:
    - https://<hilfe-domain>/oauth/slack/callback
  scopes:
    bot:
      - app_mentions:read
      - chat:write
      - commands
      - im:write
      - users:read
      - users:read.email
settings:
  event_subscriptions:
    request_url: https://<hilfe-domain>/api/v1/slack/events
    bot_events:
      - app_home_opened
  interactivity:
    is_enabled: true
    request_url: https://<hilfe-domain>/api/v1/slack/interactions
  socket_mode_enabled: false
```

4. Install app to organisation workspace
5. Copy **Bot User OAuth Token** (`xoxb-...`) and **Signing Secret** to secrets manager

### 3.2 Application Configuration

Add to `application.yaml`:

```yaml
slack:
  bot-token: ${SLACK_BOT_TOKEN}          # xoxb-...
  signing-secret: ${SLACK_SIGNING_SECRET}
  client-id: ${SLACK_CLIENT_ID}
  client-secret: ${SLACK_CLIENT_SECRET}
  app-id: ${SLACK_APP_ID}
  redirect-uri: ${APP_BASE_URL}/oauth/slack/callback
  hilfe-base-url: ${APP_BASE_URL}
  rate-limit:
    commands-per-user-per-minute: 10
    interactions-per-user-per-minute: 20
```

Create `SlackProperties.java` bound to the `slack` prefix using `@ConfigurationProperties`.

---

## 4. Phase 1 — Database Schema

### 4.1 New Tables

**Migration:** `V52__create_slack_integration_tables.sql`

```sql
-- Maps HILFE users to their Slack user IDs and stores access tokens
CREATE TABLE slack_account_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slack_user_id   VARCHAR(50) NOT NULL,
    slack_team_id   VARCHAR(50) NOT NULL,
    -- Tokens encrypted at rest via application-layer encryption (AES-256)
    -- Never stored in plain text
    access_token_encrypted  TEXT NOT NULL,
    token_scope             TEXT,
    connected_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_slack_account_mappings_user       UNIQUE (user_id),
    CONSTRAINT uq_slack_account_mappings_slack_user UNIQUE (slack_user_id, slack_team_id)
);

CREATE INDEX idx_slack_account_mappings_slack_user_id ON slack_account_mappings(slack_user_id);

-- OAuth state tokens for CSRF protection (short-lived, one-time use)
CREATE TABLE slack_oauth_state_tokens (
    token           VARCHAR(128) PRIMARY KEY,
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    slack_user_id   VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    used            BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_slack_oauth_state_tokens_expires ON slack_oauth_state_tokens(expires_at);

-- Per-user notification preferences for Slack notifications
CREATE TABLE slack_notification_preferences (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    notify_on_assignment    BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_status_change BOOLEAN NOT NULL DEFAULT TRUE,
    notify_on_new_message   BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_slack_notification_prefs_user UNIQUE (user_id)
);

-- Audit log for all Slack bot interactions
CREATE TABLE slack_bot_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slack_user_id   VARCHAR(50),
    hilfe_user_id   UUID REFERENCES users(id) ON DELETE SET NULL,
    action          VARCHAR(100) NOT NULL,
    payload         JSONB,
    success         BOOLEAN NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_slack_bot_logs_created_at     ON slack_bot_logs(created_at DESC);
CREATE INDEX idx_slack_bot_logs_hilfe_user_id  ON slack_bot_logs(hilfe_user_id);
CREATE INDEX idx_slack_bot_logs_action         ON slack_bot_logs(action);

-- Scheduled cleanup: expire used/old state tokens
-- Handled by AutoCloseScheduler or a dedicated SlackMaintenanceScheduler
```

### 4.2 JPA Entities

**`SlackAccountMapping.java`**

```java
@Entity
@Table(name = "slack_account_mappings")
public class SlackAccountMapping {
    @Id @GeneratedValue private UUID id;
    @Column(name = "user_id", nullable = false)  private UUID userId;
    @Column(name = "slack_user_id", nullable = false) private String slackUserId;
    @Column(name = "slack_team_id", nullable = false) private String slackTeamId;
    @Column(name = "access_token_encrypted", nullable = false) private String accessTokenEncrypted;
    @Column(name = "token_scope") private String tokenScope;
    @Column(name = "connected_at") private Instant connectedAt;
    @Column(name = "updated_at") private Instant updatedAt;

    @PrePersist void prePersist() { connectedAt = updatedAt = Instant.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = Instant.now(); }
}
```

**`SlackOAuthStateToken.java`**

```java
@Entity
@Table(name = "slack_oauth_state_tokens")
public class SlackOAuthStateToken {
    @Id private String token;
    @Column(name = "user_id")       private UUID userId;
    @Column(name = "slack_user_id") private String slackUserId;
    @Column(name = "created_at")    private Instant createdAt;
    @Column(name = "expires_at")    private Instant expiresAt;
    @Column(name = "used")          private boolean used;
}
```

**`SlackNotificationPreference.java`**

```java
@Entity
@Table(name = "slack_notification_preferences")
public class SlackNotificationPreference {
    @Id @GeneratedValue private UUID id;
    @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
    @Column(name = "notify_on_assignment")    private boolean notifyOnAssignment    = true;
    @Column(name = "notify_on_status_change") private boolean notifyOnStatusChange  = true;
    @Column(name = "notify_on_new_message")   private boolean notifyOnNewMessage    = true;
    @Column(name = "updated_at")              private Instant updatedAt;

    @PrePersist @PreUpdate void preUpdate() { updatedAt = Instant.now(); }
}
```

**`SlackBotLog.java`**

```java
@Entity
@Table(name = "slack_bot_logs")
public class SlackBotLog {
    @Id @GeneratedValue private UUID id;
    @Column(name = "slack_user_id")  private String slackUserId;
    @Column(name = "hilfe_user_id")  private UUID hilfeUserId;
    @Column(name = "action", nullable = false) private String action;
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb") private Map<String, Object> payload;
    @Column(name = "success", nullable = false) private boolean success;
    @Column(name = "error_message")  private String errorMessage;
    @Column(name = "created_at")     private Instant createdAt;

    @PrePersist void prePersist() { createdAt = Instant.now(); }
}
```

---

## 5. Phase 2 — Slack OAuth 2.0 Account Connection

### 5.1 Token Encryption

Before any OAuth tokens are stored, implement application-layer AES-256 encryption.

**`SlackTokenEncryptionService.java`**

- Inject encryption key from `SLACK_TOKEN_ENCRYPTION_KEY` environment variable (32-byte hex)
- Use `javax.crypto.Cipher` with `AES/GCM/NoPadding`
- Methods: `encrypt(String plaintext): String` and `decrypt(String ciphertext): String`
- **Never log plaintext tokens**; log only a redacted prefix (e.g., `xoxb-****`)

### 5.2 OAuth State Token Service

**`SlackStateTokenService.java`**

```java
@Service
@Transactional
public class SlackStateTokenService {

    // Generates a cryptographically random 128-character hex token
    public String generateStateToken(String slackUserId) {
        String token = generateSecureToken();
        SlackOAuthStateToken stateToken = new SlackOAuthStateToken();
        stateToken.setToken(token);
        stateToken.setSlackUserId(slackUserId);
        stateToken.setCreatedAt(Instant.now());
        stateToken.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        stateToken.setUsed(false);
        repository.save(stateToken);
        return token;
    }

    // Validates state token: must exist, not expired, not already used
    // Marks as used on successful validation (one-time use)
    public SlackOAuthStateToken validateAndConsume(String token) { ... }

    // Scheduled: cleans up expired/used tokens older than 1 hour
    @Scheduled(fixedDelay = 3_600_000)
    public void cleanExpiredTokens() { ... }
}
```

### 5.3 OAuth Flow

#### 5.3.1 Slash Command Handler — `/hilfe connect`

**`SlackCommandController.java`** — `POST /api/v1/slack/commands`

1. Verify `X-Slack-Signature` (see Phase 9)
2. Parse slash command payload
3. Route to `SlackCommandHandler`

**`SlackCommandHandler.java`** — handles `/hilfe` command

```
if subcommand == "connect":
    1. Check if slackUserId already has a mapping → respond with "already connected" ephemeral message
    2. Generate state token via SlackStateTokenService
    3. Build OAuth URL:
       https://slack.com/oauth/v2/authorize
         ?client_id={clientId}
         &scope=chat:write,im:write,users:read,users:read.email,app_mentions:read
         &redirect_uri={redirectUri}
         &state={stateToken}
    4. Respond with ephemeral Slack message containing a button linking to the OAuth URL
       (ephemeral = only visible to the requesting user)

if subcommand == "settings":
    → Open notification preferences modal (Phase 8)

if no subcommand:
    → Show help ephemeral message listing available subcommands
```

#### 5.3.2 OAuth Authorisation Page

**`SlackOAuthController.java`** — `GET /oauth/slack/authorize`

- This is the landing page the user is redirected to before Slack's OAuth dialog (optional intermediate page; can also go directly to Slack's authorize URL)
- Renders a frontend page (see Phase 10)

#### 5.3.3 OAuth Callback

**`SlackOAuthController.java`** — `GET /oauth/slack/callback`

```
1. Extract code and state from query parameters
2. Validate state token via SlackStateTokenService.validateAndConsume(state)
   → reject with 400 if invalid, expired, or already used (CSRF protection)
3. Exchange code for access token:
   POST https://slack.com/api/oauth.v2.access
     client_id, client_secret, code, redirect_uri
4. Extract slackUserId from response (authed_user.id)
5. Look up HILFE user by slackUserId (in case already linked) or from session
   → Require the user to be authenticated on HILFE (session cookie) at callback time
   → If no HILFE session, redirect to HILFE login with return URL
6. Encrypt the access token via SlackTokenEncryptionService
7. Upsert SlackAccountMapping {userId, slackUserId, slackTeamId, encryptedToken, scope}
8. Create default SlackNotificationPreference for user if not existing
9. Call SlackApiClient.publishHomeTab(slackUserId) to refresh app home
10. Log event: slackBotLogService.log(slackUserId, hilfeUserId, "ACCOUNT_CONNECTED", ...)
11. Redirect to /oauth/slack/success  (renders success page)
```

**Error cases:**
- `state` invalid/missing → redirect to `/oauth/slack/error?reason=invalid_state`
- Slack token exchange fails → redirect to `/oauth/slack/error?reason=token_exchange_failed`
- HILFE user not authenticated → redirect to login

#### 5.3.4 Disconnect

**`SlackAccountMappingService.java`** — `disconnect(UUID hilfeUserId)`

```
1. Find SlackAccountMapping by hilfeUserId
2. Call Slack API to revoke token: POST https://slack.com/api/auth.revoke
3. Delete SlackAccountMapping from database
4. Delete SlackNotificationPreference from database
5. Refresh app home to show "disconnected" state
6. Log event: "ACCOUNT_DISCONNECTED"
```

The disconnect button is in the app home (Phase 4). It posts to `POST /api/v1/slack/interactions` with action_id `hilfe_disconnect`.

### 5.4 Account Mapping Service

**`SlackAccountMappingService.java`**

Key methods:

| Method | Description |
|---|---|
| `findBySlackUserId(String slackUserId)` | Returns `Optional<SlackAccountMapping>` — used for all inbound event lookups |
| `findHilfeUserBySlackUserId(String slackUserId)` | Returns the linked `User` entity |
| `isConnected(String slackUserId)` | Returns boolean |
| `saveMapping(UUID userId, String slackUserId, String slackTeamId, String plainToken, String scope)` | Encrypts and saves |
| `disconnect(UUID userId)` | Revokes and deletes |
| `getDecryptedToken(SlackAccountMapping mapping)` | Decrypts and returns token for API calls |

---

## 6. Phase 3 — Slack Event & Interaction Handler

### 6.1 Endpoint Security (All Inbound Endpoints)

All three Slack HTTP endpoints share the same pre-processing pipeline:

```
POST /api/v1/slack/commands
POST /api/v1/slack/events
POST /api/v1/slack/interactions
  ↓
  SlackSignatureVerifier.verify(request)   ← MUST pass; reject 401 if invalid
  ↓
  SlackRateLimiter.checkLimit(slackUserId) ← reject 429 if exceeded
  ↓
  Route to appropriate handler
```

These endpoints must be **excluded from Spring Security's JWT filter** (no HILFE JWT expected from Slack). Add them to the security config's `permitAll()` list — their authentication is the Slack signature instead.

Add to `SecurityConfig.java`:
```java
.requestMatchers("/api/v1/slack/**", "/oauth/slack/**").permitAll()
```

### 6.2 Event Handler — `POST /api/v1/slack/events`

**`SlackEventController.java`**

Handles:

1. **URL verification challenge** (Slack sends this once when you first save the event URL)
   ```json
   { "type": "url_verification", "challenge": "..." }
   ```
   → Respond immediately with `{ "challenge": "..." }`

2. **`app_home_opened`** event
   → Call `SlackEventHandler.handleAppHomeOpened(slackUserId)`
   → Which calls `SlackApiClient.publishHomeTab(slackUserId)` (re-renders app home)

All events respond with HTTP 200 immediately (Slack requires response within 3 seconds). Actual processing should be dispatched to a `@Async` method.

### 6.3 Interaction Handler — `POST /api/v1/slack/interactions`

**`SlackInteractionController.java`**

Slack sends all button clicks, modal submissions, and overflow menu selections here as `application/x-www-form-urlencoded` with a `payload` parameter containing a JSON string.

**`SlackInteractionHandler.java`** — routes by `type` and `action_id`/`callback_id`:

| Trigger | Handler Method |
|---|---|
| `block_actions` / `action_id: hilfe_open_create_incident` | `openIncidentCreationModal(slackUserId, triggerId)` |
| `block_actions` / `action_id: hilfe_open_my_incidents` | `openMyIncidentsView(slackUserId, triggerId)` |
| `block_actions` / `action_id: hilfe_disconnect` | `disconnectAccount(slackUserId)` |
| `block_actions` / `action_id: hilfe_my_incidents_page_next` | `paginateMyIncidents(slackUserId, triggerId, page + 1)` |
| `block_actions` / `action_id: hilfe_my_incidents_page_prev` | `paginateMyIncidents(slackUserId, triggerId, page - 1)` |
| `block_actions` / `action_id: hilfe_save_notification_prefs` | `saveNotificationPrefs(slackUserId, values)` |
| `view_submission` / `callback_id: hilfe_create_incident_modal` | `submitIncidentCreation(slackUserId, modalValues)` |
| `view_submission` / `callback_id: hilfe_notification_prefs_modal` | `saveNotificationPrefsModal(slackUserId, values)` |

---

## 7. Phase 4 — App Home (Block Kit UI)

### 7.1 `HomeTabBuilder.java`

Builds the app home view using Slack Block Kit. The view differs based on connection status and user role.

#### Connected State — Client Role

```
Header: "Welcome to HILFE Bot 👋"
Section: "You're connected as {fullName} ({email})"
Divider
Actions:
  [Add New Incident]    [My Incidents]    [Notification Settings]
Divider
Section (muted): "For full incident management, attachments, and message threads, visit HILFE directly."
Actions:
  [Open HILFE ↗]
Divider
Actions:
  [Disconnect from HILFE] (danger style)
```

#### Connected State — Agent Role

```
(same as above)
Note: Agents see the same buttons; agent-only features (e.g. assigned incidents) are available
      in future iterations — not in scope for v1.
```

#### Disconnected State

```
Header: "Welcome to HILFE Bot 👋"
Section: "HILFE Bot lets you report and track incidents directly from Slack."
Section (muted): "⚠️ Your Slack account is not connected to HILFE. Connect your account to get started."
Divider
Actions:
  [Connect to HILFE]
Divider
Section (muted): "For full incident management, visit HILFE directly."
Actions:
  [Open HILFE ↗]
```

### 7.2 `SlackApiClient.publishHomeTab(slackUserId)`

```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public void publishHomeTab(String slackUserId) {
    Optional<SlackAccountMapping> mapping = mappingService.findBySlackUserId(slackUserId);
    View view = mapping.isPresent()
        ? homeTabBuilder.buildConnected(mapping.get())
        : homeTabBuilder.buildDisconnected();

    slack.methods(botToken).viewsPublish(r -> r
        .userId(slackUserId)
        .view(view)
    );
}
```

The `@Retryable` annotation (Spring Retry) ensures transient Slack API failures are retried without impacting user-facing operations.

---

## 8. Phase 5 — Incident Creation Modal

### 8.1 `IncidentModalBuilder.java`

Builds the modal dynamically, loading dropdown options from the database at render time.

```
Modal Title: "Report New Incident"
Callback ID: hilfe_create_incident_modal

Block 1 — Plain text input
  Label: "Title"
  Action ID: title
  Max length: 100
  Placeholder: "Brief summary of the incident"

Block 2 — Plain text input (multiline)
  Label: "Description"
  Action ID: description
  Max length: 1000
  Placeholder: "Describe the incident in detail"

Block 3 — Static select
  Label: "Incident Type / Topic"
  Action ID: incident_type_id
  Options: loaded from IncidentTypeRepository.findAllActive()
           (value = UUID, label = type name)

Block 4 — Static select
  Label: "Priority"
  Action ID: severity_id
  Options: loaded from SeverityRepository.findAll()
  Initial option: "Low" severity (default)

Submit button: "Submit"
Close button:  "Cancel"
```

> **Design note:** Department/topic selection is represented by "Incident Type" since the existing domain model uses `incident_types` with optional `agent_group_id` to route to the correct department. There is no separate department dropdown on incident creation in the existing web platform — do not introduce a new concept here.

### 8.2 Opening the Modal

**`SlackInteractionHandler.openIncidentCreationModal(slackUserId, triggerId)`**

```
1. Verify user is connected (has SlackAccountMapping) → if not, respond with ephemeral error
2. Verify user has INCIDENT_CREATE permission
3. Load incident types and severities from DB
4. Build modal view via IncidentModalBuilder
5. Call Slack API: views.open(triggerId, view)
```

### 8.3 Modal Submission Handler

**`SlackInteractionHandler.submitIncidentCreation(slackUserId, modalValues)`**

```
1. Extract values: title, description, incidentTypeId, severityId
2. Look up hilfeUser via SlackAccountMappingService.findHilfeUserBySlackUserId(slackUserId)
3. Server-side validation:
   a. title not blank, max 100 chars
   b. description not blank, max 1000 chars
   c. incidentTypeId is a valid UUID pointing to an active IncidentType
   d. severityId is a valid UUID pointing to an existing Severity
   If any fail → return modal errors response (Slack will display inline)
4. Build CreateIncidentRequest from validated values
5. Authenticate as the HILFE user:
   - Create a Spring SecurityContext with the user's authorities
   - Call IncidentService.createIncident(request, authentication)
6. On success:
   - Respond with HTTP 200 (empty body clears the modal)
   - Send a DM to the user:
     "✅ Incident #INC-{incidentNo} reported successfully! {title}
      View your ticket: {hilfeBaseUrl}/incidents/{incidentId}"
7. On validation failure from service layer:
   - Return Slack errors response with field-level messages
8. Log: slackBotLogService.log(slackUserId, hilfeUserId, "INCIDENT_CREATED", {incidentId}, success)
```

**Security context injection:**

Since the service layer expects a Spring `Authentication` object, inject one programmatically:

```java
UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
    hilfeUser, null, hilfeUser.getAuthorities()
);
SecurityContextHolder.getContext().setAuthentication(auth);
try {
    incidentService.createIncident(request, auth);
} finally {
    SecurityContextHolder.clearContext(); // always clear after async/non-web invocation
}
```

---

## 9. Phase 6 — My Incidents View

### 9.1 View Design

The "My Incidents" view opens as a **modal** (not a full page replacement of app home) triggered by the "My Incidents" button.

```
Modal Title: "My Incidents"
Callback ID: hilfe_my_incidents_modal

For each incident (up to 5 per page):
  Section:
    Text: "*#INC-{incidentNo}* — {title}"
    Fields:
      Status: {statusName}    Priority: {severityName}
      Created: {createdAt relative}
    Accessory: Button "View in HILFE →" (URL: {hilfeBaseUrl}/incidents/{id})
  Divider

Pagination row (if > 5 incidents):
  Actions:
    [← Previous]  "Page {n} of {total}"  [Next →]

If zero incidents:
  Section: "You haven't raised any incidents yet. Use 'Add New Incident' to get started."
```

### 9.2 `IncidentListBuilder.java`

```java
public View buildMyIncidentsModal(List<Incident> incidents, int page, int totalPages) {
    // Build Block Kit view
    // Each incident renders as a section with fields and a "View in HILFE" button
    // Pagination buttons carry page number in action value
}
```

### 9.3 Handler

**`SlackInteractionHandler.openMyIncidentsView(slackUserId, triggerId)`**

```
1. Verify connected, else error
2. Find hilfeUser
3. Call IncidentService.getIncidentsByUser(userId, page=0, size=5)
   → Reuse existing paginated incident query
4. Build modal via IncidentListBuilder
5. views.open(triggerId, view) or views.update(viewId, view) for pagination
```

---

## 10. Phase 7 — Slack Notifications

### 10.1 Architecture

The existing notification system publishes domain events to `NotificationEventListener`, which persists to the database and broadcasts via WebSocket. The Slack notification delivery layer **hooks into this same listener** as an additional delivery channel.

**`NotificationEventListener.java`** — modify existing listener (minimal change):

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onNotification(IncidentNotificationEvent event) {
    // ... existing persistence and WebSocket broadcast ...

    // NEW: attempt Slack delivery (non-fatal)
    slackDeliverySafe(event);
}

private void slackDeliverySafe(IncidentNotificationEvent event) {
    try {
        slackNotificationDeliveryService.deliver(event);
    } catch (Exception e) {
        log.warn("Slack notification delivery failed for event {}: {}", event.getClass().getSimpleName(), e.getMessage());
        // NEVER re-throw — must not impact HILFE operations
    }
}
```

### 10.2 `SlackNotificationDeliveryService.java`

```java
@Service
public class SlackNotificationDeliveryService {

    @Async
    public void deliver(IncidentNotificationEvent event) {
        UUID targetUserId = event.getTargetUserId();

        // 1. Check if user has a Slack account linked
        Optional<SlackAccountMapping> mapping = mappingService.findByHilfeUserId(targetUserId);
        if (mapping.isEmpty()) return;

        // 2. Check notification preferences
        SlackNotificationPreference prefs = preferenceService.getOrDefault(targetUserId);
        if (!shouldDeliver(event, prefs)) return;

        // 3. Build message (NO sensitive data in payload)
        String message = buildNotificationMessage(event);
        String slackUserId = mapping.get().getSlackUserId();
        String token = tokenEncryptionService.decrypt(mapping.get().getAccessTokenEncrypted());

        // 4. Send DM via Slack API with retry
        sendDmWithRetry(token, slackUserId, message, event.getIncidentId());
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2),
               include = SlackApiException.class)
    private void sendDmWithRetry(String token, String slackUserId, String message, UUID incidentId) {
        // Open DM channel, then post message
        var openResponse = slack.methods(token).conversationsOpen(r -> r.users(List.of(slackUserId)));
        String channelId = openResponse.getChannel().getId();
        slack.methods(token).chatPostMessage(r -> r
            .channel(channelId)
            .blocks(buildNotificationBlocks(message, incidentId))
        );
    }
}
```

### 10.3 Notification Types & Payloads

All Slack notification DMs follow this structure (Block Kit):

```
┌─────────────────────────────────────────┐
│ 🔔  HILFE Notification                  │
│                                         │
│  {NotificationTitle}                    │
│  {NotificationMessage — no sensitive    │
│   data, no internal notes}              │
│                                         │
│  Incident: #INC-{incidentNo} — {title}  │
│                                         │
│  [View in HILFE →]                      │
└─────────────────────────────────────────┘
```

**Payload content rules:**
- Include: incident number, incident title, status name, action taken
- **Exclude:** agent internal notes, client personal data beyond name, file attachments, message content

| Event | Notification Title | Message |
|---|---|---|
| `IncidentAssignedEvent` | "Incident Assigned" | "Your incident has been assigned to an agent." |
| `IncidentAutoAssignedClientEvent` | "Incident Assigned" | "Your incident has been picked up and is being worked on." |
| `IncidentStatusChangedEvent` | "Status Updated" | "Your incident status changed to {newStatus}." |
| `NewMessageOnIncidentEvent` | "New Message" | "An agent has responded to your incident." |

### 10.4 Notification Message Builder

**`SlackNotificationContentFactory.java`**

Mirrors the existing `NotificationContentFactory` but produces Slack Block Kit output. Maps each `IncidentNotificationEvent` subtype to a `SlackNotificationContent` (title + body + incidentLink).

---

## 11. Phase 8 — Notification Preferences

### 11.1 Preferences Modal

Opened via the "Notification Settings" button in the app home or `/hilfe settings`.

```
Modal Title: "Notification Settings"
Callback ID: hilfe_notification_prefs_modal

Section: "Choose which Slack notifications you want to receive."

Checkboxes (action_id: notification_types):
  ☑ Notify me when a ticket is assigned to me
  ☑ Notify me when a ticket status changes
  ☑ Notify me when there's a new message on my ticket

Submit: "Save Settings"
```

### 11.2 Handler

**`SlackNotificationPreferenceService.savePreferences(UUID userId, boolean assignment, boolean statusChange, boolean newMessage)`**

- Upserts `SlackNotificationPreference` row
- Publishes no events (not auditable domain event; just a preference update)
- Returns updated preferences

---

## 12. Phase 9 — Security Controls

### 12.1 Slack Request Signature Verification

**`SlackSignatureVerifier.java`**

Implements Slack's [signing secret verification protocol](https://api.slack.com/authentication/verifying-requests-from-slack):

```java
public void verify(HttpServletRequest request) {
    String timestamp = request.getHeader("X-Slack-Request-Timestamp");
    String slackSignature = request.getHeader("X-Slack-Signature");

    // 1. Reject requests older than 5 minutes (replay attack prevention)
    long now = Instant.now().getEpochSecond();
    if (Math.abs(now - Long.parseLong(timestamp)) > 300) {
        throw new SlackSignatureException("Request timestamp too old");
    }

    // 2. Reconstruct signature
    String requestBody = readBodyAsString(request);  // Must cache body for reuse
    String baseString = "v0:" + timestamp + ":" + requestBody;
    String expectedSignature = "v0=" + hmacSha256Hex(signingSecret, baseString);

    // 3. Constant-time comparison (prevent timing attacks)
    if (!MessageDigest.isEqual(
            slackSignature.getBytes(StandardCharsets.UTF_8),
            expectedSignature.getBytes(StandardCharsets.UTF_8))) {
        throw new SlackSignatureException("Invalid Slack signature");
    }
}
```

> **Important:** The raw request body must be read and cached in a `ContentCachingRequestWrapper` before the signature check, so it can be read again by the handler. Implement this as a `OncePerRequestFilter` on Slack endpoint paths.

### 12.2 Rate Limiting

**`SlackRateLimiter.java`** — uses Bucket4j with per-user in-memory buckets

```java
@Component
public class SlackRateLimiter {
    private final ConcurrentHashMap<String, Bucket> commandBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> interactionBuckets = new ConcurrentHashMap<>();

    public void checkCommandLimit(String slackUserId) {
        Bucket bucket = commandBuckets.computeIfAbsent(slackUserId,
            k -> Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many commands. Please wait a moment.");
        }
    }

    public void checkInteractionLimit(String slackUserId) {
        // 20 per minute for interactions
        ...
    }
}
```

### 12.3 Role Enforcement in Bot

All bot handlers that perform ticketing actions must:

```java
User hilfeUser = mappingService.findHilfeUserBySlackUserId(slackUserId)
    .orElseThrow(() -> new SlackNotConnectedException("Account not connected"));

// Reuse existing permission check
if (!userAuthorityService.hasPermission(hilfeUser, Permission.INCIDENT_CREATE)) {
    throw new SlackAuthorizationException("You do not have permission to perform this action.");
}
```

Bot-specific permission checks map directly to existing `Permission` enum values. No new permissions are introduced.

### 12.4 Input Sanitisation

All text inputs from Slack modals are sanitised before passing to `IncidentService`:

```java
String sanitizedTitle = HtmlUtils.htmlEscape(rawTitle.strip());
String sanitizedDescription = HtmlUtils.htmlEscape(rawDescription.strip());
```

The existing `IncidentService` validation (`@NotBlank`, `@Size`) also applies, providing defence in depth.

### 12.5 CSRF Protection for OAuth

The `state` parameter in the OAuth flow is a one-time-use, time-limited token stored in the database (`slack_oauth_state_tokens`). See Phase 2 for full details.

---

## 13. Phase 10 — Frontend OAuth Pages

> **Note:** The existing project is a Spring Boot backend only. The OAuth pages are Spring MVC views (Thymeleaf or static HTML served by the backend), not a separate frontend application. If the project already has a frontend (React/Angular), coordinate with the frontend team to implement these as frontend routes instead.

### 13.1 Pages Required

| URL | Purpose |
|---|---|
| `GET /oauth/slack/authorize` | Optional intermediary page before Slack's OAuth dialog |
| `GET /oauth/slack/success` | Shown after successful connection |
| `GET /oauth/slack/error` | Shown on failure with `?reason=` parameter |

### 13.2 `/oauth/slack/authorize` — Connection Prompt Page

Content:
- HILFE Bot logo + brand mark
- Heading: "Connect Your HILFE Account to Slack"
- Description of what permissions are being requested and why
- Button: "Authorise with Slack" → links to Slack OAuth URL (with state token)
- Note: "You will be redirected back to HILFE after authorisation."

### 13.3 `/oauth/slack/success` — Success Page

Content:
- Success icon
- Heading: "Account Connected!"
- Message: "Your HILFE account is now linked to Slack. Return to the HILFE Bot app in Slack to get started."
- Button: "Return to Slack" → deep link to `slack://app?team={teamId}&id={appId}&tab=home`
- Secondary: "Open HILFE" → link to HILFE platform

### 13.4 `/oauth/slack/error` — Error Page

Dynamic content based on `reason` parameter:

| Reason | User Message |
|---|---|
| `invalid_state` | "The connection request has expired or is invalid. Please try connecting again from Slack." |
| `token_exchange_failed` | "We couldn't complete the authorisation with Slack. Please try again." |
| `access_denied` | "You declined the Slack authorisation. Please try again if this was a mistake." |
| `already_connected` | "This HILFE account is already connected to Slack." |

Each error page includes a button: "Try Again" → links back to `/oauth/slack/authorize` with a fresh state token.

### 13.5 Acceptance Criteria for Pages

- Pages follow the approved company design system
- No sensitive data (tokens, user IDs) exposed in page source or URL parameters
- Pages are mobile-responsive
- Pages degrade gracefully if JavaScript is disabled

---

## 14. Phase 11 — Audit Logging

### 14.1 `SlackBotLogService.java`

```java
@Service
public class SlackBotLogService {

    @Async
    public void log(String slackUserId, UUID hilfeUserId, String action,
                    Map<String, Object> payload, boolean success, String errorMessage) {
        SlackBotLog log = new SlackBotLog();
        log.setSlackUserId(slackUserId);
        log.setHilfeUserId(hilfeUserId);
        log.setAction(action);
        log.setPayload(sanitisePayload(payload)); // remove tokens before logging
        log.setSuccess(success);
        log.setErrorMessage(errorMessage);
        repository.save(log);
    }
}
```

**Actions to log:**

| Action Constant | Trigger |
|---|---|
| `ACCOUNT_CONNECTED` | Successful OAuth connection |
| `ACCOUNT_DISCONNECTED` | User disconnects |
| `INCIDENT_CREATED` | Incident created via modal |
| `INCIDENT_MODAL_OPENED` | User opened creation modal |
| `MY_INCIDENTS_VIEWED` | User viewed incidents list |
| `NOTIFICATION_PREFS_UPDATED` | Preferences changed |
| `NOTIFICATION_DELIVERED` | Slack DM sent successfully |
| `NOTIFICATION_DELIVERY_FAILED` | DM failed after retries |
| `SIGNATURE_VERIFICATION_FAILED` | Invalid Slack signature |
| `RATE_LIMIT_EXCEEDED` | Rate limit hit |
| `PERMISSION_DENIED` | Unauthorized bot action |
| `OAUTH_STATE_REJECTED` | Invalid/expired CSRF state token |

All log entries include: `slackUserId`, `hilfeUserId` (if resolved), `action`, `success`, `timestamp`.

### 14.2 Existing ActivityLog Integration

For significant events (incident creation), also write to the existing `ActivityLog` table via `ActivityLogService` to maintain a unified audit trail visible in the HILFE admin dashboard.

---

## 15. Phase 12 — Testing

### 15.1 Unit Tests

| Class | What to Test |
|---|---|
| `SlackSignatureVerifier` | Valid signature passes; invalid signature throws; expired timestamp throws; timing-safe comparison |
| `SlackStateTokenService` | Token generation is unique; valid token validates; expired token rejects; used token rejects; cleanup removes old tokens |
| `SlackTokenEncryptionService` | Encrypt-decrypt roundtrip; different plaintexts produce different ciphertexts |
| `SlackRateLimiter` | Under limit passes; at limit passes; over limit throws; limit resets after window |
| `IncidentModalBuilder` | Modal contains all required blocks; options populated correctly |
| `HomeTabBuilder` | Connected view contains disconnect button; disconnected view contains connect button |
| `SlackNotificationDeliveryService` | Delivers when user connected and prefs allow; skips when user not connected; skips when pref disabled; Slack API failure does not throw |
| `SlackCommandHandler` | Connect command builds correct OAuth URL; unknown subcommand returns help |

### 15.2 Integration Tests

| Scenario | What to Test |
|---|---|
| Full OAuth flow | State token generated → callback validates state → mapping saved → app home published |
| Incident creation via modal | Modal submitted → incident created in DB → DM sent to user |
| Disconnect flow | Disconnect action → mapping deleted → prefs deleted → app home shows disconnected state |
| Notification delivery | `IncidentAssignedEvent` published → Slack DM delivered to linked user; non-linked user receives no DM |
| Signature rejection | POST to `/api/v1/slack/commands` with invalid signature → 401 returned |
| Rate limit enforcement | 11 commands in 1 minute from same user → 11th rejected with 429 |

### 15.3 Test Fixtures & Mocking

- Mock `Slack` SDK client using Mockito to avoid real API calls in tests
- Use `@SpringBootTest` with `@MockBean SlackApiClient` for integration tests
- Use Testcontainers (PostgreSQL) for DB-level integration tests (consistent with existing test patterns)
- Test `SlackSignatureVerifier` with precomputed HMAC vectors from Slack's documentation

---

## 16. Configuration & Secrets Reference

### 16.1 Environment Variables

| Variable | Description | Storage |
|---|---|---|
| `SLACK_BOT_TOKEN` | Bot User OAuth Token (`xoxb-...`) | Secrets Manager |
| `SLACK_SIGNING_SECRET` | Slack app signing secret | Secrets Manager |
| `SLACK_CLIENT_ID` | Slack app client ID | Secrets Manager |
| `SLACK_CLIENT_SECRET` | Slack app client secret | Secrets Manager |
| `SLACK_APP_ID` | Slack app ID (for deep links) | Secrets Manager |
| `SLACK_TOKEN_ENCRYPTION_KEY` | 32-byte hex key for AES-256 token encryption | Secrets Manager |

**None of these values may be committed to source control or stored in `application.yaml`.**

### 16.2 Feature Flag (Optional)

Consider wrapping the Slack module behind a feature flag during initial rollout:

```yaml
slack:
  enabled: ${SLACK_INTEGRATION_ENABLED:false}
```

Disable all Slack endpoints and background delivery if `enabled: false`, with no impact on core HILFE operations.

---

## 17. Dependency Map

```
Phase 0 (Slack App Setup)
  └── required before all other phases

Phase 1 (DB Schema)
  └── required before Phase 2, 4, 5, 6, 7, 8

Phase 2 (OAuth)
  └── requires Phase 0, Phase 1
  └── required before Phase 4, 5, 6, 7, 8

Phase 3 (Event/Interaction Handlers)
  └── requires Phase 0, Phase 9 (signature verification)
  └── routes to Phase 4, 5, 6

Phase 4 (App Home)
  └── requires Phase 2, Phase 3

Phase 5 (Incident Creation Modal)
  └── requires Phase 3, Phase 2
  └── depends on existing IncidentService (no changes)

Phase 6 (My Incidents)
  └── requires Phase 3, Phase 2
  └── depends on existing IncidentService paginated query

Phase 7 (Notifications)
  └── requires Phase 1, Phase 2
  └── hooks into existing NotificationEventListener (minimal change)

Phase 8 (Notification Preferences)
  └── requires Phase 1, Phase 2, Phase 3

Phase 9 (Security Controls)
  └── required before Phase 3 (must be implemented first)

Phase 10 (Frontend Pages)
  └── requires Phase 2 (OAuth callback logic)
  └── parallel track — can be done alongside Phases 3–8

Phase 11 (Audit Logging)
  └── woven into all phases — log after each action
  └── SlackBotLogService created in Phase 1 setup, used from Phase 2 onwards

Phase 12 (Testing)
  └── written alongside each phase, not after
```

---

## 18. Rollout Checklist

### Pre-Deployment (Staging)

- [ ] Slack App registered in Slack API dashboard with correct scopes and event URLs pointing to staging
- [ ] All environment variables (`SLACK_BOT_TOKEN`, `SLACK_SIGNING_SECRET`, etc.) set in secrets manager for staging
- [ ] `SLACK_TOKEN_ENCRYPTION_KEY` generated and stored; backup secured
- [ ] Flyway migration `V52__create_slack_integration_tables.sql` applied to staging DB
- [ ] `/hilfe connect` slash command tested end-to-end in staging Slack workspace
- [ ] OAuth flow tested: connect → app home refreshes → disconnect → app home resets
- [ ] Incident creation modal tested: valid submission creates ticket in HILFE; invalid inputs show errors
- [ ] "My Incidents" modal tested: pagination works; "View in HILFE" links are correct
- [ ] Notification delivery tested: ticket assignment, status change, new message all produce Slack DMs
- [ ] Notification preferences tested: toggling off suppresses correct notification types
- [ ] Signature verification tested: spoofed requests return 401
- [ ] Rate limiting tested: 11th command rejected within window
- [ ] Role enforcement tested: unauthenticated/unlinked user cannot create incidents
- [ ] OAuth CSRF protection tested: reused state token rejected; expired state token rejected
- [ ] Audit logs verified in `slack_bot_logs` table for all interactions
- [ ] Existing HILFE functionality regression tested: bot failures do not impact incident creation, notifications, or WebSocket delivery
- [ ] OAuth web pages render correctly (authorize, success, error) and follow design system
- [ ] Product owner has reviewed and approved app home, modal, and notification designs in staging

### Pre-Deployment (Production)

- [ ] Slack App event subscription URL updated to production domain
- [ ] OAuth redirect URI updated to production domain in Slack app settings
- [ ] Production environment variables set in secrets manager
- [ ] Flyway migration applied to production DB
- [ ] HILFE Bot installed to organisation's production Slack workspace
- [ ] Smoke test: at least one real user connects, creates an incident, and receives a notification
- [ ] Integration runbook written and reviewed by team

### Post-Deployment

- [ ] Monitor `slack_bot_logs` for delivery failures in first 48 hours
- [ ] Confirm no spike in HILFE incident creation errors attributable to bot
- [ ] Alert configured on `NOTIFICATION_DELIVERY_FAILED` log action count threshold
- [ ] Product owner sign-off on production behaviour

---

*End of Implementation Plan*
