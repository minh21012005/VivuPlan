# VivuPlan Backend Agent Guide

This package is a Spring Boot 3 API using Java 21, Maven, Spring Security,
Spring Data JPA, PostgreSQL, Gemini, Google OAuth, mail, weather/geocoding
services, and billing credits.

## Main Entry Points

- Application: `src/main/java/com/vivuplan/vivuplan_be/VivuplanBeApplication.java`
- Controllers: `controller/`
- Services: `service/`
- Entities and enums: `model/`
- DTOs: `dto/`
- Repositories: `repository/`
- Configuration: `config/`
- Seed data: `src/main/resources/data/`
- Tests: `src/test/java/com/vivuplan/vivuplan_be/`

## Backend Rules

- Keep business logic in services. Controllers should validate request shape,
  delegate, and return DTOs.
- Preserve stateless JWT security. Do not add new public endpoints without
  checking `SecurityConfig`.
- Use repository methods and transactions intentionally. For wallet/order
  mutations, preserve locking and idempotency.
- Throw `IllegalArgumentException`, `ResponseStatusException`, domain
  exceptions, or `AiGenerationException` consistently so
  `GlobalExceptionHandler` can format responses.
- Do not expose entities directly from new endpoints. Use DTOs.
- Keep API enum values stable unless frontend mappings and persisted data are
  migrated at the same time.
- User-facing strings returned by the API are generally Vietnamese. Keep UTF-8.

## AI Planning Rules

- `TripService` orchestrates trip generation, enrichment, coordinate resolution,
  persistence, warning handling, and credit consumption.
- `AiService` owns Gemini prompts, response contracts, JSON parsing, retries,
  usage logging, and itinerary quality checks.
- `UserPromptGuardService` must remain in front of user-provided prompt text.
- `PlacePlanningService` provides verified place context and enriches AI output.
- `ActivityCoordinateResolverService` validates and resolves coordinates.
- Required costs must be modeled as costs, not hidden in notes.
- AI responses must stay compatible with the contracts documented in
  `docs/AI_TRAVEL_PLANNING.md`.

## Billing Rules

- Use `BillingService` for wallet, credit, order, and ledger operations.
- Check credit before costly AI work.
- Consume credit only after the successful operation point already used by the
  current flow.
- Keep SePay webhook handling idempotent and signature/timestamp checks intact
  when configured.

## Data Rules

- `DataInitializer` can sync destinations and places from JSON seed files.
- Do not casually change seed IDs, slugs, enum strings, or verified place data.
- Validate seed changes with existing seed governance tests where applicable.

## Verification

Preferred command:

```powershell
.\mvnw.cmd test
```

For narrow backend changes, run the relevant test class first when practical,
then run the full Maven test suite for shared behavior, AI, billing, auth,
security, or persistence changes.

