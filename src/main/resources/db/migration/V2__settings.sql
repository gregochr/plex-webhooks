-- Settings copy: a mirror of CineLight's settings file (see SettingsMirror), plus a log of what changed. The file stays
-- the only thing the app starts from. Credentials (SensitiveKeys) and spring.datasource.* keys are never stored.
-- setting: one row per non-credential key in the file, kept equal to the file by a whole-file reconcile.
CREATE TABLE setting (
    key        text        PRIMARY KEY,
    value      text        NOT NULL,
    updated_at timestamptz NOT NULL
);

-- setting_change: one row per difference found. old_value is null for a key first seen, new_value is null for a key
-- removed. source: 'initial' (first reconcile into an empty table), 'dashboard' (the key was in a dashboard save
-- since the last reconcile), 'sync' (any other difference: a hand edit of the file, a save made while the database
-- was down). No retention: the log is tiny.
CREATE TABLE setting_change (
    id         bigserial   PRIMARY KEY,
    changed_at timestamptz NOT NULL,
    key        text        NOT NULL,
    old_value  text,
    new_value  text,
    source     text        NOT NULL CHECK (source IN ('initial', 'dashboard', 'sync'))
);

CREATE INDEX setting_change_changed_at_idx ON setting_change (changed_at);
