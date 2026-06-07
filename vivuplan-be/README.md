# VivuPlan Backend

Spring Boot API for VivuPlan.

## Stack

- Java 21
- Spring Boot 3.3
- Maven wrapper
- PostgreSQL in normal runtime
- H2 for tests
- JWT authentication and role-based authorization
- Gemini itinerary generation
- Google OAuth token verification
- SePay-style billing orders and credit ledger
- Open-Meteo weather and Nominatim geocoding

## Local Setup

Create environment variables based on `.env.example`, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Run tests:

```powershell
.\mvnw.cmd test
```

See `../AGENTS.md`, `AGENTS.md`, and `../docs/` before making behavior changes.

