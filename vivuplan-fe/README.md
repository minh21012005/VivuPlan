# VivuPlan Frontend

Next.js frontend for VivuPlan, a Vietnam-focused AI travel planning app.

## Stack

- Next.js 16 App Router
- React 19
- TypeScript strict mode
- Tailwind CSS 4
- Radix UI primitives
- lucide-react icons
- Leaflet maps

## Local Setup

Create environment variables based on `.env.example`, then run:

```powershell
npm install
npm run dev -- --hostname 127.0.0.1 --port 3000
```

Verification:

```powershell
npm run lint
npm run build
```

Main API configuration:

- `NEXT_PUBLIC_API_URL`: backend API base URL, defaulting in code to
  `http://localhost:8080`.

See `../AGENTS.md`, `AGENTS.md`, and `../docs/` before making behavior changes.
