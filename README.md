# VivuPlan

VivuPlan is a Vietnam-focused AI travel planning application with a Spring Boot
backend and a Next.js frontend.

## Repository

- `vivuplan-be/`: backend API, AI planning, billing, auth, admin, seed data.
- `vivuplan-fe/`: frontend app, planner, itinerary, maps, billing, admin UI.
- `docs/`: architecture, API, AI planning, data model, security, and development
  guides.

## Start Here

For AI-assisted development, read:

1. `AGENTS.md`
2. `vivuplan-be/AGENTS.md` or `vivuplan-fe/AGENTS.md`
3. Relevant files under `docs/`

For setup:

- Backend: `vivuplan-be/README.md`
- Frontend: `vivuplan-fe/README.md`
- Environment examples: `vivuplan-be/.env.example` and
  `vivuplan-fe/.env.example`

## Verification

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

