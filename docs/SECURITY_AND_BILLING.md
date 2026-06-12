# Security And Billing Guide

This guide captures behavior that must remain hard to accidentally bypass.

## Authentication

The backend uses stateless JWT authentication.

- CSRF is disabled for the API.
- CORS origins are read from configuration.
- JWT validation happens in the auth filter.
- The authenticated principal is the user ID.
- Authorities come from JWT role claims.
- The filter also checks that the user still exists and is active.

Frontend token storage:

- The token key is `vp_token`.
- Legacy `vp_user` cleanup exists in the auth context.
- Logout clears client state and returns to the home route.

## Public Endpoints

The source of truth is `SecurityConfig`.

Generally public:

- Auth registration, login, Google login, logout, and password reset endpoints.
- Billing packages.
- SePay webhook.
- Public trip read/feed endpoints.
- Destination endpoints.
- Health check.

Everything else should be treated as authenticated unless `SecurityConfig`
explicitly says otherwise.

## Roles

- Normal users have `USER`.
- Admin users have `ADMIN`.
- Admin endpoints require `ROLE_ADMIN`.

Admin mutations must preserve current self-protection and last-admin protection
rules. Do not add admin shortcuts that bypass `AdminService`.

## Ownership

Trip, activity, and regeneration operations must verify ownership unless the
flow is explicitly public. Public visibility and share codes are intentional
access mechanisms, not a reason to skip ownership checks elsewhere.

A share code grants read access only while the trip is public. Switching a trip
back to private must immediately make its existing share URL unavailable.

JWT identity remains token-based, but account lock state and authorities are
resolved from the current user record on every authenticated request. Role
changes therefore take effect without waiting for an older token to expire.

## Local And Google Accounts

Local accounts:

- Register through OTP verification.
- New local registrations reject disposable email domains before OTP delivery.
  The denylist is vendored under backend resources.
- Can use password reset OTP.
- Can update local profile and password where supported.

Google accounts:

- Login through verified Google ID token.
- Have provider-specific restrictions for manual profile/password changes.

Keep provider restrictions aligned between backend and frontend.

## Credit Types

Wallets track:

- Plan credits for full trip generation.
- Edit credits for day regeneration preview.
- Suggestion credits for destination suggestions.

Credit checks happen before costly or state-changing work. Consumption happens
only at the successful point defined by each flow.

## Credit Consumption Points

- Trip generation: check before AI, consume after the trip is saved.
- Day regeneration: check before AI, consume after a valid preview is created.
- Applying regeneration: do not consume another edit credit.
- Destination suggestions: consume after successful suggestions.
- Signup credits: granted through billing service behavior.

## Payment Orders

Orders are created from package IDs managed by the package catalog. Package
prices and credit amounts are product behavior and should not be changed without
updating frontend displays and tests.

Order status changes must preserve:

- Pending order handling.
- Paid fulfillment.
- Underpaid handling.
- Expiration.
- Cancellation.
- Idempotent fulfillment.

## Webhook Rules

SePay-compatible webhooks should remain:

- Signature-checked when a secret is configured.
- Timestamp-checked when configured.
- Idempotent by transaction ID.
- Able to find orders by configured order code/content conventions.
- Safe against repeated delivery.

Do not trust webhook payloads blindly.

## Error Handling

Billing errors may include HTTP 402 and machine-readable codes. Frontend planner
and regeneration flows use this to open purchase modals.

When adding new billing failures, update:

- Backend exception/code behavior.
- Frontend `ApiError` handling if needed.
- Affected purchase or wallet UI.
- Tests.
