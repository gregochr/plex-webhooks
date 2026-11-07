### Added — React frontend toolchain and app shell (PR 2a of the dashboard redesign)

`GET /` now serves a new React (Vite) dashboard shell: the CineLight header with the automatic or manual mode status,
the mode button and a More menu, in the Classical design. Theater and House rules are still placeholders, and the
data layer (live playback, scenes, config and Hue/DIRIGERA status over one event stream) and the admin sign-in
dialog are in place for the next PRs. The previous dashboard remains at `GET /classic`.

The Maven build now runs `npm ci` and `npm run build` (Node 22 is downloaded into `target/`, so nothing needs
installing); pass `-Dfrontend.skip=true` to skip it. CI gains a frontend lint, test and coverage job.
