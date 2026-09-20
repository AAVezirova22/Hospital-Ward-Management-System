# Medcore redesign

Implemented and verified locally on 20 September 2026. The production Docker site is served at http://localhost:8088.

## Design and motion

The supplied frontend-taste, high-end visual design, GPT taste, redesign, and full-output skills informed the implementation. Their marketing conventions were scoped to the sign-in surface; operational screens retain existing routes, labels, forms, permissions, and review steps. The user's requested Framer Motion implementation takes precedence over the GPT taste skill's GSAP preference.

- Forest-green and mint palette, self-hosted Geist variable font, and consistent rounded controls and nested image frames.
- Generated architectural imagery on sign-in and the overview, with an optimized 212 KB WebP and early image/font loading.
- Framer Motion image entrance and scroll depth, staggered content reveals, spring button feedback, shared navigation selection, and route transitions. Modal entrance animations use CSS transforms and opacity.
- Light/dark themes, keyboard focus indicators, skip links, mobile navigation, and reduced-motion support.
- Reworked overview, patient lists, room cards, forms, reports, and assistant surfaces.
- The operational brief now reads verified dashboard totals directly; opening the overview does not make a redundant external AI request.

Motion implementation follows [Motion's accessibility guidance](https://motion.dev/docs/react-accessibility) and [scroll animation guidance](https://motion.dev/docs/react-use-scroll).

## Secure session fix

The database and frontend were running, but the backend had exited during bootstrap because the configured initial administrator password had only four characters. The backend requires at least 12 characters and at most 72 UTF-8 bytes.

The ignored local `.env` now contains a valid bootstrap password, and the backend has been recreated successfully. Compose checks API health before starting the frontend. `.env.example` now uses a valid-length example password.

The browser distinguishes service unavailability, network failure, malformed CSRF responses, expired sign-in sessions, and invalid credentials. CSRF protection remains enabled. Tests cover session error handling, and real browser sign-in succeeds.

## Gemini configuration and remaining blocker

The supplied key is stored only in the ignored root `.env` and passed to the Java backend. It is absent from frontend source and configuration.

The local configuration uses `AI_MODE=external`, `gemini-3.5-flash`, and Google's [OpenAI-compatible chat endpoint](https://ai.google.dev/gemini-api/docs/openai). A direct minimal request to Google returned:

> HTTP 403, PERMISSION_DENIED: Your project has been denied access. Please contact support.

The application also returns its structured assistant-unavailable response. Live Gemini operation has not passed: the Google project must regain API access, or an authorized key from another enabled project must be supplied. Standard hospital workflows work independently.

## Verification

- Production Docker build and TypeScript check passed.
- All 10 frontend unit tests passed, including four new secure-session tests.
- Seven browser tests passed: sign-in/service errors, both themes, responsive navigation, full patient admission/procedure/transfer/discharge journey, catalogue and account administration, doctor permissions, report filters, and mobile overflow/reduced-motion checks.
- The existing assistant-success browser test was excluded because Google currently denies access; it is not claimed as passing. No backend business logic changed.
- Browser tests create clearly named synthetic test records in the local demonstration database.
- Lighthouse report: [redesign-lighthouse.json](redesign-lighthouse.json). This is a local mobile lab result for the sign-in page, not a field-performance measurement. The final pass scored 88 performance, 100 accessibility, and 96 best practices, with zero cumulative layout shift. The throttled largest-contentful-paint measurement was 3.9 seconds, above the 2.5-second target. Image discovery, eager loading, and high fetch priority checks pass. The console diagnostic is the expected unauthenticated `/auth/me` response (401) before sign-in.
- After the final font/loading adjustments, the two design browser tests passed again, including navigation at 1440 x 800 and mobile menu opening/closing.

## Artwork

Generated with the built-in image tool. Source: `frontend/public/medcore-atrium.png`. Optimized asset used by the site: `frontend/public/medcore-atrium.webp`.

Final generation prompt:

> Use case: photorealistic-natural. Asset type: cinematic background photograph for Medcore hospital operations sign-in screen, landscape 1536x1024. A serene contemporary hospital atrium, sculptural curved pale concrete walls and a broad curved staircase, floor-to-ceiling glass on the right, one beautiful indoor tree in a sunken garden, soft daylight forming precise long shadows on stone floors. Architectural magazine photograph, 35mm lens, realistic materials, deep forest green shadows and desaturated silver mint highlights, carefully composed with sweeping architectural lines. No people, no medical equipment, no text, no logos, no watermarks. The left third and top have dark negative space for white interface typography. Cinematic and calm, refined and credible, not science fiction.

Screenshots: [dark sign-in](screenshots/redesign-sign-in-dark.png), [light sign-in](screenshots/redesign-sign-in-light.png), [desktop overview](screenshots/redesign-dashboard-dark.png), and [mobile overview](screenshots/redesign-dashboard-mobile.png).
