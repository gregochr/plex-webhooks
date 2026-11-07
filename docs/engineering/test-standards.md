# Test standards — CineLight

Adapted from goldenhour's `docs/engineering/test-improvement-standards.md` (2026-11-07). Read this before writing
or changing any test class.

## Philosophy

Tests exist to protect behaviour, not to hit a coverage number. A test that passes when the feature is broken is
worse than no test: it creates false confidence. The coverage gates (95% line and 90% branch per class, 90% of
changed lines on a PR) are a floor that catches untested code, not proof that the tests are good.

## The three questions every test must answer

1. **What specific behaviour is being tested?** One test, one behaviour. Not "the Plex controller works" but "a
   `media.pause` event for a movie, with pause enabled for movies, activates the configured pause scene."
2. **What exact inputs produce what exact outputs?** Specific values, not `any()`. If sunset is the threshold, test
   one second before, exactly at, and one second after.
3. **What breaks if this test fails?** If you cannot answer, the test protects nothing.

## Smells to eliminate

### 1. `any()` in `verify()`

```java
// BAD: proves a scene was activated, not which one
verify(hueBridgeClient).activateScene(any());

// GOOD
verify(hueBridgeClient).activateScene("04a6c3e1-scene-pause");
```

Where the argument is an object, capture it with `ArgumentCaptor` and assert its fields.

### 2. `any()` in `when()` where the input is knowable

```java
// BAD: returns the canned response whatever coordinates are requested
when(sunriseSunsetClient.getSunsetTimeForToday(anyDouble(), anyDouble())).thenReturn(response);

// GOOD: a wrong latitude/longitude reaching the client now fails the test
when(sunriseSunsetClient.getSunsetTimeForToday(54.777797, -1.600031)).thenReturn(response);
```

### 3. `lenient()` stubs

If Mockito reports a stub as unnecessary, either delete it (the path does not need it) or fix the test (it is not
exercising the path you think). Do not silence it.

### 4. Missing boundary tests

Every threshold gets a test at the boundary, one below and one above: the sunset comparison, each Plex event ×
media-type enable flag, config value validation, SSE heartbeat and retry intervals.

### 5. `@SpringBootTest` overuse

A full context takes seconds; a unit test takes milliseconds. Use `@ExtendWith(MockitoExtension.class)` or plain
construction. Use `@WebMvcTest` for a controller's HTTP contract and security. Keep `@SpringBootTest` for tests that
genuinely need the whole context (wiring, event publication across beans).

### 6. Time

- Production code takes an injected `java.time.Clock`; it never calls `now()` without one.
- Pin `Clock.fixed(...)` to an instant whose date **cannot** be the day the suite runs: use a past year (2024) or a
  far-future one. The mutation these tests exist to catch is "the code ignores the injected clock". That broken code
  reads the real date, and if the fixture happens to equal it, the test passes on exactly the day you write it.
- No `Thread.sleep()`. Inject a clock or a scheduler you control.

### 7. Live third-party services

A test that calls a real external API (sunrise-sunset.org, the postcode lookup, a real Hue bridge) is tagged
`@Tag("real-api")`. Those tests are excluded from the default run and from every PR gate, and run weekly in
`real-api-smoke.yml`. Assert shape and plausibility there, not exact values the provider can recompute. The build
gate covers the same client code deterministically with `MockRestServiceServer` and canned JSON.

## Tags

| Tag | Meaning | Runs |
|---|---|---|
| *(none)* | Unit test | Every build |
| `integration` | Spring context or several real components together, no network | Every build |
| `real-api` | Calls a live third-party service | Weekly `real-api-smoke.yml`, or `-Dsurefire.excludedGroups= -Dgroups=real-api` |

## Process for improving a test class

1. Read the class under test first.
2. Remove every `lenient()`; resolve why it was needed.
3. Replace `any()` in `verify()` with exact values or captors.
4. Replace `any()` in `when()` with exact values where the input is knowable.
5. Add boundary tests for every threshold.
6. Downgrade `@SpringBootTest` where the context is not needed.
7. Run the full gate (`./mvnw clean verify`, exit code 0).

## Mutation-checking a test without destroying work

Mutating production code to prove a test can fail is encouraged. Restoring it is where work gets lost.

- **Never restore with `git checkout -- <path>`, `git restore` or `git stash`.** They revert to HEAD and silently
  delete any uncommitted change in the file, usually the very change under test.
- Back up with `cp` and restore with `cp`, or commit first, or mutate in a separate worktree.
- Confirm the restore: `git diff` on the file shows exactly the change you started with.
- PIT does this systematically: `./mvnw test-compile pitest:mutationCoverage`. The codebase is small enough to run
  it locally; a narrowed run takes `-Dpit.targetClasses='gregochr.services.sse.*'`.

## What not to do

- Do not add tests that assert nothing meaningful (a constructor sets a field) to move a coverage number.
- Do not add `@Disabled` tests: fix them or delete them.
- Do not test private methods directly: test through the public API.
- Do not write a test that could never fail. If you cannot imagine the code change that breaks it, it protects
  nothing.
