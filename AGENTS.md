# VivuPlan Agent Guide

This file is the project-wide contract for AI-assisted coding in VivuPlan.
Read it before editing, then read the package-level `AGENTS.md` file for the
area you touch.

## Project Snapshot

VivuPlan is a Vietnam-focused travel planning app.

- Backend: Spring Boot 3, Java 21, Maven, PostgreSQL, JWT security, Google
  OAuth, Gemini itinerary generation, SePay-style billing credits, weather and
  geocoding integrations.
- Frontend: Next.js 16 App Router, React 19, TypeScript strict mode, Tailwind
  CSS 4, Radix primitives, Leaflet maps, Vietnamese user-facing UI.
- Main product flows: destination discovery, AI trip generation, itinerary
  editing, day regeneration previews, public sharing, credit purchase, and admin
  operations.

## Read Order For Agents

1. `AGENTS.md` at repo root.
2. `vivuplan-be/AGENTS.md` or `vivuplan-fe/AGENTS.md`, depending on scope.
3. Relevant docs in `docs/`.
4. The actual code and tests. The code is the final source of truth.

## Repository Map

- `vivuplan-be/`: Spring Boot API, domain model, AI orchestration, billing,
  auth, admin, seed data, tests.
- `vivuplan-fe/`: Next.js app, route pages, UI components, API client, auth and
  billing contexts.
- `docs/`: durable architecture and development context for humans and agents.
- `.github/`: GitHub-specific instructions and workflows.

## Non-Negotiables

- Do not invent product behavior. Inspect code, tests, config, and docs before
  changing behavior.
- Keep user-facing application copy in Vietnamese unless the surrounding screen
  is intentionally English.
- Preserve UTF-8. Do not corrupt Vietnamese accents. Be careful with PowerShell
  output because mojibake in the console does not always mean the file is bad.
- Never commit secrets. Use `.env.example` as the contract and environment
  variables for real values.
- Backend DTO changes must be reflected in `vivuplan-fe/lib/api.ts` and all
  affected consumers.
- API errors should stay predictable. Prefer structured errors from controllers
  and `GlobalExceptionHandler`.
- Do not bypass auth, role checks, ownership checks, wallet checks, prompt
  guards, AI quality checks, or billing idempotency.
- Do not hide mandatory travel costs in notes. Costs shown in itineraries should
  reflect the group-level VND total expected by the backend.
- Seed data is application behavior. Treat files under
  `vivuplan-be/src/main/resources/data/` with the same care as code.
- Keep edits scoped. Avoid large refactors unless the requested change requires
  them.

## Core Invariants

- A trip requires an authenticated user, a destination, valid dates, valid
  traveler count, valid budget, and available plan credit before AI generation.
- Plan credit is consumed only after a trip is successfully saved.
- Edit credit is consumed by day regeneration preview, not by applying an
  existing preview.
- Suggestion credit is consumed only after successful destination suggestions.
- Admin actions must protect the last admin and prevent self-demotion or
  self-lock where implemented.
- Public trip access is allowed only through the explicit public/share flows.
- Weather, verified places, geocoding, and AI coordinates are helpful context,
  not reasons to guess unsafe data.

## Verification Commands

Backend:

```powershell
cd vivuplan-be
.\mvnw.cmd test
```

Frontend:

```powershell
cd vivuplan-fe
npm run lint
npm run build
```

Local frontend server:

```powershell
cd vivuplan-fe
npm run dev -- --hostname 127.0.0.1 --port 3000
```

Use the narrowest useful verification when the change is small, and run the
broader command set when touching shared contracts, auth, billing, AI planning,
or route-level UI.

## Documentation Index

- `docs/PROJECT_CONTEXT.md`: product context, users, system boundaries.
- `docs/ARCHITECTURE.md`: backend/frontend architecture and important flows.
- `docs/API_CONTRACT.md`: public API shape and cross-stack DTO expectations.
- `docs/AI_TRAVEL_PLANNING.md`: AI itinerary rules, quality gates, and prompt
  contract.
- `docs/DATA_MODEL.md`: persisted entities, relationships, enums, and seed data.
- `docs/SECURITY_AND_BILLING.md`: auth, roles, credits, orders, and webhook
  invariants.
- `docs/DEVELOPMENT_GUIDE.md`: local setup, standards, testing, and common
  pitfalls.
