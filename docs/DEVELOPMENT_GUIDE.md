# VivuPlan Development Guide

## Prerequisites

- Java 21
- Maven wrapper from the repository
- Node.js compatible with the frontend package
- npm
- PostgreSQL for local full-stack runtime

## Environment

Backend environment variables are documented in:

```text
vivuplan-be/.env.example
```

Frontend environment variables are documented in:

```text
vivuplan-fe/.env.example
```

Never store real secrets in the repository.

## Backend Commands

```powershell
cd vivuplan-be
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Useful package command:

```powershell
.\mvnw.cmd -DskipTests package
```

## Frontend Commands

```powershell
cd vivuplan-fe
npm install
npm run lint
npm run build
npm run dev -- --hostname 127.0.0.1 --port 3000
```

## Coding Standards

### General

- Keep changes narrow and behavior-focused.
- Prefer existing helpers, components, services, and patterns.
- Update docs when durable behavior changes.
- Keep UTF-8 source encoding intact.
- Preserve Vietnamese UI copy.
- Do not add dependencies without a clear reason.

### Backend

- Java 21 and Spring Boot conventions.
- Services own business rules.
- Controllers should remain thin.
- DTOs define API surface.
- Repositories should not accumulate business logic.
- Add tests near the behavior being changed.
- Keep auth, ownership, admin, and wallet checks close to existing patterns.
- Keep external service calls behind services.

### Frontend

- TypeScript strict mode should stay clean.
- Keep API types in `lib/api.ts`.
- Reuse shared UI and existing route patterns.
- Prefer accessible Radix primitives for complex controls.
- Keep maps client-only.
- Keep weather and optional enrichments non-blocking.
- Avoid broad CSS rewrites.

## Database And Seeds

The backend uses JPA entities and seed JSON files for destinations and places.
The data initializer can sync seed data when enabled.

Be careful when changing:

- Destination slugs.
- Place names, aliases, coordinates, and types.
- Enum strings.
- Relationship mappings.
- Cascade behavior.

There is no documented migration framework in the current repo. If schema
changes are needed, document the operational impact and update tests.

## Testing Strategy

Run focused tests first while developing, then broader verification for shared
areas.

High-risk areas that need strong verification:

- Auth and role checks.
- Billing, credits, orders, ledgers, and webhook idempotency.
- AI prompt contracts and quality checks.
- Trip generation and regeneration flows.
- DTO changes crossing backend/frontend.
- Coordinate resolution and seed data.
- Admin moderation actions.

## Common Pitfalls

- PowerShell can display UTF-8 Vietnamese text incorrectly. Inspect files with
  an editor before assuming content is corrupt.
- Frontend validation mirrors backend validation but backend is authoritative.
- Public trip pages and authenticated trip detail have overlapping behavior.
- Day regeneration preview already consumes edit credit.
- Applying a regeneration preview must not consume credit again.
- Weather and geocoding are useful but should not block unrelated workflows.
- Updating backend DTOs without updating `lib/api.ts` breaks the frontend.

## Change Checklist

Before finishing a code change:

1. Check the relevant `AGENTS.md`.
2. Inspect current tests around the behavior.
3. Make the smallest coherent change.
4. Update cross-stack DTOs if needed.
5. Add or update tests for changed rules.
6. Run focused verification.
7. Run full backend/frontend verification for shared or risky changes.
8. Update docs if the durable behavior changed.

