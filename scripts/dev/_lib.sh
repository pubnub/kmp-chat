#!/usr/bin/env bash
# Shared helpers for scripts/dev/*.sh. Sourced, not executed.
#
# Goal: run Gradle / npm but keep the full output OUT of the caller's context (an AI agent or a human
# terminal). The full output goes to a log file; stdout gets a few lines:
#   PASS <name> (Ns, N tests) log=<path>
#   FAIL <name> (Ns, exit=N) log=<path>   + failed tests / first errors, capped.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="$REPO_ROOT/build/agent-logs"
MAX_FAILED_TESTS="${MAX_FAILED_TESTS:-15}"
MAX_ERROR_LINES="${MAX_ERROR_LINES:-25}"

# Gradle needs Java 21 to drive the build (see AGENTS.md). Pick it up on macOS if JAVA_HOME is unset.
ensure_java() {
  if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
    local jh
    jh="$(/usr/libexec/java_home -v 21 2>/dev/null)" && export JAVA_HOME="$jh"
  fi
}

# Integration tests hit real PubNub; fail fast (without printing any secret) if credentials are missing.
# require_credentials kotlin|js <name>
require_credentials() {
  case "$1" in
    kotlin)
      # pubnub-chat-test/build.gradle.kts reads test.properties, else SDK_* env vars, else silently uses "demo".
      if [ -f "$REPO_ROOT/test.properties" ]; then return 0; fi
      # All five are required: if any one is missing, the Gradle fallback sets every key to "demo".
      if [ -n "${SDK_PUB_KEY:-}" ] && [ -n "${SDK_SUB_KEY:-}" ] && [ -n "${SDK_PAM_PUB_KEY:-}" ] \
        && [ -n "${SDK_PAM_SUB_KEY:-}" ] && [ -n "${SDK_PAM_SEC_KEY:-}" ]; then return 0; fi
      echo "FAIL $2: no credentials. Create test.properties in the repo root (pubKey, subKey, pamPubKey, pamSubKey, pamSecKey) or export SDK_PUB_KEY/SDK_SUB_KEY/SDK_PAM_PUB_KEY/SDK_PAM_SUB_KEY/SDK_PAM_SEC_KEY."
      ;;
    js)
      # js-chat/tests/utils.ts loads js-chat/.env via dotenv; real env vars work too.
      if [ -f "$REPO_ROOT/js-chat/.env" ]; then return 0; fi
      if [ -n "${PUBLISH_KEY:-}" ] && [ -n "${SUBSCRIBE_KEY:-}" ]; then return 0; fi
      echo "FAIL $2: no credentials. Create js-chat/.env (PUBLISH_KEY, SUBSCRIBE_KEY, PAM_*, PAM_WITH_INTEGRITY_*) or export them."
      ;;
  esac
  exit 2
}

# Prints the compact summary for a finished run. Reads JUnit XML files newer than the marker.
# summarize <name> <rc> <secs> <log> <marker> <xml-search-dir> <xml-find-args...>
summarize() {
  local name="$1" rc="$2" secs="$3" log="$4" marker="$5" search_dir="$6"
  shift 6

  local xmls
  xmls="$(find "$search_dir" "$@" -newer "$marker" 2>/dev/null)"
  local total=0 failed=0
  if [ -n "$xmls" ]; then
    total=$(echo "$xmls" | tr '\n' '\0' | xargs -0 grep -h -o '<testcase ' 2>/dev/null | wc -l | tr -d ' ')
    failed=$(echo "$xmls" | tr '\n' '\0' | xargs -0 grep -h -E -o '<(failure|error)[ >]' 2>/dev/null | wc -l | tr -d ' ')
  fi

  if [ "$rc" -eq 0 ] && [ "$failed" -eq 0 ]; then
    echo "PASS $name (${secs}s, ${total} tests) log=$log"
    return 0
  fi

  echo "FAIL $name (${secs}s, exit=$rc, ${total} tests, ${failed} failed) log=$log"

  if [ "$failed" -gt 0 ]; then
    echo "-- failed tests (max $MAX_FAILED_TESTS) --"
    echo "$xmls" | tr '\n' '\0' | xargs -0 awk '
      /<testcase / {
        cls = ""; nm = ""
        if (match($0, /classname="[^"]*"/)) cls = substr($0, RSTART + 11, RLENGTH - 12)
        if (match($0, / name="[^"]*"/))     nm  = substr($0, RSTART + 7,  RLENGTH - 8)
      }
      /<(failure|error)[ >]/ {
        msg = ""
        if (match($0, /message="[^"]*"/)) msg = substr($0, RSTART + 9, RLENGTH - 10)
        print cls "." nm ": " substr(msg, 1, 200)
      }' 2>/dev/null | head -n "$MAX_FAILED_TESTS"
  fi

  # Build/compile/lint errors: Kotlin "e: file.kt:..." lines, detekt/ktlint "file.kt:L:C" lines, TS/ESLint
  # "file.ts:L:C" lines, jest "●" failure headers, and Gradle's "What went wrong" block.
  echo "-- errors (max $MAX_ERROR_LINES lines) --"
  {
    grep -E '^e: |\.(kt|kts|ts|js|mjs):[0-9]+(:[0-9]+)?|^[[:space:]]*●' "$log" | grep -v '^[[:space:]]*at '
    awk '/^\* What went wrong:/ {c=6} c>0 {print; c--}' "$log"
    grep -E '^npm (ERR|error)' "$log"
  } | awk '!seen[$0]++' | head -n "$MAX_ERROR_LINES"
  echo "(full output: $log)"
  return 1
}

# run_gradle <name> <search-dir-relative-to-repo-root> <gradle args...>
run_gradle() {
  local name="$1" search_dir="$2"
  shift 2
  mkdir -p "$LOG_DIR"
  local log="$LOG_DIR/$name.log" marker="$LOG_DIR/.$name.start"
  : > "$log"
  touch "$marker"
  ensure_java

  local start end rc
  start=$(date +%s)
  (cd "$REPO_ROOT" && ./gradlew --console=plain --continue "$@") > "$log" 2>&1
  rc=$?
  end=$(date +%s)

  # JUnit XML written during THIS run, restricted to the modules this script owns.
  summarize "$name" "$rc" "$((end - start))" "$log" "$marker" \
    "$REPO_ROOT/$search_dir" -path '*/build/test-results/*' -name 'TEST-*.xml'
}

# run_logged <name> <junit-xml-path-relative-to-repo-root|-> <command...>
# Runs an arbitrary command from the repo root (npm, shell scripts) with the same compact output.
run_logged() {
  local name="$1" junit="$2"
  shift 2
  mkdir -p "$LOG_DIR"
  local log="$LOG_DIR/$name.log" marker="$LOG_DIR/.$name.start"
  : > "$log"
  touch "$marker"
  ensure_java

  local start end rc
  start=$(date +%s)
  (cd "$REPO_ROOT" && "$@") > "$log" 2>&1
  rc=$?
  end=$(date +%s)

  if [ "$junit" = "-" ]; then
    summarize "$name" "$rc" "$((end - start))" "$log" "$marker" "$LOG_DIR" -name '__no_junit__'
  else
    summarize "$name" "$rc" "$((end - start))" "$log" "$marker" \
      "$REPO_ROOT/$(dirname "$junit")" -maxdepth 1 -name "$(basename "$junit")"
  fi
}
