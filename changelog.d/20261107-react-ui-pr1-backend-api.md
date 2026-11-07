### Added — Backend API for the dashboard redesign: playback history, now-playing, scene catalog, outcomes

CineLight now remembers the last 100 media events since boot (`GET /api/playback/history?limit=`), the current
now-playing state (`GET /api/playback/now`, also pushed as a `now-playing` SSE event) and each event's outcome (scene
applied, ignored because the rule or Hue is off, unsupported, or failed). `GET /api/hue/scenes` lists scene names,
rooms and the level each scene sets, cached from the bridge on its own slow schedule (new `hue.scene-catalog-interval`,
default 300000 ms, editable like the other intervals). `GET /api/config` and the `config` SSE event now carry the scenes
and per-type Plex switches as well, and saving a scene or a Plex rule now updates other open dashboards live. The Debug
endpoint takes an optional `title` and runs through the same path as a real webhook, so debug events appear in history
(marked `source: debug`). Nothing changes on screen.

### Security — Plex payloads are rebuilt from an allowlist; the legacy dashboard no longer renders titles as HTML

The Plex payload sent to the public SSE stream and stored in the history keeps only the event, user flags, device name
and basic media fields: the account name and avatar, the player's public IP and uuid, the server uuid and artwork paths
are dropped. The classic dashboard's event list and Hue resource filter now render server strings as text, closing a
stored-XSS path now that history replays titles.

### Changed — Webhook outcome and scheduler

An unrecognised Plex event is now reported as unsupported even while Hue integration is off (the lights are untouched
either way). A Hue exception while handling a webhook is recorded as a failed event and the webhook still answers 200.
The scheduler pool grows from three threads to four for the scene catalog refresh.
