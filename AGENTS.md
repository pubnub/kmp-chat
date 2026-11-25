# AGENTS.md

Guidance for AI coding agents working in this repository, and the single source of truth for guidance shared by all agents. Agents that read `AGENTS.md` (Cursor, Codex and others) load it directly; `CLAUDE.md` imports it for Claude Code. Put guidance that applies to every agent here, and keep only Claude Code-specific notes in `CLAUDE.md`.

Shared review rules (tone, path filters, path-specific rules) live in `.claude/review-rules.md`.

## Project overview

This is the **PubNub Chat SDK** core, written in Kotlin Multiplatform as a chat-domain layer on top of the PubNub Kotlin Core SDK. One shared codebase (`commonMain` + per-target source sets) is shipped as separate platform artifacts rather than as a multiplatform library for KMP consumers.

Two products ship from this repo (see `.github/workflows/release/products.json`):

- **kotlin** (root `.pubnub.yml`, tag `kotlin-X.Y.Z`)
  - **Kotlin Chat SDK for JVM/Android** — `com.pubnub:pubnub-chat` on Maven Central. Gradle also publishes KMP metadata and `-js` / `-iossimulatorarm64` klibs as a side effect of the build; these are not a supported distribution (there are no device Apple klibs).
  - **`PubNubChat.xcframework`** — the Kotlin/Native binary for iOS/macOS/tvOS, attached to the GitHub Release and wrapped by `Package.swift` / `PubNubChat.podspec`. Apple app developers use the **Swift Chat SDK**, which lives in its own repository and builds on this binary.
- **js** (`js-chat/.pubnub.yml`, tag `X.Y.Z`) — **JS Chat SDK**, `@pubnub/chat` on npm, built from the Kotlin/JS output plus `js-chat/`.

## Build & test

Java 21 is required to drive the build (CI uses Zulu 21). Node 22 is used for `js-chat/`.

**Builds, tests and linters go through `scripts/dev/`, never raw `./gradlew`** (in Claude Code a `PreToolUse` hook blocks it). The wrappers print one `PASS`/`FAIL` line plus the first failing tests/errors and write the full output to `build/agent-logs/`; read that log only if the summary is not enough.

| Command | What it runs |
|---|---|
| `scripts/dev/check.sh lint` | `ktlintCheck` |
| `scripts/dev/check.sh static` | `ktlintCheck detekt` (same as CI "Static analysis") |
| `scripts/dev/check.sh api` | `apiCheck` (binary-compatibility validator, `api/pubnub-chat.api`) |
| `scripts/dev/check.sh compile` | JVM + JS main and test compilation (tests are compiled, not run) |
| `scripts/dev/check.sh unit [--tests X]` | `:pubnub-chat-impl:jvmTest` limited to `com.pubnub.kmp.*` and `com.pubnub.internal.*` (no network) |
| `scripts/dev/check.sh jslint` | `npm --prefix js-chat run lint` (ESLint, as in CI) |
| `scripts/dev/check.sh format` | `ktlintFormat` — **writes files**; may be run without asking |
| `scripts/dev/check.sh apidump` | `apiDump` — **writes `api/*.api`**; may be run without asking, then review the `.api` diff |
| `scripts/dev/integration-kotlin.sh [--tests X]` | `:pubnub-chat-impl:jvmTest --tests 'com.pubnub.integration.*'` plus the root module's `:jvmTest` — real PubNub, opt-in. `--tests X` runs only the impl module; `--root [--tests X]` only the root module |
| `scripts/dev/js-build.sh` | Kotlin/JS production build, packs `js-chat/dist`, then `npm run build` |
| `scripts/dev/integration-js.sh [tests/x.test.ts]` | jest suite in `js-chat/` — real PubNub, opt-in, needs `js-build.sh` first |

`lint`, `static`, `api`, `compile`, `format`, `apidump` can be combined in one Gradle run (`check.sh static api compile`).

CI (`.github/workflows/run-tests.yml`) runs `./gradlew check -x detekt` — all targets' tests including integration tests — and `run-validations.yml` runs `ktlintCheck detekt` plus ESLint for `js-chat/`.

### Tests

