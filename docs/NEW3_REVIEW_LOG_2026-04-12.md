# NEW3 Review Log

Date: 2026-04-12

## Purpose

- Keep a strict log of what was already reviewed in `Strange/New3`.
- Prevent repeated passes over the same files while the audit is still in progress.
- Separate "already verified", "improved", and "next queue" so the next iteration stays targeted.

## Already Reviewed And Improved

- `build.gradle`
- `.gitignore`
- `README.md`
- `src/client/java/ru/strange/client/utils/io/AtomicFileIO.java`
- `src/client/java/ru/strange/client/manager/cfg/ConfigManager.java`
- `src/client/java/ru/strange/client/manager/friend/FriendManager.java`
- `src/client/java/ru/strange/client/StarterMenu/AltAccountStore.java`
- `src/client/java/ru/strange/client/ui/clickgui/localization/GuiLocalization.java`
- `src/client/java/ru/strange/client/utils/other/ItemShaderProfiles.java`
- `src/client/java/ru/strange/client/utils/math/animation/anim2/Interpolator.java`
- `src/client/java/ru/strange/client/utils/particle/ParticleUtil.java`
- `src/client/java/ru/strange/client/utils/math/Mathf.java`
- `src/client/java/ru/strange/client/utils/math/MathHelper.java`
- `src/client/java/ru/strange/client/renderengine/font/FontRenderer.java`
- `src/client/java/ru/strange/client/renderengine/font/FontUtils.java`
- `src/client/java/ru/strange/client/module/impl/world/Svetych.java`
- `src/client/java/ru/strange/client/module/impl/player/Trails.java`
- `src/client/java/ru/strange/client/module/impl/utilities/GPS.java`
  - replaced per-rebuild route list allocations with reusable build/post-process buffers;
  - centralized route invalidation state to avoid duplicated branch cleanup;
  - switched surface probing to a shared `BlockPos.Mutable` scan path.
- `src/client/java/ru/strange/client/StarterMenu/MenuBackgroundManager.java`
  - unified duplicated initialization/refresh preparation into a single background-source bootstrap path;
  - removed per-pixel `float[]` allocations from cubemap direction sampling;
  - normalized locale-sensitive filename handling and removed an unused helper branch.
- `src/client/java/ru/strange/client/module/impl/player/TargetESP.java`
  - replaced per-frame render allocator creation with reusable render buffers;
  - centralized target/runtime reset handling and removed repeated animation-state cleanup branches;
  - moved repeated `getBuffer(...)` lookups out of several hot render loops, including the legacy cube-particle path.
- `src/client/java/ru/strange/client/utils/render/RenderUtil.java`
  - removed unused render imports and collapsed duplicated `Image`/`Gif` draw boilerplate into shared helper paths;
  - preserved the public API while reducing maintenance overhead across overloaded draw methods.
- `src/client/java/ru/strange/client/ui/clickgui/newstyle/NewGuiRender.java`
  - cached static GUI texture identifiers instead of rebuilding `Identifier` instances in the render path;
  - unified per-frame time sampling for blink/shake animation so search, text input, and module shake reuse the same clock values;
  - reduced avoidable render-path churn in the clickgui category/settings flow.
- `src/client/java/ru/strange/client/module/impl/interfaces/hud/TargetHudRenderer.java`
  - preserved the last valid display target during fade-out so the HUD no longer falls into a null render state mid-animation;
  - cached the preview face texture identifier and aligned remember/clear flows with the new display-target state;
  - kept animated health ownership stable while the target is fading out.
- `src/client/java/ru/strange/client/ui/clickgui/screen/ItemShaderProfilesScreen.java`
  - cached selectable shader presets locally to avoid repeated clone allocations from `selectablePresets()`;
  - added direct item-id lookup storage so the preview path no longer linearly scans the item catalog for the selected entry;
  - collapsed repeated theme-visibility refresh calls behind a shared helper to keep profile snapshot usage consistent.
- `src/client/java/ru/strange/client/StarterMenu/AltManagerScreen.java`
  - aligned selection, keyboard navigation, and destructive actions with the filtered account list so search no longer acts on hidden rows;
  - stopped unconditional mouse-wheel capture outside the account list;
  - removed repeated `indexOf(...)` work from the visible-row render loop by reusing the resolved visible selection.
- `src/client/java/ru/strange/client/module/impl/interfaces/WaterMark.java`
  - added zero-sized window guards before HUD layout and drag math so minimize/resize transitions cannot feed invalid coordinates into the editor state;
  - validated mouse-coordinate scaling and pruned inactive segment animation entries from `segProgress`.
- `src/client/java/ru/strange/client/module/impl/world/DashCubes.java`
  - re-keyed placeable spot caches by entity UUID instead of transient entity ids;
  - replaced repeated per-spot entity collision queries with a reused nearby bounding-box snapshot during placement scans.
- `src/client/java/ru/strange/client/module/impl/interfaces/hud/PotionHudRenderer.java`
  - added a reusable per-tick effect cache for sorting, text formatting, and width calculation;
  - cached effect texture identifiers and converted texture fallback diagnostics to warn-once behavior.
- `src/client/java/ru/strange/client/module/impl/interfaces/hud/HudRenderDiagnostics.java`
  - bounded once-only diagnostic key storage so HUD logging dedupe cannot grow without limit.
- `src/client/java/ru/strange/client/utils/other/KeyBindPolicy.java`
  - bounded bind-name failure dedupe storage to prevent unbounded memory growth during long sessions.

## Reviewed Hotspots Without Code Changes Yet

- None in the current documented pass.

## Current Queue

- None. The documented hotspot queue for this audit pass is cleared.

## Rules For Next Passes

- Start by checking this file before opening another hotspot.
- Only return to an already-reviewed file if there is a new bug, regression, or a planned follow-up refactor.
- Mirror every completed pass into `NEW3_AUDIT_2026-04-12.md` after the code change is verified.
