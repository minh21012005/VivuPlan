# VivuPlan Architecture

## Backend

The backend is a layered Spring Boot application.

- Controllers define REST endpoints and map request/response DTOs.
- Services contain business logic and orchestration.
- Repositories provide persistence access through Spring Data JPA.
- Entities define persisted state.
- Configuration classes define security, CORS, mail, billing, AI, and external
  service settings.
- Tests cover service behavior, security filters, AI quality, billing, weather,
  prompts, seed governance, and application context.

### Key Backend Services

- `TripService`: trip lifecycle, validation, AI orchestration, enrichment,
  coordinate resolution, persistence, warnings, sharing, activity editing, and
  regeneration preview/apply.
- `AiService`: Gemini prompts, response contracts, retries, JSON parsing,
  quality policy, token/cost tracking.
- `DestinationSuggestionService`: AI destination suggestion flow, caching,
  cooldown, validation, and credit consumption.
- `PlacePlanningService`: verified place context scoring and AI activity
  enrichment.
- `ActivityCoordinateResolverService`: coordinate validation, cache use,
  geocoding, and confidence/source assignment.
- `BillingService`: wallet credits, credit ledgers, payment orders, SePay
  webhooks, package fulfillment, scheduled expiration.
- `AuthService`: local registration, OTP, password reset, Google auth, profile
  updates, admin bootstrap.
- `DestinationService` and `WeatherService`: destination catalog, geocoding,
  daily forecast, current weather, cache behavior.
- `AdminService`: dashboard stats, user/trip moderation, transactions, AI cost
  reporting.

## Frontend

The frontend is a Next.js App Router application.

- `app/layout.tsx` provides metadata and wraps the app in auth and billing
  providers.
- `lib/api.ts` centralizes HTTP calls, DTOs, enum types, auth headers, and API
  error parsing.
- `contexts/auth-context.tsx` owns token persistence and current user loading.
- `contexts/billing-context.tsx` owns wallet state and refresh sequencing.
- Route pages compose shared UI and feature components.
- `app/globals.css` contains the current design system and many feature styles.

### Important Frontend Areas

- Planner: `app/plan/page.tsx`.
- Trip list: `app/itinerary/page.tsx`.
- Trip detail: `app/itinerary/[id]/page.tsx`.
- Maps: `components/travel/DayRouteMap.tsx`.
- Weather UI: `components/travel/*Weather*`, `hooks/use-weather.ts`,
  `lib/weather-utils.ts`.
- Pricing and purchase: `app/pricing/page.tsx`,
  `components/billing/PurchaseModal.tsx`.
- Admin: `app/admin/` and nested admin detail routes.

## Core Flows

### Trip Generation

1. Frontend validates planner input and sends a generation request.
2. Backend authenticates the user.
3. `TripService` validates destination, dates, days, travelers, budget, and
   prompt fields.
4. Wallet plan credit is checked before AI work.
5. Weather and verified place context are prepared.
6. `AiService` calls Gemini and validates the structured response.
7. Activities are enriched with verified places and coordinates.
8. Costs and warnings are normalized.
9. Trip is saved.
10. Plan credit is consumed after successful save.
11. Frontend receives the trip DTO and navigates to itinerary detail.

### Destination Suggestions

1. Frontend asks for suggestions when the planner has no destination.
2. Backend validates and sanitizes request text.
3. Suggestion credit and cooldown/cache behavior are applied.
4. Gemini returns exactly three structured suggestions.
5. Backend validates labels and catalog matches.
6. Suggestion credit is consumed only after a successful response.

### Day Regeneration

1. User requests a day regeneration preview with an instruction.
2. Backend validates ownership, prompt text, and edit credit.
3. Backend gives each activity in the target day a request-scoped
   `sourceActivityRef`. Valid references are matched first and are authoritative
   even when the replacement has completely different text.
4. Gemini regenerates one day within the existing trip context and returns the
   reference of the old activity that each output activity keeps or replaces.
5. Missing, unknown, or duplicated references are discarded without failing
   the preview. Remaining activities are matched by exact place identifiers,
   normalized name/location, then a language-independent semantic score using
   token overlap, character trigrams, and token bigrams.
6. Semantic candidates must pass both a confidence threshold and a reciprocal
   ambiguity margin before deterministic maximum-weight one-to-one matching.
   The matcher does not use destination word lists or force a match merely
   because type and time are similar.
7. Backend enriches, resolves coordinates, normalizes, validates, and computes
   the activity diff without pairing activities by list index.
8. Edit credit is consumed during preview.
9. Preview stores actionable user-facing changes, unchanged activities,
   optional trusted metadata patches, and the original-day fingerprint.
10. Apply always starts from the persisted day, merges selected modified, added,
   or removed activities by change ID, applies trusted metadata patches decided
   by backend policy, rejects stale previews, and does not consume another edit
   credit.
11. Only a full actionable apply persists the proposal request-fulfillment
   warnings. Partial and metadata-only apply preserve the trip's current
   warnings.

### Billing

1. Users have wallets with plan, edit, and suggestion credits.
2. Packages define credit amounts and prices.
3. Orders are created from package IDs.
4. Webhooks are validated when secrets are configured.
5. Transactions are idempotent by SePay ID.
6. Fulfillment writes ledgers and wallet changes.

### Auth

1. Local registration uses OTP verification.
2. Local password reset uses OTP verification.
3. Google login verifies the Google ID token with the configured client ID.
4. JWTs include role information.
5. The frontend stores the token in localStorage as `vp_token`.

## External Services

- Gemini: itinerary generation, destination suggestions, day regeneration.
- Google OAuth: sign-in token verification.
- SMTP: OTP email delivery.
- Open-Meteo: forecast and current weather.
- Nominatim: geocoding and coordinate resolution.
- SePay-compatible webhook: billing transaction notification.

External services should be wrapped by existing service boundaries. Do not call
them directly from unrelated controllers or frontend components.