- **Kotlin integration tests** hit real PubNub and live in `pubnub-chat-impl/src/commonTest/kotlin/com/pubnub/integration/` (base class `BaseChatIntegrationTest`), with a few platform-specific ones in `jvmTest`/`jsTest`/`appleTest` under the same package. They run inside the normal `*Test` tasks — there is no separate `integrationTest` source set.
- **Root-module integration tests** (base class `BaseIntegrationTest` from `pubnub-chat-test`): `src/commonTest/kotlin/com/pubnub/integration/MessageDraftIntegrationTest.kt` covers the `createMessageDraft()` / `createThreadMessageDraft()` extensions in the root module's `src/commonMain/kotlin/com/pubnub/chat/mediators.kt`, so it cannot move to `pubnub-chat-impl`; `src/jvmTest/kotlin/compubnub/chat/ChatIntegrationTest.kt` (package `compubnub.chat`) is JVM-only. Both run in the root `:jvmTest`, not in `:pubnub-chat-impl:jvmTest`.
- **Kotlin unit tests** live under `com.pubnub.kmp` and `com.pubnub.internal`. Fakes and helpers: `pubnub-chat-impl/src/commonTest/kotlin/com/pubnub/kmp/utils/`.
- **JS tests** are hand-written jest integration tests in `js-chat/tests/*.test.ts`. Each entity has its own file (`channel.test.ts`, `membership.test.ts`, `thread-channel.test.ts`, `channel-group.test.ts`, …); new tests go in the file matching the entity being tested.

### Test credentials

- **Kotlin:** `pubnub-chat-test/build.gradle.kts` (buildkonfig `Keys` object) reads `test.properties` in the repo root (git-ignored; keys `pubKey`, `subKey`, `pamPubKey`, `pamSubKey`, `pamSecKey`), else env vars `SDK_PUB_KEY`, `SDK_SUB_KEY`, `SDK_PAM_PUB_KEY`, `SDK_PAM_SUB_KEY`, `SDK_PAM_SEC_KEY`, else silently falls back to `demo`. `integration-kotlin.sh` refuses to run unless all five keys are set, non-empty and not `demo`.
- **JS:** `js-chat/tests/utils.ts` loads `js-chat/.env` via dotenv (git-ignored): `PUBLISH_KEY`, `SUBSCRIBE_KEY`, `PAM_PUBLISH_KEY`, `PAM_SUBSCRIBE_KEY`, `PAM_SECRET_KEY`, `PAM_WITH_INTEGRITY_PUBLISH_KEY`, `PAM_WITH_INTEGRITY_SUBSCRIBE_KEY`, `PAM_WITH_INTEGRITY_SECRET_KEY`. `integration-js.sh` refuses to run unless all eight are set (env var or `.env`), non-empty and not `demo`.
- Never print, log or commit key values.

### Multiplatform targets

Targets are gated by `gradle.properties` flags (`ENABLE_TARGET_JS`, `ENABLE_TARGET_IOS_OTHER`, `ENABLE_TARGET_IOS_SIMULATOR_ARM64`, `ENABLE_TARGET_IOS_ALL`). The default profile enables JVM + JS + iOS simulator (arm64); release builds use `ENABLE_TARGET_IOS_ALL=true`. iOS simulator testing requires a configured simulator id; CocoaPods integration may need `pod repo update` before a clean build.

## Working rules

- **Behavior fixes ship a regression test.** After changing production behavior, extend or add a test that fails without the fix. Do not stop at the code edit. For bugs, write the failing test first.
- **Source edits go through the `Edit` tool** so each diff can be reviewed. Do not script source changes through Bash (`sed`, `perl`, heredocs, etc.).
- **Do not start implementing until the user explicitly says to.** Approving a plan is not a go-ahead.
- **Git:** commit or push only when asked. Right after creating a new file, register it with `git add` (or `git add -N`) so it shows up in the change set; don't stage edits to existing files until asked to commit, and never `git add -A`/`.`. Temporary/helper files go under `build/agent-logs/` (git-ignored) and are never added. When asked to commit, `git add` every file going into the commit by explicit path — including new files registered earlier, since they may have changed (`AM`) or been added with `-N` (a plain `git commit` skips those). `git commit -am` includes `-N` files but silently skips fully untracked (`??`) files, so re-check `git status` before committing.
- **Before reporting Kotlin changes as done, run `scripts/dev/check.sh lint`.** If a public API changed, also run `check.sh api`. If a JS wrapper changed, also run `check.sh compile`.
- **Integration tests are opt-in** (real PubNub, need credentials): run `integration-kotlin.sh` / `integration-js.sh` only when the user asks; recommend them in the report when behavior changed.
- **Core SDK versions are not fixed.** The Chat SDK tracks the latest PubNub Kotlin Core (`pubnub` in `gradle/libs.versions.toml`) and JS Core (`pubnub` in `js-chat/package.json`) releases. Always read the current versions from those files rather than from documentation.

## Architecture

### Module layout

- `pubnub-chat-api/` — public API interfaces and contracts (what customers interact with).
- `pubnub-chat-impl/` — concrete implementations of the API layer.
- `pubnub-chat-test/` — shared test utilities, buildkonfig test keys.
- `pubnub-3p-diff-match-patch/` — vendored third-party diff-match-patch; not analysed by ktlint/detekt.
- `js-chat/` — npm package: generated JS from the Kotlin/JS build plus hand-maintained `.d.ts`, rollup config and jest tests.
- Root project (`src/`) — JS entry/exports and the TypeScript declaration source of truth (`src/jsMain/resources/index.d.ts`), Apple packaging.
- `build-logic/` — `includeBuild` with the `pubnub.*` Gradle plugins (`build-logic/gradle-plugins/`) and custom ktlint rules (`build-logic/ktlint-custom-rules/`).
- `api/` — binary-compatibility-validator dump.
- `examples/`, `Sample Chat app/` — samples, not published.

