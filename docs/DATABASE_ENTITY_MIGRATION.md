# Hilfe v2 — Database Entity Migration

**Date:** 2026-04-24
**Team:** Amalitech Hilfe Backend
**Status:** Complete

---

## Overview

This document covers the first phase of migrating the Hilfe (Incident Management System) backend from **NestJS + Prisma + PostgreSQL** to **Java Spring Boot + JPA/Hibernate + PostgreSQL**.

In this phase, we mapped all 14 existing database tables as JPA entities in the new Spring Boot application. The Spring Boot app connects to the **same PostgreSQL database** the NestJS app uses — no schema changes, no data migration. Hibernate runs in `validate` mode to ensure our entities match the existing tables exactly.

---

## What Changed

| Item | Before | After |
|------|--------|-------|
| ORM | Prisma (NestJS) | JPA/Hibernate (Spring Boot) |
| Entity definitions | `schema.prisma` | Java `@Entity` classes in `com.amalitech.hilfe.models` |
| Database | PostgreSQL (Prisma-managed) | Same PostgreSQL database (no changes) |
| Schema management | Prisma Migrate | Flyway (disabled for now — DB already exists) |
| Naming strategy | Prisma default (PascalCase tables) | `PhysicalNamingStrategyStandardImpl` to preserve PascalCase |

---

## Configuration Changes

### application.yaml

**Hibernate naming strategy** — Spring Boot's default naming strategy converts `User` to `user` and `AgentGroup` to `agent_group`. Since the existing Prisma database uses PascalCase table names, we set the physical naming strategy to `StandardImpl` which uses names as-is:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

**Flyway disabled** — The database is already managed by Prisma. Flyway is disabled to prevent conflicts with the existing `_prisma_migrations` table:

```yaml
spring:
  flyway:
    enabled: false
```

---

## Entity-to-Table Mapping

All entities live in the `com.amalitech.hilfe.models` package.

### 14 Tables, 14 Entities

| # | Entity Class | DB Table | Columns | Relationships | Owner |
|---|-------------|----------|---------|---------------|-------|
| 1 | `User.java` | User | 12 | Admin (1:1), Agent (1:1), Location (N:1) | Person 1 |
| 2 | `Admin.java` | Admin | 5 | User (1:1) | Person 1 |
| 3 | `Agent.java` | Agent | 6 | User (1:1), AgentGroup (N:1) | Person 1 |
| 4 | `AgentGroup.java` | AgentGroup | 6 | Agents (1:N) | Person 1 |
| 5 | `Location.java` | Location | 5 | Users (1:N) | Person 1 |
| 6 | `Incident.java` | Incident | 13 | User, Location, IncidentType, Status, Severity, Agent | Person 2 |
| 7 | `IncidentType.java` | IncidentType | 7 | Agent (N:1) | Person 2 |
| 8 | `Status.java` | Status | 6 | — | Person 2 |
| 9 | `Severity.java` | Severity | 5 | — | Person 2 |
| 10 | `IncidentReportLog.java` | IncidentReportLog | 5 | Incident (N:1), Status (N:1) | Person 2 |
| 11 | `IncidentLog.java` | IncidentLog | 6 | Incident (N:1), User (N:1) | Person 3 |
| 12 | `Message.java` | Message | 6 | User/sender (N:1), Incident (N:1) | Person 3 |
| 13 | `Media.java` | Media | 5 | Incident (N:1) | Person 3 |
| 14 | `Session.java` | Session | 4 | — | Person 3 |

---

## Relationship Diagram

