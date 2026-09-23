# Medcore public website redesign

## Brief and audit

The September 2026 redesign replaces the entire public landing experience while retaining the `/app` workspace and existing public anchor destinations. Applied all five repo skills from `.agents/skills`: design-taste-frontend, high-end-visual-design, gpt-taste, redesign-existing-projects, and full-output-enforcement.

The prior source combined a dark photographic hospital website, fictional specialist profiles, repeated sections, looping message illustrations, and a walkthrough form that claimed to submit without a server request. The public site should communicate the hospital operations product and give visitors a useful path into the actual workspace.

## Product evidence

| Capability | Source |
| --- | --- |
| Hospital and department workspaces | `frontend/src/components/WorkspaceSwitcher.tsx` |
| Patient admissions, transfers, discharge | `frontend/src/features/admissions/` |
| Capacity and staged drag-and-drop ward planning | `frontend/src/features/planner/` |
| Uploads and connected folders for AI workflows | `frontend/src/features/assistant/AssistantSources.tsx` |
| Reviewed multi-step workflows with confirmation | `frontend/src/features/assistant/WorkflowProposal.tsx`, `README.md` |
| Messaging gateway | Not implemented in this checkout, as documented in `docs/imessage-integration.md`. Public copy describes the in-workspace assistant and file-based workflows only. |
| Reporting, drill-downs, CSV exports | `frontend/src/features/reports/` |
| Patient account and care history | `frontend/src/features/patients/PatientPortal.tsx` |

Marketing examples use synthetic records. They do not send messages, upload visitor files, or make hospital changes. Actual file processing remains in the authenticated workspace. The site does not invent customer logos, outcome statistics, certifications, or endorsements.

## Design references

Reviewed official websites on 2026-09-20:

- [Maven Clinic](https://www.mavenclinic.com/): human imagery, clear audience paths, open spacing, concise service explanations.
- [Oscar Health](https://www.oscarhealth.com/): plain language, approachable scale, confident brand-led composition.
- [Nabla](https://www.nabla.com/): clear workflow benefits and a direct route from explanation to product access.

This is an original Medcore design informed by those references, not an official or copied private design system. The palette uses mineral whites and deep green, with Outfit display typography and Geist interface text. CSS variables supply light and dark themes. Motion intensity 7, design variance 7, visual density 3. The page follows attention, interest, desire, action, with the repo's motion rules adapted to avoid conflicting animation ownership and inaccessible autoplay.

## Motion and assets

- Existing `motion/react` is the current Framer Motion API and handles entrances, state transitions, and hover feedback.
- `gsap` and `@gsap/react` provide isolated, reversible scroll choreography.
- [Canvas UI](https://canvasui.dev/) supplies the already-vendored WebGL effects; original license attribution is retained. Only artwork is affected, not text or controls.
- `frontend/public/landing/stills/presence.webp` is generated editorial imagery, not a photograph of Medcore staff or an actual customer facility. Built-in image generation was used and the result compressed with Sharp.
- `frontend/public/landing/stills/workspace.webp` is a real screenshot of the existing application with synthetic demonstration data, converted from `docs/screenshots/cinematic-workspace.png`.

Final image prompt: cinematic wide editorial photograph of two medical professionals in pale sage scrubs reviewing a tablet at a modern, airy hospital nurse station; subjects in the right half, luminous architectural space on the left; limestone, fluted glass, trees and late-morning sunlight; restrained silver and sage, authentic candid body language; no logos, text, UI, or watermark. Source image: `C:/Users/polek/.codex/generated_images/01a0c055-e648-7df1-9af6-a23c85abaac7/exec-affc6dcc-b3a6-49ac-8f78-1252e0f3c396.png`.

## Verification

Verification results will be recorded after the implementation is checked in production mode.
