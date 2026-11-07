-- Playback history: a write-behind copy of the in-memory ring (see PlaybackHistoryWriter). Only media.* events are
-- stored. id is PlaybackService's clock-derived id (milliseconds * 1000 + a counter); it is the primary key and
-- inserts use ON CONFLICT (id) DO NOTHING. payload is the already-sanitised Plex payload (jsonb reorders keys).
-- command_title and duration rebuild the legacy command object exactly until it is removed (for unsupported media
-- its title is '', not the event title).
CREATE TABLE playback_event (
    id                bigint      PRIMARY KEY,
    occurred_at       timestamptz NOT NULL,
    event             text        NOT NULL,
    media_type        text,
    title             text,
    grandparent_title text,
    parent_index      int,
    idx               int,
    year              int,
    player            text,
    outcome           text        NOT NULL,
    scene_id          text,
    source            text        NOT NULL,
    payload           jsonb,
    command_title     text,
    duration          int
);

CREATE INDEX playback_event_occurred_at_idx ON playback_event (occurred_at);
