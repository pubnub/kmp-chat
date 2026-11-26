#!/usr/bin/env bash
# Builds the JS Chat SDK: Kotlin/JS production library, packs it into js-chat/dist, then rollup.
# Usage: scripts/dev/js-build.sh
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

run_logged js-build - bash -c '.github/workflows/release/js-pre-npm-build.sh && npm --prefix=js-chat run build'
