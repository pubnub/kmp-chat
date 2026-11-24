#!/usr/bin/env bash
# Kotlin integration tests on the JVM target (real PubNub; needs credentials). Opt-in: run explicitly.
# Usage: scripts/dev/integration-kotlin.sh [--tests "com.pubnub.integration.ChannelIntegrationTest"]
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

require_credentials kotlin integration-kotlin
[ $# -eq 0 ] && set -- --tests 'com.pubnub.integration.*'
run_gradle integration-kotlin pubnub-chat-impl :pubnub-chat-impl:jvmTest "$@"
