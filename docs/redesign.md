# Medcore redesign

Implemented and verified locally on 20 September 2026. The production Docker site is served at http://localhost:8088.

## Design and motion

The supplied frontend-taste, high-end visual design, GPT taste, redesign, and full-output skills informed the implementation. Their marketing conventions were scoped to the sign-in surface; operational screens retain existing routes, labels, forms, permissions, and review steps. The user's requested Framer Motion implementation takes precedence over the GPT taste skill's GSAP preference.

- Forest-green and mint palette, self-hosted Geist variable font, and consistent rounded controls and nested image frames.
- Generated architectural imagery on sign-in and the overview, with an optimized 212 KB WebP and early image/font loading.
- Framer Motion image entrance and scroll depth, staggered content reveals, spring button feedback, shared navigation selection, and route transitions. Modal entrance animations use CSS transforms and opacity.
- Light/dark themes, keyboard focus indicators, skip links, mobile navigation, and reduced-motion support.
- Canvas UI's actual [Clouds React/WebGL source](https://canvasui.dev/docs/components/clouds), vendored from its official registry, creates the atmospheric layer over architectural images. The component and shared rectangle-cache helper retain David Haz's [MIT + Commons Clause license](../frontend/src/components/canvasui/LICENSE.md).
- Cinematic opening aperture, masked two-line title reveal, 24-second camera drift, spring-smoothed pointer parallax, short route crossfades, and a restrained film-grain texture. Patient data and forms remain normal HTML, outside the shader.
- A persistent pause/play control disables decorative motion; changes to the operating system's reduced-motion setting apply immediately. Small screens (800px and below), hidden tabs, and offscreen artwork do not mount the GPU effect. WebGL context loss leaves static artwork and usable controls.
- The shader loads after initial content, runs at a capped 30fps, limits pixel density to 1.25, and renders its expensive noise field at 35% resolution. No new runtime package is required. No experimental browser flags or origin-trial tokens are needed for the mist overlay.
- Reworked overview, patient lists, room cards, forms, reports, and assistant surfaces.
- The operational brief now reads verified dashboard totals directly; opening the overview does not make a redundant external AI request.

Motion implementation follows [Motion's accessibility guidance](https://motion.dev/docs/react-accessibility) and [scroll animation guidance](https://motion.dev/docs/react-use-scroll).

## Secure session fix

The database and frontend were running, but the backend had exited during bootstrap because the configured initial administrator password had only four characters. The backend requires at least 12 characters and at most 72 UTF-8 bytes.

The ignored local `.env` now contains a valid bootstrap password, and the backend has been recreated successfully. Compose checks API health before starting the frontend. `.env.example` now uses a valid-length example password.

The browser distinguishes service unavailability, network failure, malformed CSRF responses, expired sign-in sessions, and invalid credentials. CSRF protection remains enabled. Tests cover session error handling, and real browser sign-in succeeds.

## Gemini configuration and remaining blocker

The supplied key is stored only in the ignored root `.env` and passed to the Java backend. It is absent from frontend source and configuration.

The replacement key and requested model are configured locally with `AI_MODE=external`, `gemini-3.5-flash-lite`, and Google's [OpenAI-compatible chat endpoint](https://ai.google.dev/gemini-api/docs/openai). The backend has been recreated with this configuration. A direct minimal request with the new key returned:

> HTTP 402, RESOURCE_EXHAUSTED: Your prepayment credits are depleted.

The authenticated application request also returns its structured assistant-unavailable response with model identifier `gemini-3.5-flash-lite`. Live Gemini operation has not passed: replenish the project's prepaid credits in [Google AI Studio](https://ai.studio/projects). Standard hospital workflows work independently. Both `.env` and private `.env.*` variants are excluded from Git; `.env.example` contains no key. Rotate keys shared in conversation before any public deployment.

## Verification

- Production Docker build and TypeScript check passed.
- All 14 frontend unit tests passed, including secure-session handling and procedure-time defaults under browser/server clock skew. The procedure default now cannot precede the active admission's server-recorded timestamp; explicitly entered times and backend validation are unchanged.
- All 10 selected browser tests passed on the final production build: Canvas UI rendering and context-loss fallback; saved motion preference and live OS changes; desktop/mobile navigation; both themes; service-error handling; patient admission/procedure/transfer/discharge; account and catalogue administration; doctor permissions; reports; and assistant-unavailable recovery. The recovery test uses a deterministic error response, not a successful Gemini call.
- TypeScript, formatting, and whitespace checks passed. The authenticated assistant probe reported the configured model correctly; a direct Google request established the separate prepaid-credit blocker.
- The existing assistant-success browser test remains excluded because Google currently requires prepaid credits; it is not claimed as passing. No backend business logic changed.
- Browser tests create clearly named synthetic test records in the local demonstration database.
- Original redesign Lighthouse report: [redesign-lighthouse.json](redesign-lighthouse.json). This predates the Canvas UI pass. It scored 88 performance, 100 accessibility, and 96 best practices, with zero cumulative layout shift and 3.9-second mobile LCP. These are local lab results, not field-performance measurements. The expected unauthenticated `/auth/me` response (401) is logged before sign-in.
- Canvas UI pass: [cinematic-lighthouse.json](cinematic-lighthouse.json), mobile lab scores 86 performance, 100 accessibility, 96 best practices; LCP 3.9 seconds, total blocking time 160ms, layout shift 0. The 2.5-second LCP target remains unmet; cinematic changes are not claimed to improve loading performance.
- After the final font/loading adjustments, the two design browser tests passed again, including navigation at 1440 x 800 and mobile menu opening/closing.

## Artwork

Generated with the built-in image tool. Source: `frontend/public/medcore-atrium.png`. Optimized asset used by the site: `frontend/public/medcore-atrium.webp`.

Final generation prompt:

> Use case: photorealistic-natural. Asset type: cinematic background photograph for Medcore hospital operations sign-in screen, landscape 1536x1024. A serene contemporary hospital atrium, sculptural curved pale concrete walls and a broad curved staircase, floor-to-ceiling glass on the right, one beautiful indoor tree in a sunken garden, soft daylight forming precise long shadows on stone floors. Architectural magazine photograph, 35mm lens, realistic materials, deep forest green shadows and desaturated silver mint highlights, carefully composed with sweeping architectural lines. No people, no medical equipment, no text, no logos, no watermarks. The left third and top have dark negative space for white interface typography. Cinematic and calm, refined and credible, not science fiction.

Screenshots: [dark sign-in](screenshots/redesign-sign-in-dark.png), [light sign-in](screenshots/redesign-sign-in-light.png), [desktop overview](screenshots/redesign-dashboard-dark.png), and [mobile overview](screenshots/redesign-dashboard-mobile.png).

Canvas UI pass screenshots: [cinematic sign-in](screenshots/cinematic-sign-in.png), [cinematic workspace](screenshots/cinematic-workspace.png), and [mobile entrance](screenshots/cinematic-mobile.png).

## Checkpoints

The workspace was not previously a Git repository. A baseline and focused implementation, configuration, regression-test, and verification commits now preserve the redesign. No remote push was performed. The supplied local `.agents` directory remains untouched and untracked; credentials remain ignored.
