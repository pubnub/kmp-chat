#!/usr/bin/env bash
# PreToolUse hook (matcher: Edit|Write|MultiEdit|NotebookEdit). Blocks agent edits to protected paths and
# says how the file is meant to change. Exit 2 = block the tool call; stderr is shown to the agent.
# Known gap: edits made through Bash (sed -i, redirects, cp) bypass this hook.

if ! command -v jq >/dev/null 2>&1; then
  echo "protect-files.sh: jq not found, protected-path check skipped" >&2
  exit 0
fi
file="$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' 2>/dev/null)"
[ -z "$file" ] && exit 0

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
rel="${file#"$root"/}"
name="${rel##*/}"
why=""

case "$rel" in
  # AGENTS.md: edit src/jsMain/resources/index.d.ts, then sync these two copies from it.
  js-chat/dist/index.d.ts | js-chat/dist-test/index.d.ts) ;;
  js-chat/dist/* | js-chat/dist-test/*)
    why="generated JS build output; change the Kotlin sources and run scripts/dev/js-build.sh" ;;
  *.api)
    why="binary-compatibility dump; regenerate it with scripts/dev/check.sh apidump" ;;
  .pubnub.yml | js-chat/.pubnub.yml)
    why="written by the release bot from the PR description" ;;
  package-lock.json | */package-lock.json)
    why="change it through npm install, not by hand" ;;
  gradlew | gradlew.bat | gradle/wrapper/*)
    why="Gradle wrapper; update it with the Gradle wrapper task" ;;
  config/detekt/baseline-*.xml | config/ktlint/baseline.xml | */config/ktlint/baseline.xml | js-chat/eslint-suppressions.json)
    why="lint baseline; fix the code instead of suppressing the finding" ;;
  .claude/hooks/* | .claude/settings.json | .github/workflows/*)
    why="guardrail/CI configuration" ;;
  test.properties | local.properties)
    why="may contain secrets" ;;
esac
case "$name" in
  .env | .env.* | *.env | *.pem | *.key | *.p12 | *.jks | *.keystore) why="may contain secrets" ;;
esac

if [ -n "$why" ]; then
  echo "Blocked: $rel is protected ($why). Ask a human to change it." >&2
  exit 2
fi
exit 0
