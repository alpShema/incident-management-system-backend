# SSO Implementation Plan - Basit

Scope: Authorization, guards, and role-based access enforcement. This covers protecting routes with IMS JWT and ensuring role-based behavior aligns with the doc.

## Goals
- Enforce JWT checks on protected routes.
- Ensure role-based access (Client, Agent, Admin).
- Maintain user role consistency in API responses.

## Tasks
1) JWT strategy
- Extract `access_token` from cookie.
- Verify signature and expiry.
- Load user record from DB.

2) Access token guard
- Require valid JWT for protected endpoints.
- Reject if token missing/invalid.

3) Role guards
- Client guard: allow only client endpoints.
- Agent guard: allow incident handling endpoints.
- Admin guard: allow configuration endpoints.

4) Logout behavior
- Clear `access_token` (and `arms_token` if stored).
- If refresh tokens are stored, revoke them.

5) Access checks
- Prevent clients from viewing other users' incidents.
- Prevent agents from admin configuration.

## Inputs from Frontend
- Frontend sends cookies on requests (credentials included).

## SSO Endpoint Notes (from SSO documentation)
- SSO login entrypoints: `/login` (v1) and `/v2/login` (v2).
- v1 login redirect includes query params: `app-token`, `login-hint`, `expires-at`.
- v2 login redirect includes no query params; token data stored in HTTP-only cookie and fetched via `/login-token`.
- Refresh endpoint: `/refresh-token` (POST with refresh token in body).
- Logout endpoint: `/logout` with `login-hint`, `account`, `redirect-url` query params.
- Client app must clear its own cookies on logout (SSO does not clear client cookies).

## Deliverables
- JWT guard/strategy.
- Role guards for Admin/Agent/Client.
- Route annotations for protected controllers.

## Notes
- JWT TTL should be 1 hour.
- If refresh tokens are used, rotate and revoke on logout.