```
┌──────────────┐       1:N        ┌──────────────┐
│   Location   │◄─────────────────│     User     │
│──────────────│                  │──────────────│
│ id           │                  │ id (ARMS)    │
│ name         │                  │ email        │
│ description  │                  │ full_name    │
└──────────────┘                  │ permissions[]│
                                  │ location_id  │
                     1:1          └──┬────┬──────┘
                  ┌───────────────────┘    └──────────────────┐
                  ▼                                           ▼
           ┌──────────┐                                ┌──────────────┐
           │  Admin   │                                │    Agent     │
           │──────────│                                │──────────────│
           │ id       │                                │ id           │
           │ user_id  │                                │ user_id      │
           │ status   │                                │ agent_grp_id │
           └──────────┘                                │ status       │
                                                       └──────┬───────┘
                                                              │ N:1
                                                              ▼
                                                       ┌──────────────┐
                                                       │  AgentGroup  │
                                                       │──────────────│
                                                       │ id           │
                                                       │ name         │
                                                       │ description  │
                                                       │ status       │
                                                       └──────────────┘

┌───────────────────────────────────────────────────────────────────────────┐
│                              Incident                                     │
│───────────────────────────────────────────────────────────────────────────│
│ id, title, incident_no (auto), description, read                          │
│ user_id → User  |  location_id → Location  |  incident_type_id → Type    │
│ status_id → Status  |  severity_id → Severity  |  assigned_to_id → Agent │
└───────┬──────────────┬───────────────┬────────────────────────────────────┘
        │              │               │
        ▼              ▼               ▼
┌──────────────┐ ┌───────────┐ ┌──────────────────┐
│ IncidentLog  │ │  Message  │ │      Media       │
│──────────────│ │───────────│ │──────────────────│
│ incident_id  │ │ sender_id │ │ incident_id      │
│ user_id      │ │ incid._id │ │ original_name    │
│ text         │ │ content   │ │ file_key, url    │
└──────────────┘ └───────────┘ │ (no timestamps)  │
                               └──────────────────┘

┌────────────────────┐      ┌──────────────┐      ┌──────────────┐
│ IncidentReportLog  │      │    Status    │      │   Severity   │
│────────────────────│      │──────────────│      │──────────────│
│ incident_id        │─────►│ id           │      │ id           │
│ status_id          │      │ name         │      │ name         │
│ date               │      │ show_on_reply│      │ description  │
│ (no updated_at)    │      └──────────────┘      └──────────────┘
└────────────────────┘

┌────────────────────┐
│   IncidentType     │
│────────────────────│
│ name, description  │
│ admin_id (no FK)   │
│ agent_id → Agent   │
│ visible_to_group   │
└────────────────────┘

┌──────────────┐
│   Session    │  (NestJS session store — not used by Spring Boot)
│──────────────│
│ id, sid      │
│ data         │
│ expiresAt    │  ← camelCase in DB
└──────────────┘
```

---

## All 18 Foreign Key Relationships

| Source Table | Column | Target Table | Target Column | Cascade |
|-------------|--------|-------------|---------------|---------|
| Admin | user_id | User | id | ON DELETE CASCADE |
| Agent | user_id | User | id | ON DELETE CASCADE |
| Agent | agent_group_id | AgentGroup | id | ON DELETE SET NULL |
| User | location_id | Location | id | ON DELETE SET NULL |
| Incident | user_id | User | id | ON DELETE RESTRICT |
| Incident | location_id | Location | id | ON DELETE RESTRICT |
| Incident | incident_type_id | IncidentType | id | ON DELETE RESTRICT |
| Incident | status_id | Status | id | ON DELETE SET NULL |
| Incident | severity_id | Severity | id | ON DELETE SET NULL |
| Incident | assigned_to_id | Agent | id | ON DELETE SET NULL |
| IncidentType | agent_id | Agent | id | ON DELETE RESTRICT |
| IncidentLog | incident_id | Incident | id | ON DELETE RESTRICT |
| IncidentLog | user_id | User | id | ON DELETE RESTRICT |
| IncidentReportLog | incident_id | Incident | id | ON DELETE RESTRICT |
| IncidentReportLog | status_id | Status | id | ON DELETE RESTRICT |
| Media | incident_id | Incident | id | ON DELETE RESTRICT |
| Message | incident_id | Incident | id | ON DELETE RESTRICT |
| Message | sender_id | User | id | ON DELETE RESTRICT |

---

## Design Patterns Used

### 1. Dual Foreign Key Mapping

Every foreign key is mapped twice in the entity:
- A plain `String` field for reading/writing the FK value
- A `@ManyToOne` / `@OneToOne` relationship for navigating to the related object

The relationship is always marked `insertable = false, updatable = false` to avoid conflicts.

```java
// Plain FK field — use this to set the value
@Column(name = "user_id", nullable = false)
private String userId;

// Relationship — use this to read the related entity
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", insertable = false, updatable = false)
private User user;
```

