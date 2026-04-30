# SSO Implementation Plan - Alphone

Scope: ARMS integration and user synchronization. This aligns with the document describing ARMS as the source of truth and the backend using ARMS to fetch user details.

## Goals
- Implement ARMS API client.
- Fetch user details by token.
- Fetch employee directory for admin views.
- Provide helpers to transform ARMS data into IMS user entities.

## Tasks
1) ARMS API client
- Configure ARMS endpoints (auth, employee info, token lookup).
- Support REST call: "token -> user".
- Support GraphQL queries for employee list and user by ID.

2) Token verification
- Implement `getUserByToken(token)` to return:
  - first_name
  - last_name
  - email
  - profile_image
  - user_id
- Fail with explicit auth error if ARMS is unreachable or token invalid.

3) Employee directory helpers
- Implement `getAllUsersFromArms()` for admin list page.
- Implement `getUserById(id)` lookup.

4) Data transformation
- Implement `mapArmsUserToImsUser(...)` to return:
  - id
  - full_name
  - email
  - profile_img
  - status
  - permissions (default empty)

5) Error handling
- Normalize errors from ARMS.
- Log diagnostics; return clear messages to caller.

## Inputs from Frontend
- Frontend already completed ARMS login; backend receives token only.

## SSO Endpoint Notes (from SSO documentation)
- SSO login entrypoints: `/login` (v1) and `/v2/login` (v2).
- v1 login redirect includes query params: `app-token`, `login-hint`, `expires-at`.
- v2 login redirect includes no query params; token data stored in HTTP-only cookie and fetched via `/login-token`.
- Refresh endpoint: `/refresh-token` (POST with refresh token in body).
- Logout endpoint: `/logout` with `login-hint`, `account`, `redirect-url` query params.

## Deliverables
- ARMS integration service.
- Typed ARMS DTOs.
- Unit tests for token verification and user mapping.

## Notes
- Avoid caching user data longer than necessary.
- Ensure ARMS token is only used server-to-server.
