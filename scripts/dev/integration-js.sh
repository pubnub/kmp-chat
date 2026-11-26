#!/usr/bin/env bash
# JS Chat SDK jest tests (real PubNub; needs credentials). Opt-in: run explicitly.
# Requires a JS build first (scripts/dev/js-build.sh) after any Kotlin or index.d.ts change.
# Usage: scripts/dev/integration-js.sh [tests/channel.test.ts]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

require_credentials js integration-js
if [ ! -d "$REPO_ROOT/js-chat/dist" ]; then
  echo "FAIL integration-js: js-chat/dist is missing. Run scripts/dev/js-build.sh first."
  exit 2
fi

if [ $# -eq 0 ]; then
  run_logged integration-js js-chat/junit.xml npm --prefix js-chat run test
else
  run_logged integration-js js-chat/junit.xml npm --prefix js-chat run test -- "$@"
fi
