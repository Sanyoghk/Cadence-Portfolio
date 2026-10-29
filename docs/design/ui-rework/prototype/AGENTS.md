# Prototype Instructions

Run the local server yourself and open the preview in the browser available to this environment. Do not give the user server-start instructions when you can run it.

Before making substantial visual changes, use the Product Design plugin's `get-context` skill when the visual source is unclear or no longer matches the current goal. When the user gives durable prototype-specific design feedback, preferences, or decisions, record them in `AGENTS.md`.

When implementing from a selected generated mock, treat that image as the source of truth for layout, component anatomy, density, spacing, color, typography, visible content, and hierarchy.

Build app UI in `src/`. Keep `.openai/hosting.json`, `worker/index.js`, `scripts/prepare-sites-build.mjs`, and `tests/sites-worker.test.mjs` intact so the same local prototype can be handed to Sites. Before a Sites handoff, run `npm run build` and `npm run test:sites`; the build must leave `dist/client/index.html`, `dist/server/index.js`, and `dist/.openai/hosting.json`.


## Durable Cadence review constraints

The user selected Cream Orbit (direction1). Keep cream/dark/sage palette, fixed background/frame/topbar,
large CADENCE and Create YOUR brand. The wedding reference controls art direction; yeahright is loader-only.
Intro minimum3 seconds. Public Identity reveals demo pillars; Standard/Plus are concepts without billing.
Use one of five Workspace sections, an explicit Brand selector, separate exact consent/approval/history.
All data synthetic; no .env, credentials, real files, external user data or Cadence API calls.
Do not change working frontend/backend. Keep artifacts under docs/design/ui-rework.
Applied skills: design-taste-frontend, emil-design-eng, mobile-native, animate.
User allowed isolated local headless Chrome/Edge QA. Work with one agent. Do not publish/deploy this review.


## GSAP motion decision

GSAP3.15.0 is installed in this prototype with pnpm and an exact lock entry. Keep motion optional
and component-scoped; never import animation code from the working frontend. Used plugins:
ScrollTrigger, SplitText, Flip, CustomEase. Intro/footer simple loops remain CSS. Keyboard and
high-frequency work actions are immediate; mobile agenda switches skip Flip. Native menus/dialog
semantics, reduced motion, fail-open visibility and business-state timers are independent of GSAP.
Local review URL is http://127.0.0.1:4176/. Publishing still requires a separate request.

## Frame refinement,2026-10-19

The user approved the design and requested removal of the stars on the outer fixed frame.
Keep the frame lines, background and topbar; no corner/side star ornaments on this frame.
This overrides the original outer-frame decoration request. Other stars/logo/artwork remain.

## Pricing refinement,2026-10-19

Keep Standard and Plus symmetrical at the same height, with equal column widths and shared
text/action baselines, following the wedding reference's two-column composition. Pricing text
and the scrolling line should be larger. Reveal both desktop plans together, without a stagger.
On phones keep a readable single column and the decorative line in the outer gutter.

## Text curtain motion,2026-10-19

The user requested all content copy to emerge upwards from behind a curtain. Use RevealText
for semantic headings, prose and text lists; keep React ownership of the actual content.
Public copy reveals once on scroll (700ms); workspace/dialog copy on mount (240ms), without
replaying on edits or frequent view toggles. Keep navigation, form/consent labels, live statuses
and data controls immediate. Intro readiness comes from MotionRoot; no reveals behind the loader.
Pricing text uses one shared trigger to retain symmetry. CADENCE alone uses SplitText char masks.
Base text stays visible without GSAP; keyboard/reduced motion and an independent watchdog must
finish text immediately. Keep self-hosted fonts, palette, layout and all demo business behavior.

## Page scrollbar,2026-10-19

Hide only the browser's outer page scrollbar. Preserve natural wheel/touch/keyboard scrolling
and native scrollbars inside dialogs, history panels and form fields. Do not use overflow:hidden.

## Accepted UI baseline,2026-10-19

The user is satisfied with the current UI and will refine animations manually later.
Treat this prototype as the accepted visual baseline; do not spend subsequent implementation
work on unsolicited motion polish. The implementation plan still orders backend28 before
working frontend rework23–24, then25–27. This decision does not itself authorize application
changes, provider selection, publication or billing. Preserve the prototype as design reference.
