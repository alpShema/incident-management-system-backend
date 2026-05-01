# Login Flow — Frontend Reference

## Overview

The frontend obtains an **ARMS SSO token** from the ARMS identity provider, then exchanges it with this backend for Hilfe JWT tokens.

---

## `POST /auth/login`

### Request

```http
POST /auth/login
Content-Type: application/json
```

```json
{
  "armsToken": "<JWT from ARMS SSO>"
}
```

### Response — `200 OK`

**Body:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 86400
}
```

> `accessTokenExpiresIn` and `refreshTokenExpiresIn` are in **seconds**.

**Cookies set automatically** (all `HttpOnly`, `SameSite=Strict`, `Path=/`):

| Cookie | Contents | TTL |
|---|---|---|
| `access_token` | Hilfe access JWT | `accessTokenExpiresIn` |
| `refresh_token` | Hilfe refresh JWT | `refreshTokenExpiresIn` |
| `arms_token` | Original ARMS token | `accessTokenExpiresIn` |

### Error Responses

| Status | Cause |
|---|---|
| `401` | ARMS token is invalid or expired |
| `400` | Malformed request body |

---

## Subsequent Authenticated Requests

The browser sends the `access_token` cookie automatically. No `Authorization` header is needed.

---

## Related Endpoints

### `POST /auth/refresh-token`

> **Not yet implemented** — returns `500`.

```json
{ "refreshToken": "<refresh JWT>" }
```

### `POST /auth/logout`

Clears all three auth cookies and returns `204 No Content`.

```json
{ "refreshToken": "<refresh JWT>" }
```
