# Cinematic landing patch

Apply the complete mail patch from the repository root with `git am medcore-cinematic-v2.patch`. Each fix has its own commit and `Co-authored-by: Vectant Agent <agent@vectant.dev>` trailer. No dependency installation is needed: GSAP, @gsap/react, and Motion (the current Framer Motion API) are already installed; Canvas UI is already vendored.

## Direction and audit

Preserve Medcore's Outfit/Geist typography, mineral surfaces, forest accent, routes and interactions. Motion intensity 8, design variance 7, visual density 3. Follow the repo's design-taste-frontend, redesign-existing-projects, high-end-visual-design, gpt-taste and full-output-enforcement guidance. Existing page structure already follows attention, interest, desire and action; retain it and give each chapter a visual progression.

The original phone hid messages until the reader scrolled, translated beyond its column at the end, and disabled the sequence entirely on mobile. The pointer-only hero effect did not animate by itself. A conflicting CSP prevented the development runtime from hydrating and blocked image/animation style attributes. These runtime failures made otherwise implemented animations appear broken.

## Choreography

- Opening: staggered headline and actions, an 18-second camera pullback, and subtle automatic Canvas UI Clouds. Scroll carries the copy out of the opening frame.
- Product: the real workspace rises into view with perspective and scale resolving to a readable screen.
- Story: the heading stays pinned on desktop as photographic panels rise and their images move independently.
- Files: inputs and review panel converge as their chapter enters view.
- Conversation: the phone rotates into view, pauses face-on for the messages, then moves aside as the workspace appears. Playback starts at 35% visibility and completes in 2.5 seconds without requiring more scrolling. It holds the ending instead of looping abruptly. Pause, resume and replay are available on mobile and desktop. Offscreen and hidden-tab playback suspends.
- Closing: a slow photographic camera move accompanies the final call to action.

GSAP owns the clock and outer scroll frames. Motion alone writes the phone transforms and content entrance animations. They never compete for the same element's transform. Canvas UI affects an empty decorative layer, keeping controls and text in normal HTML. GPU effects unmount for small screens, reduced motion, offscreen artwork and hidden documents. Its original license remains intact.

Reduced motion presents the complete conversation and disables camera movement. Pausing does not change chapter height. Server-rendered content remains visible without JavaScript. Canvas context loss leaves the normal page intact.

## Imagery and sizing

The supporting synthetic photographs are replaced with credited, locally hosted National Cancer Institute photography; attribution is in `frontend/public/landing/stills/PHOTOGRAPHY.md`. These are illustrative photographs, not customer endorsements. The generated opening hero is retained. The product preview now frames real capacity/admission data; team and report tabs show their actual application captures rather than the same decorative dashboard picture.

Hero image sizes account for the 24px mobile gutter, 44px desktop gutter, and 1600px maximum section width. Supporting image sizes reflect their containers.

## Verification

Validated in an isolated copy of the current source, leaving the user's source files unchanged:

- 18 Playwright tests passed across the cinematic and existing landing suites.
- Automatic playback and stable endings at 390px, 768px and 1440px; mobile/desktop image loading and horizontal overflow at 360px, 768px and 1440px.
- Pause/resume, replay, offscreen suspension, live reduced-motion changes, stored global pause, Canvas context loss, and no-JavaScript readability.
- Actual product transform changes during scroll and resets after pause; no CSP violations in that browser test.
- Existing keyboard tabs, theme persistence, mobile focus handling, sample workflow confirmation/cancellation and local-file handling.
- Visual inspection of hero, product chapter, and phone beginning/end screenshots.
- TypeScript compared against the original source: identical existing diagnostics, no new diagnostics. The repository-wide typecheck still fails on pre-existing application type errors, including duplicate `Row` declarations in `src/api.ts` and downstream feature errors. A clean production build and Lighthouse scores are not claimed.

## References

- [Canvas UI](https://canvasui.dev/docs): existing vendored Clouds overlay, used without requiring experimental HTML-in-canvas support.
- [Motion values](https://motion.dev/docs/react-motion-value): continuous animation values without per-frame React renders.
- [GSAP timelines](https://gsap.com/docs/v3/GSAP/Timeline/): timed playback and explicit lifecycle control.
- [Next.js image sizes](https://nextjs.org/docs/app/api-reference/components/image#sizes): responsive source selection.
- [Next.js CSP](https://nextjs.org/docs/app/guides/content-security-policy): nonce propagation and development runtime support. Production keeps nonce-protected script/style elements; inline element styles are permitted for React, next/image and animation libraries.
