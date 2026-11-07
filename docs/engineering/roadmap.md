# Roadmap

Moved from `CLAUDE.md` on 2026-11-07 without changes in substance.

## Philips Hue client — published (done)

`hue-java-client` lives in `gregochr/hue-java-client` and is consumed from JitPack as
`com.github.gregochr:hue-java-client:v1.0.0`; the local `../hue-java-client` copy is gone. The library API and the
intentional trust-all SSL context (for self-signed LAN bridge certificates) are documented in that repo's README.

## JDK 25 and Spring Boot 4

Phase 3 of `reboot-plan.md`, under the new gates.

## Apple TV integration

Two deployment flavours:

1. **Plex-only** (current), unchanged.
2. **Apple TV**: listens for playback events from the Apple TV (MQTT, ATV remote, or a `pyatv`-style HTTP bridge)
   so the lighting works for **any** app (Netflix, Disney+, …), not just Plex.

Approach: abstract the lighting trigger behind a `MediaPlaybackEvent` interface. The Plex webhook handler and the
future Apple TV adapter both publish to it; the Hue controller subscribes.

## React UI rewrite

Replace the Thymeleaf dashboard with a standalone React SPA.

- The REST API (`/api/**`) and SSE (`/api/events`) stay unchanged; the React app is a pure consumer.
- Served as static assets by Spring Boot, or a separate dev server with a proxy.
- React Query for server state, local state for UI; real-time updates from the existing SSE stream.
- When it lands, port goldenhour's frontend gates (Vitest coverage floor, changed-lines coverage, `npm audit`,
  Playwright smoke).
