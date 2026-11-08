# React UI plan: the CineLight redesign (theater mode + house rules)

Status: **planned 2026-11-07**, revised the same day after an adversarial review. Supersedes the "React UI
rewrite" note in `roadmap.md`.

Source: the Claude Design handover (`cinelight-handover/`, not checked in: `HANDOVER.md`, `screens/`,
`design-source/CineLight Dashboard.dc.html`, `design-system/styles.css` + `readme.md`). Round 3 is the chosen
direction: **3a desktop / 3b mobile**, one responsive page.

## Owner decisions (2026-11-07)

| Question (HANDOVER §7) | Decision |
|---|---|
| Stack | **React SPA now**, not Thymeleaf. |
| Partial rule groups | **Mixed state in the sentences, plus the full per-event matrix as an Advanced view** on Config. |
| Where Event monitor / Lighting / Config / Debug go | **A "More" menu in the header**; theater/rules stays the landing view. |
| Simulate Play/Pause/Resume/Stop buttons | **Behind the Debug toggle**, admin-only, through the real webhook path. |

## Phases (one PR each, in order, each branched from the updated `master`)

| PR | Scope |
|---|---|
| 1 | Backend API: outcomes, sanitised history, now-playing, scene catalog, debug through the real path, config broadcasts |
| 2a | Frontend toolchain, Maven + CI wiring, serving at `/` (legacy at `/classic`), data hook, auth dialog, empty shell |
| 2b | Theater and House rules views, mode switching |
| 3a | Backend for the More-menu screens (config sections JSON, admin flag) |
| 3b | Event monitor, Lighting, Config (Advanced matrix, intervals, credentials), Debug screens |
| 3c | Retire Thymeleaf |

## What the backend lacks today

1. **Event history.** The log exists only in the browser since page load.
2. **Outcome per event.** SSE is broadcast *before* the command runs and carries no result.
3. **Now-playing state.** Nothing server-side records title, state and `since`.
4. **Scene names and room level.** Scenes are stored as IDs; names and brightness live only in the raw bridge
   resources JSON. `HueResourceDto` has no `group`/`actions` fields, so this needs a Jackson tree parse.
5. **Debug commands bypass SSE** and record nothing.
6. **Config writes are not pushed.** `saveHueScene` and `savePlexCommands` broadcast nothing, so a second
   dashboard goes stale.

## PR 1: backend API (Java only, no visible UI change)

**1.1 Outcome-aware pipeline.**
- `commands/PlexOutcome` (record: `kind` enum + nullable `sceneId`). Kinds, derived **in this order**:
  `IGNORED_UNSUPPORTED` (`plexEvent == null`; checked *first*, ahead of the Hue-disabled check, which leaves the
  boolean result unchanged), `IGNORED_HUE_DISABLED`, `IGNORED_UNKNOWN_TYPE` (`mediaType == null`, e.g. a Debug
  call with a bad `type`, which today would look like "rule off"), `IGNORED_RULE_OFF`, `SCENE_APPLIED`, `FAILED`.
- `PlexCommand` gets `PlexOutcome run(...)`; the existing `boolean execute(...)` delegates to it, so callers and
  tests keep working.
- `HueSceneController.startMovieLightScene()`/`stopMovieLightScene()` return the scene id they activated (read
  the `volatile` field **once** per call) or an empty `Optional` on failure. `PlexEvent.execute` passes it through.
- **Exceptions:** the pipeline catches any `RuntimeException` from the Hue path, logs it, records `FAILED`, and
  the webhook still returns 200 (graceful degradation). Test that.
- `services/events/PlaybackService` owns the pipeline (execute → record → broadcast) and is used by both
  `PlexController` and `DebugController`. Execute **then** broadcast, so the event carries its outcome. The id
  assignment, history append, now-playing update and broadcast happen in **one critical section**, so concurrent
  webhooks can never record in one order and broadcast in another.

