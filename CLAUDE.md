# CLAUDE.md

@AGENTS.md

## Claude Code notes

- **Where guidance goes:** put guidance that applies to all agents in `AGENTS.md`. Keep only Claude Code-specific notes in this file.
- **Hooks and permissions** (`.claude/settings.json`): a `PreToolUse` hook (`.claude/hooks/block-raw-gradle.sh`) blocks raw `./gradlew` and points to `scripts/dev/`; another (`.claude/hooks/protect-files.sh`) blocks `Edit`/`Write` on generated, release-bot, baseline, guardrail/CI and secret files (`*.api`, `.pubnub.yml`, `js-chat/dist*` except `index.d.ts`, lockfiles, lint baselines, `.claude/hooks/`, `.claude/settings.json`, `.github/workflows/`, `.env`, `test.properties`) and says how each is meant to change; `scripts/dev/check.sh` is pre-approved, the integration and JS build scripts are not. Personal overrides go in `.claude/settings.local.json` (git-ignored).
- **Skills** (`.claude/skills/`): `brief` (problem brief + decision-focused plan), `sdk-review` (read-only code review), `pr-description` (release-bot-safe PR body), `securityReview`, `attackSimulation`. All review skills read `.claude/review-rules.md`.
