### Added — A copy of the settings file in Postgres, with a change log

When a database is configured, CineLight now keeps a copy of the settings file in the `cinelight` database (`setting`) and logs every difference it finds (`setting_change`: old value, new value, and whether it came from a dashboard save, a hand edit or the first copy). The file is still the only thing the app starts from and reads, and credentials (the Hue key, the admin password, anything named like a token, secret or key) and `spring.datasource.*` are never copied, so they cannot reach a nightly backup or a release dump. A save never waits on the database and works with it down. `scripts/settings-export.sh` on the host prints the settings (`key=value`) or the last changes (`--changes [N]`).

### Fixed — Two dashboard saves at the same moment can no longer lose one change

Saving settings now takes turns, so two simultaneous `PUT /api/config/**` requests can no longer overwrite one another's change in the settings file. A new line in a value (CR or LF) is refused with 400 by every config save, including `PUT /api/config/hue-scene`, which used to accept any scene id and so could inject a line into the file.

### Changed — Database startup retries each part separately

If the playback-history step or the settings-copy step fails after the migration, only that step is retried (every 30 s), without undoing the other. A table that disappears (a rebuilt `utils-pg`) is recreated after a normal 30 s wait, not at once, and a table still missing straight after a successful migration now logs one error and stops retrying instead of looping.