**1.2 The event record, and payload sanitising.**
- `services/events/PlaybackEvent`: `id` (monotonic long), `timestamp` (**ISO string**, not `Instant`:
  `SseService` uses a bare `ObjectMapper` with no JavaTimeModule), `event` (`media.play` …), `mediaType`, `title`,
  `grandparentTitle`, `parentIndex`, `index` (so the UI can render `Slow Horses · S4 E2`), `year`, `player`
  (`Player.title`), `outcome`, `sceneId`, `source` (`plex` | `debug`), `payload`.
  Extend `PlexPayloadDto`/`PlexMetadataDto` with the missing fields, `@JsonIgnoreProperties(ignoreUnknown = true)`.
- **Credential and privacy rule:** the raw Plex payload contains `Account` (name, avatar URL),
  `Player.publicAddress` (the WAN IP), `Player.uuid` and `Server.uuid`. Today that reaches only live SSE
  listeners; a public history would expose it permanently. So `payload` is **rebuilt from an allowlist** with
  Jackson (`event`, `user`, `owner`, `Player.title`, `Player.local`, `Metadata.{type,title,grandparentTitle,
  parentTitle,parentIndex,index,year,librarySectionTitle,duration,viewOffset}`) and everything else is dropped,
  in a `PlexPayloadSanitizer` with its own tests over the raw string, including a fixture of a real-shaped
  payload that asserts no `Account`, `publicAddress`, `uuid`, `thumb` or token-like key survives. SSE `message`
  gets the same sanitised payload.
- Debug events get a synthetic payload of the same allowlisted shape, so anything that renders `payload` works.
- **Legacy compatibility (until PR 3c):** the Thymeleaf JS reads `data.command.{event,title,duration}`,
  `data.payload` and `data.timestamp`. `PlaybackEvent` keeps a nested `command` object with those three fields.
