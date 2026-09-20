# Canvas UI integration

Upstream: David Haz, [Canvas UI](https://canvasui.dev), retrieved 20 September 2026.

- `Clouds.tsx`: https://canvasui.dev/r/clouds-react.json (React, WebGL2, no dependencies).
- `Glass.tsx`: https://canvasui.dev/r/glass-react.json (React, WebGL2, no dependencies). Used on the public landing diagnostic gallery as a glass lens over photography.
- `../rect-cache.ts`: https://github.com/DavidHDev/canvas-ui/blob/main/src/lib/rect-cache.ts. The registry source imports this helper but does not include it in its dependency metadata.
- License: [MIT + Commons Clause](LICENSE.md), permitting use within this application, not resale of the components themselves.

Medcore changes: pixel-density cap of 1.25, 30fps pacing, context-loss/error fallback, and formatting. The app dynamically loads Clouds only for visible desktop artwork with motion enabled; it unmounts the effect on pause, reduced motion, hidden documents, and small viewports. The public landing loads Clouds as sunrise mist over the hero and Glass over the technology stills, both desktop-only with reduced-motion fallbacks. Controls and patient information are never passed into the GPU effect. The standard WebGL overlay works without experimental HTML-in-canvas support.

`e2e/cinematic.spec.ts` covers WebGL rendering, context loss, stored pause state, live system preferences, viewport changes, animated navigation, and the assistant-unavailable UI contract.
