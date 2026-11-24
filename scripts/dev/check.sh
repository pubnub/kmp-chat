#!/usr/bin/env bash
# Non-integration checks with a compact result. Full output goes to build/agent-logs/<name>.log.
#
# Usage:
#   scripts/dev/check.sh lint                  # ktlintCheck, all modules
#   scripts/dev/check.sh static                # ktlintCheck detekt (as in CI "Static analysis")
#   scripts/dev/check.sh api                   # apiCheck (binary-compatibility validator)
#   scripts/dev/check.sh compile               # JVM main + test, JS main
#   scripts/dev/check.sh static api compile    # lint/static/api/compile/format/apidump combine in one Gradle run
#   scripts/dev/check.sh unit [--tests X]      # :pubnub-chat-impl:jvmTest, unit packages only (no network)
#   scripts/dev/check.sh jslint                # ESLint in js-chat/ (as in CI)
#   scripts/dev/check.sh format                # ktlintFormat (WRITES files)
#   scripts/dev/check.sh apidump               # apiDump (WRITES api/*.api; review the diff)
#
# Integration tests are intentionally NOT here; see integration-kotlin.sh / integration-js.sh.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

usage() { sed -n '2,15p' "$0"; exit 64; }

case "${1:-}" in
  unit)
    shift
    if [ $# -eq 0 ]; then
      # Integration tests live in com.pubnub.integration and hit real PubNub; keep them out.
      set -- --tests 'com.pubnub.kmp.*' --tests 'com.pubnub.internal.*'
    fi
    run_gradle unit pubnub-chat-impl :pubnub-chat-impl:jvmTest "$@"
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
    compile) tasks+=(compileKotlinJvm compileTestKotlinJvm compileKotlinJs) ;;
    format)  tasks+=(ktlintFormat) ;;
    apidump) tasks+=(apiDump) ;;
    *) usage ;;
  esac
  name="${name:+$name+}$word"
done
[ ${#tasks[@]} -eq 0 ] && usage

run_gradle "$name" . "${tasks[@]}"
