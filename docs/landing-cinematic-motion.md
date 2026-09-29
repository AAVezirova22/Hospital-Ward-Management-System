# Cinematic landing motion

A motion pass over the public landing (`frontend/src/landing`) that makes the page read as a sequence of shots, using GSAP, Motion (the Framer Motion API) and the vendored Canvas UI effects. It follows all five repo skills in `.agents/skills`: design-taste-frontend, redesign-existing-projects, high-end-visual-design, gpt-taste and full-output-enforcement.

## Design read

Redesign (preserve) of a B2B hospital-operations landing for ward administrators and clinical staff: calm, trust-first, cinematic. Brand tokens, Outfit/Geist type, forest accent, routes, anchors, copy and conversion paths are unchanged.

Dials: design variance 7, motion intensity 8, visual density 3. Motion was raised from the existing pass because the brief asked for the page to feel cinematic; each animation below has a one-line reason.

## Choreography

| Moment | Library | What happens | Why |
| --- | --- | --- | --- |
| Opening shot | GSAP | The hero frame widens from a letterboxed aperture; the headline rises out of per-line masks, then the lede, actions and note follow. A CSS pre-hydration hold prevents a full-size flash. | Storytelling: the page opens like a film. |
| Leaving the hero | GSAP | The frame recedes and rounds while the photograph drifts and pushes in. | State transition: a dolly-out instead of a cut. |
| Window light | Canvas UI Clouds | A slow haze drifts over the hero photograph; cursor wind parts it. | Hierarchy and brand: presence clears the scene. |
| Intro line | GSAP | "A hospital is a thousand moving parts. Bring them together." assembles word by word on scroll. | Storytelling: the parts come together. |
| Platform chapter | GSAP | The section rises inset with deep shoulders and widens to full bleed. | Hierarchy: the product is the main stage. |
| Product tabs | Motion | The next view wipes in over the previous one, which recedes beneath it. | State transition: a shot change, not a swap. |
| Primary actions | Motion | "Open the workspace" leans toward a mouse pointer on a spring. | Feedback on the primary conversion. |
| Closing shot | GSAP | The closing panel opens from an aperture and its headline rises from line masks. | Storytelling: the ending mirrors the opening. |
| Presence trail | Canvas UI Liquid | The pointer leaves a soft trail of light across the closing panel. | Feedback and brand. Muted on the dark theme to protect type contrast. |
| Film grain | CSS | One fixed, non-interactive noise layer with a stepped transform jitter. | Texture for photography and flat surfaces. |

Existing choreography (pinned care-team story, product bezel tilt, file workflow convergence, phone conversation film) is unchanged.

## Ownership rules

- GSAP owns scroll and timeline work; Motion owns spring physics and state changes. They never write the same element's transform: GSAP moves the hero `img`, Motion scales its wrapper; Motion transforms `.mc-magnetic`, CSS keeps the button's own hover and press.
- Canvas UI layers are artwork only. Text, controls and patient information stay in normal HTML above them.
- Every effect honours `prefers-reduced-motion` and the site's pause control. GPU layers mount only on desktop, in view, with the document visible. The page is complete without JavaScript.

## Pre-flight changes

- Section eyebrows reduced from six to three (hero, care-team story, closing call) to meet the one-per-three-sections rule.
- No em-dashes in visible copy. No new copy, claims, numbers or images were introduced.

## Verification

- `e2e/landing-motion.spec.ts` covers the opening aperture, reduced motion, the word scrub, the tab wipe, magnetic pull and release with working navigation, and desktop-only Canvas UI mounting with no phone overflow.
- Landing suites run on Edge against the dev server: 26 passed. The two failures (`landing-film` pause after the 2.5s film has completed, and `landing-redesign` finding "Messages" in a meta description) fail identically on `main` and are not touched here. `cinematic.spec.ts` targets `/app` and needs the backend.
- Manual screenshots at 390px and 1440px, light and dark themes. `tsc --noEmit` passes.
