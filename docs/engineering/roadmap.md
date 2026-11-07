# Roadmap

Moved from `CLAUDE.md` on 2026-11-07 without changes in substance.

## Philips Hue client — published (done)

`hue-java-client` lives in `gregochr/hue-java-client` and is consumed from JitPack as
`com.github.gregochr:hue-java-client:v1.0.0`; the local `../hue-java-client` copy is gone. The library API and the
intentional trust-all SSL context (for self-signed LAN bridge certificates) are documented in that repo's README.

## JDK 25 and Spring Boot 4 (done)

Phase 3 of `reboot-plan.md`: JDK 25, Spring Boot 4.1.2, Jackson 3.

## Apple TV integration

Two deployment flavours:

1. **Plex-only** (current), unchanged.
2. **Apple TV**: listens for playback events from the Apple TV (MQTT, ATV remote, or a `pyatv`-style HTTP bridge)
   so the lighting works for **any** app (Netflix, Disney+, …), not just Plex.

Approach: abstract the lighting trigger behind a `MediaPlaybackEvent` interface. The Plex webhook handler and the
future Apple TV adapter both publish to it; the Hue controller subscribes.

## React UI rewrite

Planned in detail, with the owner's decisions, in `react-ui-plan.md` (2026-11-07).
