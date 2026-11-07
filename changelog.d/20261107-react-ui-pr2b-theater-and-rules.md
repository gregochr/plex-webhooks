### Added — Theater and House rules views (PR 2b of the dashboard redesign)

The new dashboard at `GET /` now shows its two main views. Theater mode (dark, shown while something plays) has the
title, a ring for the level the current scene sets, the two scenes and the last four events. House rules (light)
reads each media type's rules as a sentence whose two phrases switch the lights on or off for that type, plus the
current scenes, a "Now" panel and the event log. Tapping a scene cycles to the next one, and a rule phrase toggles
its events; both save at once and roll back if the save fails or sign-in is cancelled. A change made on another
dashboard appears live.

Deliberate departure from the design prototype: a phrase can be partly on. "Pauses or stops" covers pause, stop and
scrobble, and the prototype ignored scrobble, so a media type with scrobble off looked fully on. It now shows a dashed
underline, a "partly" marker and a tooltip naming the events that are off; clicking turns them all on. Without a
Hue bridge (no scene list) the scenes show their configured id or "Not set" and cannot be cycled.
