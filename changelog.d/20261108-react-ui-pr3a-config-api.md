### Fixed — Saved settings show up straight away, and new keys are kept

After you save a setting (Plex commands, Hue scenes, intervals), the next read now returns the new value instead of the one from startup. A setting that was missing from the settings file is now added to the end of the file instead of being silently dropped. The Plex command rules are also now safe to read while a save is applying.

### Added — `GET /api/config/sections` settings endpoint

A public, read-only endpoint lists the settings sections for the new dashboard. Credentials (Hue application key, passwords, tokens, secrets and API keys) are reported only as set or not set; their values are never sent.

### Security — Settings writes accept only known keys and single-line values

Saving Plex commands now writes only the known `plex.<type>-<event>-enabled` keys, Hue scene ids must be letters, digits and hyphens, and the settings file writer refuses any key or value containing a line break, so a request cannot add extra lines to the settings file.
