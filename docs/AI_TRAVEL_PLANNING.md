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

### Internal activity references

For day regeneration only, the backend assigns each activity in the target day
a temporary reference such as `src-1`. The target-day prompt includes this
`sourceActivityRef` together with the activity's user-facing time, name, type,
location, duration, estimated cost, and note. Other days remain reduced context
and do not receive references.

The model should copy the old activity's reference when keeping, editing, or
replacing it. A completely new activity uses `null`; an omitted reference means
the old activity was removed. A reference may appear at most once. For
split/merge responses, only the primary successor or predecessor carries the
reference.

Replacing a generic activity with a more specific named venue or experience
keeps the original reference. For example, a generic lunch, dinner, riverside
cafe, sightseeing stop, or transfer refined into a concrete venue remains a
replacement rather than a completely new activity.

These references are linkage hints scoped to one AI request. They are not
database IDs, are not persisted, and are never returned through the public trip
or preview API. The same reference context is reused for the one existing
contract or quality retry.

Missing, unknown, or duplicated references do not fail the preview and do not
trigger an extra Gemini retry. The parser discards invalid references and lets
the deterministic exact/semantic matcher handle the remaining activities.

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

## Prompt Self-Validation

Prompts should include a short final self-check before the JSON schema. The
model should use it silently and must not output the checklist.

The self-check should verify:

- The response is exactly one JSON object with the required top-level keys.
- Array counts match the operation contract.
- Required fields, enum-like values, times, activity types, and basic cost
  representation match the schema.
- Required paid items are not represented only in notes with a zero or missing
  cost.

Do not repeat broad semantic planning rules such as weather interpretation,
style, pacing, destination selection, or request fulfillment inside the final
self-check. Those rules belong in the main prompt and application-side quality
validation. Keeping the final checklist structural reduces recency bias toward
one planning concern.

Self-validation is only a model instruction. Backend parsing, quality checks,
normalization, retries, and tests remain the real enforcement layer.

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
- Daily `outdoorRiskLevel` is a planning summary, not the worst raw WMO code of
  the day. When complete morning and afternoon windows exist, mark the day
  severe only when both daytime windows are severe; mixed daytime conditions
  remain rain-flexible.
- Keep window-level risk authoritative for activities scheduled in that window.
  A rain-flexible day may still contain a severe afternoon or evening window.
- Safety-sensitive outdoor activities such as trekking, climbing, caving,
  canyoning, boat/island trips, diving, paddling, and paragliding are not
  automatically banned on a rain-flexible day. The full activity, including
  access and return time, must fit suitable non-severe forecast windows.
- Move, shorten, substitute, or omit those activities when relevant hazards such
  as thunderstorms, heavy rain, strong wind, flooding, rough seas, or slippery
  trails make them unsafe. Travelers should reconfirm local route/site
  conditions and operator status where relevant.
- Preserve AI `WEATHER_SAFETY` explanations after the normal response-contract
  parsing. Do not silently rewrite or suppress them with a second simplified
  weather interpretation in `TripService`; prompt context and AI quality rules
  own the semantic explanation.
- Do not turn itinerary text into a forecast bulletin or blanket weather
  advisory, repeat exact forecast values, or present predicted conditions as
  certain facts. Concise natural activity-specific context such as "nếu thời
  tiết thuận" or advice to reconfirm route, site, water-level, sea, or operator
  conditions is allowed when useful. Do not repeat generic weather disclaimers
  across activities.
- Backend quality validation must not classify weather words in day titles,
  summaries, activity names, or notes as deterministic failures. Keyword
  matching cannot reliably distinguish a forecast claim from useful outdoor
  context and must not trigger an AI retry.
- Explain weather-driven omissions, substitutions, or material weakening of a
  user request or destination-signature experience through
  `requestFulfillment`.
- Persist AI `WEATHER_SAFETY` explanations in `Trip.aiWarnings` with the other
  request-fulfillment messages. They explain why that saved itinerary was
  constructed, so users must still see them after refresh. Current frontend
  weather advisories remain a separate live-weather layer.
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