**Why:** This avoids the common JPA pitfall where you need to load a full entity just to set a foreign key. You can simply do `agent.setUserId("abc-123")` instead of `agent.setUser(userRepository.findById("abc-123").get())`.

### 2. Lifecycle Timestamps

Timestamps are managed by JPA lifecycle callbacks, mirroring Prisma's `@default(now())` and `@updatedAt`:

```java
@PrePersist
protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
}

@PreUpdate
protected void onUpdate() {
    updatedAt = Instant.now();
}
```

**Exceptions:**
- `Media` — no timestamps at all
- `Session` — no timestamps (only `expiresAt`)
- `IncidentReportLog` — has `created_at` but no `updated_at`

### 3. String IDs (Not Auto-Generated)

All entities use `String` primary keys. No `@GeneratedValue`. IDs are either:
- Provided by ARMS (the `User.id` comes from the external auth system)
- Generated manually with `UUID.randomUUID().toString()` for all other entities

This matches Prisma's `@id @default(uuid())` which generates UUIDs as text.

### 4. Nullable vs Non-Nullable Booleans

The entities carefully distinguish between:
- `boolean` (primitive) — for NOT NULL columns (e.g., `Admin.status`, `Incident.read`, `IncidentType.visibleToGroup`)
- `Boolean` (wrapper) — for nullable columns (e.g., `User.status`, `Agent.status`, `AgentGroup.status`, `Status.showOnReply`)

---

## Notable Quirks

| Quirk | Detail |
|-------|--------|
| PascalCase table names | Prisma creates tables as `User`, `AgentGroup`, etc. — not the `users`, `agent_groups` convention Java developers expect. Handled by `PhysicalNamingStrategyStandardImpl`. |
| `Session.expiresAt` | The only camelCase column name in the entire database. Every other column is snake_case. Mapped with `@Column(name = "expiresAt")`. |
| `Incident.incident_no` | Auto-increment (`SERIAL`) column. Marked `insertable = false, updatable = false` — the database generates this value. |
| `IncidentType.admin_id` | Stored as plain `String` with no `@ManyToOne` relationship. The Prisma schema has no `@relation` for this field — it's just a reference value, not a true foreign key. |
| `IncidentReportLog` | The only data entity with `created_at` but no `updated_at`. |
| `Media` | The only data entity with no timestamp columns at all. |
| `Session` | Used by the NestJS app's session store. The Spring Boot app uses stateless JWT — this entity is mapped for completeness only. |

---

## File Structure

```
hilfe-v2-backend/
├── src/main/java/com/amalitech/hilfe/
│   ├── HilfeApplication.java
│   ├── config/
│   │   └── SwaggerConfig.java
│   └── models/
│       ├── User.java
│       ├── Admin.java
│       ├── Agent.java
│       ├── AgentGroup.java
│       ├── Location.java
│       ├── Incident.java
│       ├── IncidentType.java
│       ├── IncidentLog.java
│       ├── IncidentReportLog.java
│       ├── Status.java
│       ├── Severity.java
│       ├── Media.java
│       ├── Message.java
│       └── Session.java
├── src/main/resources/
│   └── application.yaml
└── docs/
    └── DATABASE_ENTITY_MIGRATION.md  ← this document
```

---

## Verification

| Check | Result |
|-------|--------|
| All 14 tables mapped | 14/14 |
| All 18 foreign keys mapped | 18/18 |
| All column types match DB | Verified against DB schema dump |
| All nullable flags match DB | Verified (Boolean vs boolean) |
| Compilation | BUILD SUCCESS (16 source files) |
| `ddl-auto: validate` | Hibernate will validate on startup against the live DB |

---

## Next Steps

1. **Repositories** — Create Spring Data JPA repository interfaces for each entity
2. **Authentication** — Implement ARMS SSO integration and JWT-based auth (replacing Passport.js)
3. **Services & Controllers** — Migrate business logic from NestJS services to Spring `@Service` classes
4. **DTOs** — Create request/response DTOs with Jakarta Validation
5. **Exception Handling** — Global exception handler for validation errors, auth failures, DB constraints
6. **Testing** — Repository integration tests, service unit tests, controller tests