### Source sets

Each KMP module uses `commonMain` (shared logic), `jvmMain`, `jsMain`, `appleMain` (iOS/macOS/tvOS), and matching `*Test` sets (plus `nativeTest`).

### Key design patterns

- **API-implementation separation** — public interfaces in `pubnub-chat-api`, internals in `pubnub-chat-impl`.
- **Immutable entities** — `Channel`, `User`, `Message`, `Membership` etc. are snapshots; updates produce new instances and streams (`streamUpdates`, `streamUpdatesOn`) deliver them.
- **Asynchronous APIs** — Kotlin coroutines / `PNFuture`; JS wrappers expose Promises.
- **Resource lifecycle** — streaming/listener methods return `AutoCloseable`; every registration must have a release path.

## JavaScript integration

### TypeScript declarations — source of truth

**CRITICAL**: the TypeScript declaration file has THREE copies that must stay in sync:

- **`src/jsMain/resources/index.d.ts`** — the SOURCE OF TRUTH (always edit this one)
- `js-chat/dist/index.d.ts` — build output copy
- `js-chat/dist-test/index.d.ts` — test build copy

When modifying JS wrappers, always update `src/jsMain/resources/index.d.ts` first, then sync the `dist/` copies from it.

### JS wrapper files

- **Kotlin wrappers**: `pubnub-chat-impl/src/jsMain/kotlin/*Js.kt` — one per entity (`ChannelJs`, `MembershipJs`, `ThreadChannelJs`, …).
- **Types**: `pubnub-chat-impl/src/jsMain/kotlin/types.kt` — JS-specific external interfaces.
- **Converters**: `pubnub-chat-impl/src/jsMain/kotlin/converters.kt` — Kotlin-to-JS conversion utilities.
- **JS tests**: `js-chat/tests/*.test.ts` — hand-written integration tests (NOT generated).

`js-chat/` also contains JavaScript **code** generated from the Kotlin compilation. Never edit generated `.js` files directly; `.d.ts` declarations and tests are hand-maintained.

### JS wrapper checklist

When adding a method to the JS wrapper:

1. Add the Kotlin wrapper method in the appropriate `*Js.kt` file.
2. Add new types to `types.kt` and converters to `converters.kt`.
3. Update **`src/jsMain/resources/index.d.ts`** (source of truth) with the TS declaration.
4. Sync to `js-chat/dist/index.d.ts` and `js-chat/dist-test/index.d.ts`.
5. Add integration tests in the matching `js-chat/tests/<entity>.test.ts`.
6. Run `scripts/dev/check.sh compile` to verify Kotlin/JS compilation.

## Development workflow

1. **API changes** — start in `pubnub-chat-api/`, modify interfaces first.
2. **Implementation** — update `pubnub-chat-impl/`.
3. **Platform-specific** — add code in the appropriate source sets; keep `expect`/`actual` complete.
4. **Tests** — add tests in common and platform-specific test directories.
5. **JavaScript** — update wrappers + `.d.ts`, rebuild JS artifacts (`scripts/dev/js-build.sh`) after Kotlin changes.

## Public-API discipline

`pubnub-chat-api` and the JS `index.d.ts` surface are consumed by external customers; the Apple exports in `PubNubChat.xcframework` are consumed by the Swift Chat SDK (separate repository), so breaking them breaks that SDK's build. When changing them:

- Default values on public parameters/properties (limits, timeouts, intervals, flags) are part of the behavior contract — flag changes in PRs.
- Adding/removing/reordering entries in public enums or sealed hierarchies is a source-compat break for users with exhaustive `when`.
- KMP `expect` declarations require matching `actual` on every enabled target.
- Any public signature change must come with an updated `api/pubnub-chat.api` dump (`check.sh apidump`) and KDoc in the same diff.
- Any JS-visible change must update `index.d.ts` in all three copies.

## Code style

- Official Kotlin code style; ktlint with custom rules from `build-logic/ktlint-custom-rules/`; detekt config in `config/detekt/` with per-module baselines.
- KDoc for public APIs.
- No comments that just restate the code; comment only non-obvious "why".

## Release

- Maven Central (JVM/Android), npm (JS), and `PubNubChat.xcframework` on the `kotlin-X.Y.Z` GitHub Release (consumed via `Package.swift` / CocoaPods by the Swift Chat SDK).
- Releases are triggered per product from a merged PR with `@pubnub-release-bot release kotlin as X.Y.Z` or `@pubnub-release-bot release js as X.Y.Z`. The PR body is the changelog source — see the `pr-description` skill.