- **XSS:** the legacy page renders titles via `innerHTML`, and history now replays them (plus debug's `?title=`).
  Fix the legacy renderer to use `textContent` for every server string in this PR.

**1.3 History and now-playing.**
- `PlaybackHistory`: bounded in-memory ring of the last **100 media events** (`event` starts with `media.`),
  since boot, not persisted. Non-media events (`library.*`, `admin.*`, `device.new`) are broadcast but never stored,
  so they cannot push real plays out.
- `NowPlaying`, updated only by media events with `plexEvent != null`: `play`/`resume` → `Playing`, `pause` →
  `Paused`, `stop` → `Stopped`, none yet → `Idle`. **`media.scrobble` does not change state** (Plex sends it at
  ~90% watched while playback continues; it still runs its configured scene, as today). `since` = timestamp of the
  event that entered the current state (a resume after a pause resets it). Also title fields, year, mediaType,
  player, and `sceneId` of the last `SCENE_APPLIED`.
- `GET /api/playback/history?limit=N` (default 20, clamp 1..100, newest first) and `GET /api/playback/now`.
  Public. Note: `SecurityConfig` ends in `anyRequest().permitAll()`, so public paths need no new permit; the
  security tests must instead assert that `PUT /api/config/**` and `POST /api/debug/**` still return 401 without
  credentials.
- SSE: after each media event also broadcast `now-playing`.

**1.4 Scene catalog.**
- `controllers/hue/HueSceneCatalog` (app layer; `hue-java-client` stays unchanged and Spring-free): parse the
  resources JSON as a Jackson tree. `HueSceneSummary(id, name, groupId, groupName, groupType, level)`:
  `groupType` is `room` or `zone` (a scene's `group.rtype`); `groupName` is that room's or zone's
  `metadata.name`. `level` = the mean of `actions[].action.dimming.brightness`, counting an action with
  `on.on == false` as 0, rounded to int; `null` when no action has dimming data. Never invent a number.
- Its **own** `ScheduledTaskManager` refresh (not piggybacked on `HueStatusController.broadcastStatus`, which
  returns early with no SSE clients) at a new whitelisted property `hue.scene-catalog-interval` (default 300 s),
  plus a refresh on `PropertiesUpdatedEvent` for `hue.start-scene`/`hue.stop-scene`. The resources call fetches
  everything on the bridge, so never on the 10 s status interval.
- An unknown scene id on a public GET does **not** trigger a bridge call (no public request path may cause bridge
  traffic). Bridge failure: log, keep the last good catalog (empty at boot). Skipped entirely while Hue
  integration is disabled.
- `GET /api/hue/scenes` (public): `{ scenes: [...], start: {id, summary|null}, stop: {id, summary|null} }`.
- The "room level" in the UI = the `level` of `NowPlaying.sceneId`: the level the scene sets, not a measured
  reading. Say so in the Javadoc.

**1.5 Config broadcasts.** `saveHueScene` and `savePlexCommands` broadcast a `config` SSE event carrying
`{hueIntegrationEnabled, scenes: {start, stop}, plexCommands: {type → map}}`, and `GET /api/config` returns the
same shape (still credential-free; assert it in a test).

**Tests (PR 1).** Every outcome branch (including the reordered unsupported check and the unknown-type case),
`PlexPayloadSanitizer`, `PlaybackHistory` (cap, newest first, non-media excluded; concurrency via a latch and an
executor, **no `Thread.sleep`**, per `test-standards.md`), the `NowPlaying` state machine (scrobble leaves state,
resume resets `since`), `HueSceneCatalog` (fixtures under `src/test/resources/hue/`: normal, light-off action,
zone group, no dimming → null, malformed JSON → keeps last good + logs), the webhook returns 200 when Hue throws,
the new endpoints' JSON, and the 401 rules. Every class at 95% line and 90% branch. **Run PIT** (see
`dev-and-testing.md`): headroom at 98% is small and this PR adds many mutants.

## PR 2a: frontend toolchain, wiring, shell

- `frontend/`: Vite + React 19, **JavaScript + PropTypes**, ESLint (with `jsx-a11y`), Prettier, Vitest + Testing
  Library + `@vitest/coverage-v8`. Port from `~/IdeaProjects/goldenhour/frontend/{package.json,vite.config.js,
  eslint.config.js}` and goldenhour's CI `frontend` job, minus PWA, Tailwind, Leaflet and Stryker. Exact versions.
- Fonts: `@fontsource/cormorant-garamond` and `@fontsource/lora` (400 and 600). The vendored
  `frontend/src/styles/classical.css` is `design-system/styles.css` **with its line-2 Google Fonts `@import`
  removed** (the only change; a comment at the top says so). Component CSS uses only its tokens.
- Vite dev server proxies `/api` and `/plex` to `localhost:8080`.
- **Maven:** `frontend-maven-plugin` (pinned Node 22) runs `npm ci` + `npm run build` at `generate-resources`.
  A pom property `<frontend.skip>false</frontend.skip>` feeds every goal's `<skip>`. Vite's `outDir` is
  `target/classes/static` with `emptyOutDir: false` (it sits outside Vite's root, and `favicon.svg` and the logos
  are copied there by `process-resources`). `pitest.yml` and `real-api-smoke.yml` pass `-Dfrontend.skip=true`.
- Shipping: since deploy phase D1 the app ships as a boot jar (`target/cinelight.jar`) in a Docker image
  (`deploy-plan.md`). Vite's output under `target/classes/static` is packaged into that jar, so a full build
  includes the SPA. The image's builder is Ubuntu-based (`eclipse-temurin:25-jdk`), so the glibc Node that
  `frontend-maven-plugin` downloads runs there.
  Document in `dev-and-testing.md`.
- Java tests must not depend on the frontend build: a fixture `src/test/resources/static/index.html` serves for
  the "SPA at `/`" test, so `-Dfrontend.skip` and single-test IDE runs still pass.
- `.gitignore`: `frontend/node_modules/`, `frontend/coverage/`. `dependabot.yml`: add the npm ecosystem for
  `/frontend`.
- CI `frontend` job: `npm ci`, `npm run lint` (0 warnings), `npm run test:coverage` with a floor of **90% lines /
  85% branches**, diff-cover at 90% on changed `frontend/src` lines (with goldenhour's `SF:` prefix fix), and
  `npm audit --audit-level=high`.
- **Serving:** rename the Thymeleaf template to `templates/classic.html` and move `DashboardController` to
  `GET /classic` (renaming matters: Boot's welcome page falls back to a template named `index`). `GET /` is the
  static `index.html`.
- **Data hook:** one `useCineLight()` reducer owns `nowPlaying`, `history`, `scenes`, `config`, `hueStatus`,
  `dirigeraStatus`, seeded by the GETs and kept live by one `EventSource` (`message`, `now-playing`, `config`,
  `hue-status`, `dirigera-status`, `heartbeat`). Reconnect with backoff; re-fetch history and now-playing on
  reconnect.
- **Admin auth:** every write sends `X-Requested-With: XMLHttpRequest`, which makes Spring's HTTP Basic return a
  bare 401 with no `WWW-Authenticate` header (so the browser's own prompt never appears; a Java test asserts the
  header is absent for such a request). A `<dialog>` login form opens on the first 401; credentials live **in
  memory only** (never local or session storage); a wrong password says so and keeps the dialog open; the
  pending write retries after a successful login and **rolls back only if the dialog is cancelled**.
- Empty shell: header (CineLight wordmark, mode status text, mode button, More menu stub), one breakpoint at
  768px.
- Update `CLAUDE.md`'s build ladder (one line for `-Dfrontend.skip`, one for `npm test`) and `dev-and-testing.md`.

## PR 2b: Theater and House rules

**Mode switching** (HANDOVER §1.1): `view` ∈ `auto | theater | rules`, default `auto`; `auto` shows Theater
when `nowPlaying.state === 'Playing'`, else Rules. The header button sets a manual override ("House rules" /
"Theater mode", "Rules" / "Theater" under the breakpoint). **Any new media `message` event resets `view` to
`auto`.** Status text: auto → "Switches automatically with playback"; manual → "Manual · switches back on the
next event". A pure `resolveView(view, state)`.

**Theater** (screens 01, 02; exact sizes from the `.dc.html` markup and `renderVals()`): ground
`color-mix(in srgb, var(--color-neutral-900) 80%, black)`; kicker `{state} · since HH:MM`; title (Cormorant
64px / 40px mobile); meta `year · type · player`, omitting missing parts; ring gauge (r=120, circumference 754,
`stroke-dasharray` = `754 × level/100`, 0.8s transition, `role="img"` + aria-label); centre `level%` + "Room
level" + scene name, or the scene name alone when `level` is null; right column **On play** and **On pause or
stop** `<button>`s that cycle to the next scene (same room/zone as the current scene first, then the rest) via
`PUT /api/config/hue-scene`, optimistically; bottom strip: last 4 history items (`HH:MM:SS · media.<event>`,
display title, `→ Scene` or the outcome in plain words: "Ignored · rule off", "Ignored · Hue off",
"Failed"). No simulate buttons.

**House rules** (screens 03, 04): heading, the summary sentence with the two scenes as inline cycling buttons,
one sentence per media type (movie, episode, clip, track, photo slideshow) with two phrase toggles.
- Groups: "plays or resumes" = `play`+`resume`; "pauses or stops" = `pause`+`stop`+`scrobble`.
- State per group (owner decision): `on` when every event in it is on, `off` when none is, `mixed` otherwise.
  **Scrobble counts**, deliberately unlike the prototype. Off = strikethrough and grey (HANDOVER §1.3). Mixed =
  dashed underline plus an accent-700 "partly" marker, `aria-pressed="mixed"`, and a `title` naming which events
  are off. Click: `off`/`mixed` → all on, `on` → all off. Row tag `On` if either group is on or mixed, else
  `Off`.
- Writes: `PUT /api/config/plex-commands/{mediaType}` with the type's full map, optimistically.
- Right rail (desktop): "Now" (title, state, `room at N%, Scene` or just `Scene`) and "Tonight's programme"
  (history newest first, labelled "since CineLight started" until deploy phase D2 stores history in Postgres;
  after that it survives restarts and the label is just "Recent"). Mobile: last 4 events below the rules. Empty state
  "Nothing has played yet."
- Classical rules (HANDOVER §5): colour on strokes not fills, outlined buttons, weight ≤ 600, `tnum` on
  times and levels, the focus ring, every control a real `<button>`/`<a>`/`<input>`, 44px tap targets on mobile.
- Render every server string as text (React does by default; no `dangerouslySetInnerHTML` anywhere, and ESLint
  forbids it).

**Tests (PR 2b).** `resolveView`; group state (all/none/mixed, scrobble counts); toggle transitions; scene
cycling order; display-title formatter (movie, episode, track); outcome labels; reducer reset-to-auto; render
tests for both views at both widths, including null level, idle, and empty history. Optional CI-only Playwright
smoke loading `/` against `spring-boot:run`.

## PR 3a: backend for the More-menu screens

Java only; no visible UI change.

1. **`GET /api/config/sections`** (public GET, credential-free) returns
   `{adminTabEnabled, sections: [{title, editable, items: [...]}]}` built from
   `ConfigurationViewService.getConfigurationSections()`. Each item is
   `{key, label, type, duration, secret, writable, value, displayValue}`, except that a credential item
   (`SensitiveKeys.isSensitive`) carries `set: true|false` and **no `value` or `displayValue` field at all**.
   `writable` is true only for the keys `PUT /api/config/properties` accepts (the intervals), so the UI knows
   which fields get Save/Cancel. `adminTabEnabled` is `dashboard.admin-tab-enabled` (default false). Tests feed
   credential-like values (explicit keys and suffix-matched keys) and assert none appears anywhere in the raw
   response body.
2. **Reads stop going stale after a save.** Today every read (`readAllEntries`, `readBooleanProperties`,
   `readSceneProperties`) resolves values from the Spring `Environment`, which is fixed at startup, so
   `GET /api/config/plex-commands/{type}`, `GET /api/config/hue-scenes` and the sections keep showing the startup
   values. `PropertiesRepository` keeps a thread-safe map of values saved since startup, filled only after a save
   succeeds, consulted first by `resolveValue`; `readSceneProperties` goes through `resolveValue` too.
3. **A saved key missing from the file is appended**, not dropped. `saveProperties` rewrites only keys already in
   the file, so a key added after a user's file was made (such as `hue.scene-catalog-interval`) would save,
   apply live, and vanish on restart. Callers pass only whitelisted keys.
4. **`PlexCommandConfig` becomes thread-safe.** Its `EnumMap` is written by the properties listener on a request
   thread while webhook threads read it; store the per-type configs in a concurrent map.

## PR 3b: the More-menu screens

Restyled in the Classical system from design 1a (screens 20, 20b, 20c, 20d), each a hash route (`#/events`,
`#/lighting`, `#/config`, `#/debug`) opened from the header's **More** menu (disclosure button, real menu,
Escape closes, focus returns).
- **Event monitor:** history table + the selected event's sanitised `payload` JSON.
- **Lighting:** Hue bridge status, DIRIGERA status, Hue resources browser, set a scene as start/stop.
- **Config:** the **Advanced** per-event matrix (`media type × event`, where partial states are edited
  precisely); the intervals (`sse.heartbeat-interval`, `hue.status-interval`, `dirigera.status-interval`,
  `hue.scene-catalog-interval`) with explicit Save/Cancel, Save disabled until something changes; credentials as
  **Set / Not set**. A test feeds a credential-like value and asserts it is not rendered.
- **Debug** (shown only when `adminTabEnabled`): Integrate Hue toggle, show all Hue resource types, and **send
  test event** (Play / Pause / Resume / Stop + media type + optional title) via `POST /api/debug/{command}`;
  those events appear in history marked as tests.

Detail, settled when PR 3b was planned (frontend only, no Java change):
- **Routing.** `#/events`, `#/lighting`, `#/config`, `#/debug`; any other hash is the mode view. `hashchange`
  drives it, so Back works. Each screen has a heading and a "Back to the room" link to `#`. `#/debug` with
  `adminTabEnabled` false says Debug is off instead of rendering the controls.
- **More menu.** A disclosure button (`aria-expanded`, `aria-controls`) opening a list of links; Escape and an
  outside click close it, and focus returns to the button on Escape. The Debug link is shown only when
  `adminTabEnabled`. The button gets a visible outline on the light ground (found in PR 2b).
- **Data.** `GET /api/config/sections` once at start (for `adminTabEnabled`) and again whenever Config opens
  or an interval is saved. Hue and DIRIGERA status come from the existing SSE state, seeded by
  `GET /api/hue/status` and `GET /api/dirigera/status` when Lighting opens with none yet.
- **Event monitor (screen 20).** The 100-event history as a table: time, event, media, title, lights (the
  existing outcome wording); a `source: debug` row is tagged **test**. Selecting a row shows its title, event,
  player and pretty-printed `payload`. The design's Simulate buttons appear only when `adminTabEnabled`
  (owner decision: simulate sits behind the Debug toggle) and send `POST /api/debug/{event}?type=movie`. On a
  phone the table becomes a list and the payload sits below it.
- **Lighting (screen 20b).** Hue bridge card (status, Hue commands enabled, hub address, bridge id shortened,
  application key Set / Not set) and DIRIGERA card (reachable, hub address). Hue resources from
  `GET /api/hue/resources` (the bridge's CLIP v2 JSON, `data[]` of `{id, type, metadata.name}`; a 503 shows
  "bridge unreachable"): search, a type filter of All / Scene / Room / Light (every type present when "show all
  types" is on), start / stop tags on the current scenes, and **Use on start / Use on stop** for a selected
  scene via `PUT /api/config/hue-scene`. The design's per-light level panel is left out: the backend has no
  per-light levels. In its place, On start / On stop cards name the current scenes.
- **Config (screen 20c).** The matrix of media type × Play / Resume / Pause / Stop / Scrobble checkboxes, one
  `PUT /api/config/plex-commands/{type}` per change with the same optimistic update and rollback as House rules.
  Intervals are the `writable` items, shown and edited in whole seconds (1 to 86 400), sent as milliseconds to
  `PUT /api/config/properties`; Save is disabled until a value changes and all are valid, Cancel restores.
  Credentials are the `secret` items, as Set / Not set, with the note that values never reach the browser.
- **Debug (screen 20d).** Integrate Hue (`PUT /api/config/hue-enabled`), Show all Hue resource types (a
  per-browser preference in `localStorage`, read and written inside try/catch), Test Hue connection
  (`GET /api/hue/test-connection`, result shown inline), and Send test event (event, media type, optional title;
  the outcome shown inline).

## PR 3c: retire Thymeleaf

Delete `templates/classic.html`, `DashboardController`, the `/classic` route, `spring-boot-starter-thymeleaf`,
and `PlaybackEvent`'s legacy `command` object. Update `roadmap.md`, `dev-and-testing.md` and `AGENTS.md`
where they describe the dashboard.

**Done.** Thymeleaf, `/classic` and `PlaybackEvent.command` are removed. The nullable `command_title` and `duration`
columns of `playback_event` are deliberately kept (V1 is not edited, no migration): the repository stops writing and
reading them, so rolling back to an older image is safe, and a later migration can drop them.

**Follow-up.** `GET /api/config/plex-commands/{mediaType}` and `GET /api/config/hue-scenes` are removed, along with
`ConfigurationViewService.getPlexProperties` / `getSceneProperties` and `PropertiesRepository.readBooleanProperties` /
`readSceneProperties`: only the Thymeleaf page read them. The dashboard reads `GET /api/config` and
`GET /api/config/sections`; the `PUT` handlers on both paths stay.

## Delivery

Each PR goes through CLAUDE.md steps 2 to 6: a Sonnet build, review, CI + Codex, and a squash merge. Each PR adds
its own `changelog.d/` entry.

## Risks accepted

- **Broadcast after execute** delays SSE by the Hue call's latency. Accepted: the UI needs the outcome, and the
  webhook already waited on Hue.
- **Node in the Maven build** slows `clean verify` and `compile`; `-Dfrontend.skip=true` covers the inner loop.
- **Mixed state differs from the prototype**: owner-approved, noted in the changelog.
- **Room level is the scene's set level**, not a reading; null hides it.
- **History is in memory** until deploy phase D2 (`deploy-plan.md`), which keeps it in Postgres and seeds the
  ring on start; the "since CineLight started" label goes when D2 lands.
