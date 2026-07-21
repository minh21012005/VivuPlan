# VivuPlan Data Model

This is a practical map of the persisted domain. Entity classes in
`vivuplan-be/src/main/java/com/vivuplan/vivuplan_be/model/` remain the exact
source of truth.

## User And Roles

### `User`

Represents an account.

Important fields:

- `id`
- `name`
- `email`
- `password`
- `avatarUrl`
- `googleId`
- `roles`
- `provider`
- `emailVerified`
- `accountLocked`
- `trips`

Important behavior:

- Supports local and Google providers.
- Roles are many-to-many.
- Locked users cannot authenticate successfully.
- Helper methods expose role names and role checks.

### `Role`

Represents role authorities such as `USER` and `ADMIN`.

Admin behavior must preserve guards against removing or locking the last usable
admin where the service already enforces that rule.

## Trip Planning

### `Trip`

Represents a saved travel plan.

Important relationships:

- Many trips belong to one user.
- A trip has many itinerary days.
- Itinerary days cascade with the trip.

Important fields:

- Destination and departure text.
- Trip dates and day count.
- Budget fields and budget mode.
- Traveler count.
- Style, group type, and transport mode.
- AI warnings.
- Public sharing fields.
- Status.

Important enums:

- `BudgetMode`: `PER_PERSON`, `TOTAL`.
- `TripStatus`: `DRAFT`, `PLANNED`, `COMPLETED`.
- `TravelStyle`: `ADVENTURE`, `RELAXING`, `CULTURAL`, `FOODIE`.
- `GroupType`: `SOLO`, `COUPLE`, `FRIENDS`, `FAMILY`.
- `TransportMode`: `PERSONAL_MOTORBIKE`, `PERSONAL_CAR`,
  `RENTAL_MOTORBIKE`, `RENTAL_CAR`, `TAXI_GRAB`, `BUS`, `PLANE`, `TRAIN`,
  `WALKING`, `MIXED`.

### TripInitialSnapshot

Stores one immutable audit baseline for a newly generated trip.

Important fields:

- Unique trip_id.
- normalizedSnapshot: the accepted trip DTO after verified-place enrichment,
  coordinate resolution, cost normalization, budget calculation, and warnings.
- AI request ID, model, and creation time.

The row is created in the same transaction as trip persistence and plan-credit
consumption. It is admin-only, is not changed by itinerary editing or day
regeneration, and cascades on trip deletion. Legacy trips can have no snapshot.
Successful raw AI output is intentionally not stored. Environments that ran the
short-lived pre-release snapshot schema must drop its obsolete
`raw_ai_response` column because Hibernate `ddl-auto: update` does not remove
columns.

### `ItineraryDay`

Represents one day inside a trip.

Important fields:

- `dayNumber`
- `title`
- `summary`
- Ordered `activities`

The day number is unique inside a trip.

### `Activity`

Represents one itinerary item.

Important fields:

- `name`
- `time`
- `type`
- `location`
- `duration`
- `estimatedCost`
- `note`
- `rating`
- `latitude`
- `longitude`
- `googlePlaceId`
- `coordinateSource`
- `coordinateConfidence`
- `sortOrder`

Important enums:

- `ActivityType`: `FOOD`, `CAFE`, `ATTRACTION`, `TRANSPORT`,
  `ACCOMMODATION`, `ACTIVITY`, `NIGHTLIFE`.
- Coordinate source includes verified place, AI-provided, geocoded, and manual
  values.
- Coordinate confidence uses high, medium, and low style levels.

Important constraints:

- `estimatedCost` is expected to be a VND group total.
- Activity times should use `HH:mm`.
- Manual coordinates should be validated and marked as manual/high confidence.
- Transport activities are handled specially by coordinate resolution and cost
  logic.

## Destination And Places

### `Destination`

Represents a catalog destination.

Important fields:

- `slug`
- `name`
- `region`
- `category`
- `tags`
- `imageUrl`
- `imageSource`
- `latitude`
- `longitude`
- `active`
- `featured`

Destination seed data drives discovery, weather lookup, and AI destination
context. Slugs and coordinates should be changed carefully.

### `Place`

Represents verified place context for AI planning and activity enrichment.

Important fields:

- Name, normalized name, aliases.
- Destination relationship.
- Type.
- Address and coordinates.
- Rating.
- Cost and price hints.
- Indoor/outdoor/weather sensitivity.
- Tags.
- Source.

Verified place data should improve AI output without forcing the AI to use only
seeded places.

## Billing

### `UserWallet`

Stores available credits:

- Plan credits.
- Edit credits.
- Suggestion credits.

Wallet updates should go through `BillingService`.

### `CreditLedger`

Audit log for credit grants and consumption. Keep ledger writes aligned with
wallet mutations. Deleting a trip preserves ledger history and clears the
optional trip reference before removing the trip.

### `PaymentOrder`

Represents a purchase order.

Important status values include pending, paid, underpaid, expired, and
cancelled. Order fulfillment must be idempotent.

### `SepayTransaction`

Stores webhook transaction records. SePay ID idempotency matters.

## AI And Operations

### `AiUsageLog`

Tracks AI usage, status, operation, request IDs, attempts, tokens, and cost
estimates.

Deleting a trip preserves AI usage history and clears the optional trip
reference before removing the trip.

Important operations include:

- Plan generation.
- Day regeneration.
- Destination suggestion.

### `LocationResolutionCache`

Caches coordinate resolution results. Respect cache and request interval
settings instead of bypassing the resolver.

## Seed Data

Seed files live under:

```text
vivuplan-be/src/main/resources/data/
```

Treat seed files as product behavior. They influence destination search,
verified place context, coordinate quality, and AI output.
