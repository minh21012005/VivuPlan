# VivuPlan Project Context

## Product

VivuPlan helps users plan short Vietnam trips with AI-assisted itineraries.
The application combines destination discovery, realistic itinerary generation,
weather context, verified place data, map visualization, manual editing, public
sharing, paid credit packages, and admin operations.

The core user outcome is a usable trip plan, not just a generated text answer.
Activities must have realistic times, locations, costs, day grouping, and enough
specificity to be useful on the ground.

## Primary Users

- Travelers planning Vietnam trips.
- Authenticated users managing saved trips and credits.
- Admin users monitoring users, trips, transactions, coordinates, and AI costs.

## Current Architecture At A Glance

- `vivuplan-be`: REST API and business logic.
- `vivuplan-fe`: web client and user experience.
- Database: PostgreSQL in normal runtime. Tests use H2.
- External services: Gemini, Google OAuth, SMTP, Open-Meteo, Nominatim,
  SePay-compatible webhooks.

## Product Surface

- Home and destination discovery.
- Planner form with validation and destination suggestions.
- AI trip generation with weather and verified place context.
- Saved itinerary list and itinerary detail.
- Manual activity CRUD.
- Day regeneration preview and apply.
- Public share links and public feed.
- Pricing packages and wallet state.
- Admin dashboard, users, trips, transactions, AI cost tracking.

## Source Of Truth

Use this priority order:

1. Code and tests.
2. `AGENTS.md` files.
3. `docs/`.
4. README files and environment examples.

When docs and code disagree, inspect the current code and update docs in the
same change if the documented behavior is expected to remain true.

## Language And Encoding

- Application UI copy is Vietnamese.
- Docs for agents are mostly English ASCII to reduce tooling and console
  encoding problems.
- Source files and UI strings must remain UTF-8.
- Do not "fix" Vietnamese text just because PowerShell displays mojibake.

## Important Limits

- Trip length is capped by backend DTO validation. Current frontend validation
  mirrors a maximum of 7 days.
- Traveler count is capped by backend validation. Current frontend validation
  mirrors a maximum of 10 travelers.
- Dates cannot be in the past and cannot exceed the backend future limit.
- Budget values are sanity-checked on the backend.
- Prompt input fields have explicit length and safety checks.

## Where To Change Things

- API behavior: backend controller, DTO, service, tests, then frontend
  `lib/api.ts`.
- Trip generation behavior: `TripService`, `AiService`,
  `PlacePlanningService`, coordinate resolver, related tests.
- Credit behavior: `BillingService`, billing repositories/entities, frontend
  billing context and purchase modal.
- Auth behavior: `AuthService`, `SecurityConfig`, JWT filter/util, frontend auth
  context and protected pages.
- Admin behavior: `AdminService`, `AdminController`, admin frontend routes.
- Visual behavior: route component, shared component, and only the minimal
  necessary CSS.

