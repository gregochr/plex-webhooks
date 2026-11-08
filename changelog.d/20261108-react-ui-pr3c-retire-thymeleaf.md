### Removed — The old `/classic` dashboard and the history `command` field

The legacy `/classic` page is gone; the React dashboard at `/` is the only UI. The playback history and SSE event
JSON no longer carries a `command` object (use `event`, `title` and `payload`). The `command_title` and `duration`
columns stay in the database, unused and left null, so rolling back to an older image is still safe.
