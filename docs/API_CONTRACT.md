# VivuPlan API Contract

This file summarizes the REST contract used by the frontend. The exact DTO
definitions live in backend `dto/` classes and frontend `vivuplan-fe/lib/api.ts`.

## Base URL

Frontend reads the API base URL from:

```text
NEXT_PUBLIC_API_URL
```

The frontend code falls back to `http://localhost:8080`.

## Authentication

Protected endpoints require:

```http
Authorization: Bearer <jwt>
```

JWT claims include user identity and roles. The backend supports current role
claims and some legacy role handling.

## Public Endpoints

- `POST /api/auth/register/request-otp`
- `POST /api/auth/register/verify`
- `POST /api/auth/password/forgot/request-otp`
- `POST /api/auth/password/forgot/verify`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/logout`
- `GET /api/billing/packages`
- `POST /api/billing/sepay/webhook`
- `GET /api/trips/public/share/**`
- `GET /api/destinations/**`
- `GET /actuator/health`

Admin endpoints require `ROLE_ADMIN`. Other application endpoints require an
authenticated user unless explicitly made public in `SecurityConfig`.

## Response And Error Conventions

The frontend API client expects JSON responses.

Common error shapes:

```json
{ "error": "message" }
```

```json
{ "error": "message", "code": "BILLING_CODE" }
```

```json
{ "error": "Validation failed", "details": ["field: message"] }
```

Frontend `handleResponse` prefers validation details, then `error`, then billing
`code`. Keep this behavior in mind when changing backend error responses.

## Auth Endpoints

Primary frontend methods are grouped in `authApi`:

- Request registration OTP.
- Verify registration.
- Login.
- Google login.
- Load current user.
- Update local profile.
- Change local password.
- Request password reset OTP.
- Verify password reset.
- Logout.

Provider-specific behavior matters: local users can update local profile and
password; Google users have restrictions enforced by backend and frontend.

## Trip Endpoints

Primary frontend methods are grouped in `tripApi`:

- `POST /api/trips/generate`: generate and save a trip.
- `POST /api/trips/destination-suggestions`: suggest destinations.
- `GET /api/trips`: list user trips.
- `GET /api/trips/{id}`: read an owned trip by numeric id.
- `DELETE /api/trips/{id}`: delete owned trip.
- `PATCH /api/trips/{id}/visibility`: make the owned trip public/shareable.
  This operation is idempotent; calling it again must not make the trip private.
- `PATCH /api/trips/{id}/status`: update status.
- Activity add/update/delete endpoints under a trip/day.
- Preview and apply day regeneration.
- Public share-code read. There is no public trip listing endpoint; shared
  trips are link-only and readable only through their share code while sharing
  is enabled.

Trip responses include itinerary days and activities. Activity estimated costs
are group-level VND values.

## Destination Endpoints

Primary frontend methods are grouped in `destinationApi`:

- List destinations with optional search, region, and featured filters.
- Get featured destinations.
- Get destination by slug.
- Geocode a destination or text.
- Get forecast weather.
- Get current weather.

Weather and geocoding failures should generally be non-fatal in the frontend.

## Billing Endpoints

Primary frontend methods are grouped in `billingApi`:

- Get public packages.
- Get authenticated wallet.
- Create order.
- Get order.
- Cancel order.

Billing errors may include machine-readable codes. The frontend uses HTTP 402 to
open purchase flows in planner and regeneration screens.

## Admin Endpoints

Primary frontend methods are grouped in `adminApi`:

- Dashboard stats.
- Users list/detail/role/lock.
- Trips list/detail.
- Coordinate resolution dry-run/apply.
- Transactions.
- AI cost summary, daily aggregation, and events.

Do not weaken admin role checks when adding admin endpoints.

## Cross-Stack Change Checklist

When changing API shape:

1. Update backend DTOs and service/controller logic.
2. Update backend tests.
3. Update `vivuplan-fe/lib/api.ts` types and methods.
4. Update affected route/components.
5. Update this file if the contract changed.
6. Run backend tests and frontend lint/build when practical.
