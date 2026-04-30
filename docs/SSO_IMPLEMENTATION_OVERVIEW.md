# SSO Implementation - Team Split

This folder contains the per-person breakdown for implementing the IMS/ARMS SSO flow in the Java backend. The frontend already completes ARMS login and sends a pre-issued token to the backend.

## Files
- SSO_PERSON_1.md (Lawson): Auth endpoints, JWT issuance, stateless flow.
- SSO_PERSON_2.md (Alphone): ARMS integration and user sync.
- SSO_PERSON_3.md (Basit): Guards, authorization, and role enforcement.

## Shared Assumptions
- Frontend obtains the ARMS token and calls backend login with it.
- Backend validates token with ARMS, creates/updates user, issues IMS JWT.
- Stateless auth (no server session). JWT TTL is 1 hour.
