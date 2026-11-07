# changelog.d

Every meaningful change adds **one new file** here instead of editing a shared changelog, so two open PRs never
conflict on the same lines (the approach goldenhour uses).

- Name: `YYYYMMDD-<slug>.md`, for example `20261107-real-api-tag.md`.
- Content: a `### <Category> — <title>` heading, then one to three sentences on what changed for someone running
  CineLight and why. Categories: `Added`, `Changed`, `Fixed`, `Removed`, `Security`, `Build`.
- Never edit an entry that has already merged; add a new one.

```markdown
### Fixed — Builds no longer depend on the live sunrise-sunset.org API

Tests that call real third-party services are tagged `real-api` and run weekly instead of on every PR, so a provider
changing its data can no longer turn every build red.
```

When releases start being cut, a release step folds these files into `CHANGELOG.md` and deletes them.
