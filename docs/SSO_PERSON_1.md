# SSO Implementation Plan - Lawson

Scope: Core SSO entrypoint, token intake, and backend auth handling (stateless). This maps to the shared IMS/ARMS flow document and assumes the frontend already obtains the ARMS token and sends it to the backend.

## Goals
- Accept the pre-issued ARMS token from the frontend.
- Validate the token via ARMS.
- Create or update the user profile in the local DB.
- Issue IMS access token (JWT) cookie (stateless).
- Define refresh token handling if required.

## Tasks
1) Define auth endpoint(s) for login and logout
- Create `POST /auth/login` (or equivalent) that accepts the ARMS token from the frontend.
- Expect `auth` token to arrive via query or body; align with frontend contract.
- Create `GET /auth/logout` to invalidate session and clear IMS cookies.

2) Build login flow service (stateless)
- Accept ARMS token.
- Call ARMS "token -> user" endpoint.
- Upsert user in DB with name, email, profile image, user_id.
- Issue IMS JWT with 1h expiry.
- Decide refresh token strategy (rotation + DB storage) if needed.

3) Stateless auth setup
- No server-side session store.
- Add refresh token flow if access token TTL is short.

4) Cookies
- Set `access_token` (IMS JWT) cookie (httpOnly, sameSite=strict, secure in prod).
- Set `arms_token` cookie if required by backend for subsequent ARMS calls.
- If frontend already stores ARMS token, confirm if backend needs a copy.

5) Token validation for protected routes
- Validate JWT from cookie.
- Enforce expiry (1h).
- Remove session checks and rely on JWT claims.

## Inputs from Frontend
- ARMS token is pre-issued by frontend SSO flow.
- Frontend will call `/auth/login` with ARMS token.
- Confirm exact transport: `auth` query param vs JSON body.

## SSO Endpoint Notes (from SSO documentation)
- SSO login entrypoints: `/login` (v1) and `/v2/login` (v2).
- v1 login redirect includes query params: `app-token`, `login-hint`, `expires-at`.
- v2 login redirect includes no query params; token data stored in HTTP-only cookie and fetched via `/login-token`.
- Refresh endpoint: `/refresh-token` (POST with refresh token in body).
- Logout endpoint: `/logout` with `login-hint`, `account`, `redirect-url` query params.
- Client app must clear its own cookies on logout (SSO does not clear client cookies).

## Deliverables
- Auth controller endpoints (login/logout).
- Auth service handling ARMS token verification and user upsert.
- JWT auth filter/guard.
- Cookie config constants.

## Notes
- Do not implement ARMS username/password auth in backend; backend trusts the token.
- Keep profile data in sync on every login.
- Include clear errors for invalid/expired ARMS token.
