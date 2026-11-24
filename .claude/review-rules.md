# Review rules

Shared, canonical review rules for this repo. Consumed by the `sdk-review`, `brief`, `securityReview`, and
`attackSimulation` skills. `AGENTS.md` ("Architecture", "JavaScript integration" and "Public-API discipline")
remains the source of truth — if it ever conflicts with this file, it wins.

## Tone

Be concise and actionable. Focus on correctness, performance, and security issues.

## Path filters

Skip files matching:

- `node_modules/**`
- `build/**`
- `**/build/**`
- `**/.gradle/**`
- `kotlin-js-store/**`
- `**/*.lock`
- `**/package-lock.json`
- `js-chat/dist/**` (except `js-chat/dist/index.d.ts` — check it is in sync with the source of truth)
- `js-chat/dist-test/**` (same exception for `index.d.ts`)
- `js-chat/upload/**`
- `**/generated/**`
- `pubnub-3p-diff-match-patch/**` (vendored third-party code)
- `Sample Chat app/**`
- `config/detekt/baseline-*.xml` (review only that a baseline did not grow to hide new findings)

## Path-specific rules

Match each file against the globs below. A file may match more than one rule — apply all that match.

### `pubnub-chat-api/**` — public KMP API

- Backward compatibility of interface methods, data class fields, constructor and default parameters.
- Changes to default parameter values are public-API changes even when signatures compile.
- Additions/removals/reordering of public enum entries or sealed-class subtypes break exhaustive `when` downstream.
- KDoc must reflect new/changed behavior in the same diff.
- `api/pubnub-chat.api` must be updated in the same diff for any public signature change (`apiCheck` fails otherwise).
- Every `expect` declaration has a matching `actual` on every enabled target (JVM + JS + iOS simulator arm64 by default; release enables all Apple targets).

### `pubnub-chat-impl/src/commonMain/**` — shared implementation

- Thread safety on shared mutable state (caches, registries, listener maps, rate limiters, typing timers).
- Coroutine and `PNFuture` error handling: failures propagate to the caller, nothing is swallowed silently.
- No subscription/listener leaks — everything registered (`streamUpdatesOn`, `connect`, `onX` listeners) returns an `AutoCloseable` that actually releases it, including reference-counted shared subscriptions.
- Correct use of the PubNub Kotlin Core SDK (filters, pagination, `includeX` flags); user input placed in channel names, filter expressions or metadata is validated/escaped.
- Immutable entities stay immutable — updates return new instances.
- Platform-agnostic: no JVM-only types or APIs in `commonMain`.
- Efficient collections, no accidental O(n²) on hot paths (message streams, history pagination).

### `pubnub-chat-impl/src/jsMain/**` and `src/jsMain/**` — JS wrapper surface

- Every new/changed `*Js.kt` method is declared in `src/jsMain/resources/index.d.ts` (source of truth) and that file is synced to `js-chat/dist/index.d.ts` and `js-chat/dist-test/index.d.ts`.
- New external types go in `types.kt`, conversions in `converters.kt`; converters handle `null` vs `undefined` and optional fields.
- Promise rejections carry the underlying error; no unhandled rejections or swallowed exceptions.
- A matching test exists in `js-chat/tests/<entity>.test.ts`.
- Generated `.js` under `js-chat/` must not be hand-edited.

### `src/jsMain/resources/index.d.ts`, `js-chat/dist*/index.d.ts` — TypeScript declarations

- The three copies are identical.
- Types match the Kotlin wrapper (optionality, Promise return types, union literals).
- Removing or narrowing a declaration is a breaking change for JS customers.

### `pubnub-chat-impl/src/jvmMain/**`, `pubnub-chat-impl/src/appleMain/**`, `src/iosArm64Main/**` — platform code

- Behavior parity with the other platforms for the same `expect`.
- No platform-specific leaks into common signatures.

### `js-chat/tests/**` — JS integration tests

- Tests sit in the file matching the entity (`channel.test.ts`, `membership.test.ts`, …).
- No fixed sleeps where an awaited condition is possible; clean up created channels/users and close subscriptions.

### `build-logic/**`, `gradle/libs.versions.toml`, `**/build.gradle.kts`, `js-chat/package.json` — build

- Publication / signing configuration correctness.
- Version-catalog references are valid; PubNub Core versions track the latest releases (don't pin or downgrade).
- `pubnub.*` plugins are applied consistently; detekt/ktlint coverage is not reduced.

### `.github/**` — CI and release

- Secrets are not echoed; `permissions:` stay minimal.
- Release hook scripts keep working for both products (`kotlin`, `js`).

### `**/*Test*`, `**/*.test.ts` — test code

- Assertions are meaningful, not trivially true.
- No flaky timing/ordering dependencies (sleeps, hardcoded waits, reliance on iteration order).
- Proper isolation — tests don't bleed state into each other (random ids, cleanup).
- Behavior fixes come with a test that fails without the fix.
