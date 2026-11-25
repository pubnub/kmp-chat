#!/usr/bin/env bash
# Non-integration checks with a compact result. Full output goes to build/agent-logs/<name>.log.
#
# Usage:
#   scripts/dev/check.sh lint                  # ktlintCheck, all modules
#   scripts/dev/check.sh static                # ktlintCheck detekt (as in CI "Static analysis")
#   scripts/dev/check.sh api                   # apiCheck (binary-compatibility validator)
#   scripts/dev/check.sh compile               # JVM + JS, main + test (compiles tests, does not run them)
#   scripts/dev/check.sh static api compile    # lint/static/api/compile/format/apidump combine in one Gradle run
#   scripts/dev/check.sh unit [--tests X]      # :pubnub-chat-impl:jvmTest, unit packages only (no network);
#                                              #   X must start with com.pubnub.kmp. or com.pubnub.internal.
#   scripts/dev/check.sh jslint                # ESLint in js-chat/ (as in CI)
#   scripts/dev/check.sh format                # ktlintFormat (WRITES files)
#   scripts/dev/check.sh apidump               # apiDump (WRITES api/*.api; review the diff)
#
# Integration tests are intentionally NOT here; see integration-kotlin.sh / integration-js.sh.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() { sed -n '2,16p' "$0"; exit 64; }

case "${1:-}" in
  unit)
    shift
    # This script is pre-approved, so anything passed through would run unprompted: only --tests patterns
    # inside the unit packages are accepted. A prefix check is required because Gradle also matches bare
    # class names in any package (e.g. --tests '*Channel*' would pick up com.pubnub.integration).
    filters=()
    while [ $# -gt 0 ]; do
      case "$1" in
        --tests) [ $# -ge 2 ] || usage; pattern="$2"; shift 2 ;;
        --tests=*) pattern="${1#--tests=}"; shift ;;
        *) echo "check.sh unit: unsupported argument '$1' (only --tests <pattern> is accepted)" >&2; usage ;;
      esac
      case "$pattern" in
        com.pubnub.kmp.* | com.pubnub.internal.*) filters+=(--tests "$pattern") ;;
        *) echo "check.sh unit: '$pattern' is outside com.pubnub.kmp.* / com.pubnub.internal.* (integration tests: scripts/dev/integration-kotlin.sh)" >&2; usage ;;
      esac
    done
    [ ${#filters[@]} -eq 0 ] && filters=(--tests 'com.pubnub.kmp.*' --tests 'com.pubnub.internal.*')
    run_gradle unit pubnub-chat-impl :pubnub-chat-impl:jvmTest "${filters[@]}"
    exit $?
    ;;
  jslint)
    [ $# -eq 1 ] || usage
    run_logged jslint - npm --prefix js-chat run lint
    exit $?
    ;;
esac

tasks=()
name=""
for word in "$@"; do
  case "$word" in
    lint)    tasks+=(ktlintCheck) ;;
    static)  tasks+=(ktlintCheck detekt) ;;  # same set as CI run-validations.yml
    api)     tasks+=(apiCheck) ;;
    compile) tasks+=(compileKotlinJvm compileTestKotlinJvm compileKotlinJs compileTestKotlinJs) ;;
    format)  tasks+=(ktlintFormat) ;;
    apidump) tasks+=(apiDump) ;;
    *) usage ;;
  esac
  name="${name:+$name+}$word"
done
[ ${#tasks[@]} -eq 0 ] && usage

run_gradle "$name" . "${tasks[@]}"
