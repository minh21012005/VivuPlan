<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

# VivuPlan Frontend Agent Guide

This package is a Next.js 16 App Router app using React 19, TypeScript strict
mode, Tailwind CSS 4, Radix primitives, lucide-react icons, Leaflet maps, and
Vietnamese user-facing UI.

## Main Entry Points

- App shell: `app/layout.tsx`
- Global styles: `app/globals.css`
- API contract and DTOs: `lib/api.ts`
- Auth state: `contexts/auth-context.tsx`
- Billing state: `contexts/billing-context.tsx`
- Shared UI: `components/ui/`
- Travel UI: `components/travel/`
- Admin UI: `app/admin/`

## Frontend Rules

- Treat `lib/api.ts` as the frontend source of truth for backend DTOs. Update it
  whenever backend request or response shapes change.
- Keep route pages and components consistent with existing App Router patterns.
- Use client components when browser state, localStorage, maps, auth context, or
  interactive controls are required.
- Keep user-facing copy Vietnamese and preserve UTF-8 accents.
- Do not introduce a second design system. Reuse `Button`, `Card`, `Badge`,
  `SectionHeader`, existing CSS variables, and local component patterns.
- Do not rewrite `app/globals.css` broadly for small feature work. It is a large
  shared surface.
- Use lucide-react icons for icon buttons when suitable.
- Avoid breaking the existing auth and billing context ordering in
  `app/layout.tsx`.
- Keep 402 purchase flows connected to `PurchaseModal` and wallet refreshes.
- For maps, keep Leaflet client-only behavior and coordinate validation.
- Weather UI should remain non-blocking. Weather failures should not break trip
  viewing or planning.

## Important Routes

- `/`: home and destination discovery entry.
- `/plan`: planner form, validation, destination suggestions, trip generation.
- `/itinerary`: authenticated trip list.
- `/itinerary/[id]`: trip detail, editing, sharing, map, weather, regeneration.
- `/explore`: destination catalog.
- `/pricing`: packages, wallet, purchase flow.
- `/admin`: admin dashboard.
- `/admin/trips/[id]` and `/admin/users/[id]`: admin detail pages.

## Verification

Preferred commands:

```powershell
npm run lint
npm run build
```

When changing visual behavior, start the dev server and inspect affected routes:

```powershell
npm run dev -- --hostname 127.0.0.1 --port 3000
```
