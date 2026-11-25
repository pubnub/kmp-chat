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

# file_value <file> <key>: the value of "key=value" / "key: value" / "export key=value" in a .properties or
# .env file, unquoted and trimmed; empty if the file or key is absent.
file_value() {
  [ -f "$1" ] || return 0
  # LC_ALL=C: macOS sed aborts with "illegal byte sequence" on non-UTF-8 bytes in a UTF-8 locale.
  LC_ALL=C sed -nE "s/^[[:space:]]*(export[[:space:]]+)?$2[[:space:]]*[=:][[:space:]]*//p" "$1" | tail -n 1 \
    | LC_ALL=C sed -E "s/[[:space:]]+\$//; s/^['\"]//; s/['\"]\$//"
}

# A usable key is non-empty and not the "demo" placeholder.
is_real_key() { [ -n "$1" ] && [ "$1" != demo ]; }

# Integration tests hit real PubNub; fail fast if credentials are missing. Prints key NAMES only, never values.
# require_credentials kotlin|js <name>
require_credentials() {
  local missing="" src k v
  case "$1" in
    kotlin)
      # pubnub-chat-test/build.gradle.kts uses test.properties as-is when it exists (no per-key fallback);
      # otherwise it needs all five SDK_* env vars, and if any one is missing it sets EVERY key to "demo".
      if [ -f "$REPO_ROOT/test.properties" ]; then
        src="test.properties"
        for k in pubKey subKey pamPubKey pamSubKey pamSecKey; do
          is_real_key "$(file_value "$REPO_ROOT/test.properties" "$k")" || missing="$missing $k"
        done
      else
        src="environment (no test.properties)"
        for k in SDK_PUB_KEY SDK_SUB_KEY SDK_PAM_PUB_KEY SDK_PAM_SUB_KEY SDK_PAM_SEC_KEY; do
          is_real_key "${!k:-}" || missing="$missing $k"
        done
      fi
      [ -z "$missing" ] && return 0
      echo "FAIL $2: missing, empty or demo in $src:$missing. Set all five in test.properties (pubKey, subKey, pamPubKey, pamSubKey, pamSecKey) or export SDK_PUB_KEY/SDK_SUB_KEY/SDK_PAM_PUB_KEY/SDK_PAM_SUB_KEY/SDK_PAM_SEC_KEY."
      ;;
    js)
      # js-chat/tests/utils.ts reads these via dotenv, where a variable already in the environment wins over
      # js-chat/.env. All eight are required: chat, access-manager, user and channel tests use the PAM keysets.
      src="environment or js-chat/.env"
      for k in PUBLISH_KEY SUBSCRIBE_KEY PAM_PUBLISH_KEY PAM_SUBSCRIBE_KEY PAM_SECRET_KEY \
        PAM_WITH_INTEGRITY_PUBLISH_KEY PAM_WITH_INTEGRITY_SUBSCRIBE_KEY PAM_WITH_INTEGRITY_SECRET_KEY; do
        if [ -n "${!k+set}" ]; then v="${!k}"; else v="$(file_value "$REPO_ROOT/js-chat/.env" "$k")"; fi
        is_real_key "$v" || missing="$missing $k"
      done
      [ -z "$missing" ] && return 0
      echo "FAIL $2: missing, empty or demo in $src:$missing. Set all eight in js-chat/.env or export them."
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
