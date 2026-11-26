---
name: sdk-review
description: PubNub Kotlin Multiplatform Chat SDK (kmp-chat) code review — applies the shared review rules from .claude/review-rules.md and the public-API / JS-declaration discipline from AGENTS.md. Asks whether to review the full branch vs master or only uncommitted changes, then produces a read-only report (no edits).
---

# sdk-review

A repo-specific, **read-only** code review for the Kotlin Multiplatform Chat SDK and its JS product (`js-chat/`). Rules come from `.claude/review-rules.md` (tone, path filters, path-specific rules) and `AGENTS.md` ("Architecture", "JavaScript integration", "Public-API discipline"). Read `.claude/review-rules.md` before Step 2. Treat those two files as the source of truth — if they ever conflict with this skill, they win.

## Non-goals (hard constraints)

- Do NOT use `Edit`, `Write`, or `NotebookEdit`. This skill produces a report only.
- Do NOT run mutating tasks (`check.sh format`, `check.sh apidump`, publishing, anything that writes outside `build/`). Recommending them in the report is fine; running them is not.
- Do NOT commit, stage, push, or modify git state.
- If the user wants fixes, they re-run after applying changes themselves, or use the general-purpose `code-review` skill.

## Step 1 — Ask scope

Before reading any code, call `AskUserQuestion` with exactly these two options:

- **Branch vs master** — review everything that has changed on the current branch since it diverged from `master`, including uncommitted changes.
- **Uncommitted only** — review only staged + unstaged + untracked changes in the working tree.

Do not assume a default. Wait for the answer.

## Step 2 — Collect the change set

Based on the answer, run via `Bash`:

**Branch vs master:**
```
git status
git diff master...HEAD --name-status
git diff master...HEAD
git diff HEAD            # uncommitted on top of branch
```

**Uncommitted only:**
```
git status
git diff HEAD            # staged + unstaged
# plus read each untracked file listed by git status
```

For every changed file in the resulting set:

1. Apply the **path filters** from `.claude/review-rules.md`.
2. Read the file fully with `Read`. Diff context alone is not enough — the path-specific rules need surrounding code (interface signatures, default values, `when` exhaustiveness, `AutoCloseable` release paths, etc.).

## Step 3 — Apply path-specific rules

Match each surviving file against the globs in the "Path-specific rules" section of `.claude/review-rules.md`. A file may match more than one rule — apply all that match.

## Step 4 — Repo-wide concerns

Apply these to every reviewed file regardless of path:

- **Correctness, performance, security** — the tone `.claude/review-rules.md` asks for. Be concise and actionable.
- **Public-API discipline** (`AGENTS.md`) — any `pubnub-chat-api` change, JS `index.d.ts` change or Apple export change is customer-visible. Default-value changes and enum/sealed-class shape changes are behavior breaks; flag them even if compilation succeeds. Check that `api/pubnub-chat.api` changed accordingly.
- **Kotlin ↔ JS parity** — when a `pubnub-chat-api`/`commonMain` behavior changes, check whether the matching `*Js.kt` wrapper, `index.d.ts` (all three copies) and `js-chat/tests/<entity>.test.ts` need a change too. Missing sync is at least Major.
- **KMP target hygiene** — `expect` requires `actual` on every enabled target. Watch for `commonMain` code that depends on JVM-only APIs.
- **Regression tests** — a behavior fix without a test that fails without it is Major.
- **No new comments unless they explain non-obvious "why"** — flag explanatory comments that just restate the code.

## Step 5 — Output the report

Produce a single Markdown report as your final message. **No code edits, no `Write` calls.** Structure:

```
## Summary
<one paragraph: scope (branch-vs-master | uncommitted), file count after filters, headline risks>

## Findings

### Blocker
- `path/to/File.kt:42` — <issue> — rule: <which rule, e.g. "chat-api default-value change">

### Major
- ...

### Minor
- ...

### Nit
- ...

## Suggested verifications
- `scripts/dev/check.sh static` (always, if any Kotlin file changed)
- `scripts/dev/check.sh api` (if pubnub-chat-api or any public signature changed)
- `scripts/dev/check.sh compile` (if jsMain / appleMain / expect-actual touched)
- `scripts/dev/check.sh unit` (if impl touched)
- `scripts/dev/integration-kotlin.sh` (if impl behavior changed; opt-in, needs credentials)
- `scripts/dev/check.sh jslint` (if js-chat/ TS/JS changed)
- `scripts/dev/js-build.sh && scripts/dev/integration-js.sh tests/<entity>.test.ts` (if JS wrappers or index.d.ts changed; opt-in)

## Out of scope / not reviewed
- <files skipped by path filters, with one-line reason each>
```

Severity guide:
- **Blocker** — public-API behavior break without restoration path; thread-safety bug; security issue; broken `expect`/`actual`; `index.d.ts` contradicting the Kotlin wrapper; build/publication misconfiguration.
- **Major** — likely correctness or perf bug, listener/subscription leak, `.d.ts` copies out of sync, missing `api` dump or KDoc on a public change, behavior fix without regression test.
- **Minor** — style, naming, redundant code, suboptimal but correct.
- **Nit** — cosmetic.

If the diff is empty after filtering, say so plainly and stop — do not invent findings.
