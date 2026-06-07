# AI Travel Planning Guide

This guide documents the AI itinerary behavior that should be preserved unless a
task explicitly changes it.

## Ownership

- `TripService` owns trip workflow and persistence.
- `AiService` owns Gemini calls, prompts, response contracts, retries, usage
  logging, and quality validation.
- `PlacePlanningService` owns verified place context and enrichment.
- `ActivityCoordinateResolverService` owns coordinate validation and resolution.
- `UserPromptGuardService` owns prompt safety and input normalization.

## AI Operations

The system currently uses AI for:

- Full itinerary generation.
- Destination suggestions.
- One-day itinerary regeneration.

Each operation has its own structured JSON contract and validation path. Do not
reuse one operation contract for another unless all callers and tests are
updated.

## Full Itinerary Contract

The full itinerary response must be a JSON object containing:

- `itinerary`: an array of day objects.
- `requestFulfillment`: structured notes about how the user request was handled.

The trip must match the requested number of days. Each day should contain
realistic activities with valid time values, activity types, names, locations,
durations, estimated costs, and notes where useful.

## Destination Suggestion Contract

Destination suggestions must return exactly three structured suggestions.
Backend validation enforces count, label quality, and basic content constraints.
Catalog matches are preferred when available, but AI suggestions can still be
handled as non-catalog destinations.

## Day Regeneration Contract

The day regeneration response must contain:

- `day`: the regenerated day.
- `requestFulfillment`: structured notes for the regeneration request.

The preview consumes edit credit. Applying an already-created preview must not
consume another edit credit.

## Quality Expectations

Itineraries should be:

- Vietnam-specific and destination-specific.
- Time-realistic.
- Weather-aware when weather context exists.
- Cost-realistic for the full traveler group.
- Specific enough to act on.
- Safe about uncertain coordinates.
- Honest when a user request cannot be fully satisfied.

Avoid:

- Generic activity names that could apply to any city.
- Repeated filler activities.
- Missing required intercity transport, rental, ticket, lodging, or tour costs.
- Hiding required costs in activity notes.
- Guessing exact coordinates for unverified places.
- Ignoring explicit user constraints.

## Cost Rules

- `estimatedCost` is a VND value for the whole group unless an existing contract
  says otherwise.
- Required costs belong in costs, not only in notes.
- Round-trip or bundled transport costs should not be double-counted.
- Local transfers to airports, train stations, or bus stations are not the same
  as an intercity paid leg, but a hidden paid intercity leg still needs a cost.
- Rental vehicle costs and major ticket/tour costs should be represented.

## Coordinate Rules

- Verified place coordinates are preferred.
- AI-provided coordinates must be validated.
- Geocoding can be used through the resolver and cache.
- Transport activities can be skipped for coordinate resolution where the code
  already does so.
- If coordinates are uncertain, omit or lower confidence rather than inventing
  precision.

## Weather Rules

- Weather context should influence outdoor and flexible activities.
- Severe weather should create warnings or safer alternatives where appropriate.
- Weather failures must not make core trip viewing unusable.
- Keep Open-Meteo/Nominatim integration inside existing backend services and
  frontend hooks/components.

## Prompt Safety

- User text passes through `UserPromptGuardService`.
- Keep prompt injection and off-topic detection in place.
- Do not add raw user text to prompts without sanitization.
- Keep max lengths aligned across backend and frontend validation.

## Request Fulfillment

Request fulfillment notes should explain meaningful tradeoffs:

- Requests that were satisfied.
- Requests that could not be fully satisfied.
- Safety, weather, budget, or feasibility adjustments.

Persisted warnings should remain curated and useful. Avoid storing noisy,
duplicative, or irrelevant AI commentary.

## Testing Guidance

When changing AI itinerary behavior, add or update focused tests around:

- Prompt contract expectations.
- JSON parsing and invalid response handling.
- Quality policy edge cases.
- Cost normalization.
- Transport cost detection.
- Prompt guard behavior.
- Coordinate enrichment and validation.
- Credit consumption timing.

