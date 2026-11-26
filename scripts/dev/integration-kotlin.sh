#!/usr/bin/env bash
# Kotlin integration tests on the JVM target (real PubNub; needs credentials). Opt-in: run explicitly.
# Usage:
#   scripts/dev/integration-kotlin.sh                       # pubnub-chat-impl com.pubnub.integration.* + root module :jvmTest
#   scripts/dev/integration-kotlin.sh --tests X             # pubnub-chat-impl only, e.g. "com.pubnub.integration.ChannelIntegrationTest"
#   scripts/dev/integration-kotlin.sh --root [--tests X]    # root module only (MessageDraftIntegrationTest, ChatIntegrationTest)
# --tests filters a single task: Gradle fails a filtered test task that matches nothing, so one filter can't
# be shared by both modules.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/_lib.sh"

require_credentials kotlin integration-kotlin
if [ "${1:-}" = "--root" ]; then
  shift
  run_gradle integration-kotlin . :jvmTest "$@"
elif [ $# -eq 0 ]; then
  # The root module's jvmTest holds only integration tests, so it needs no filter.
  run_gradle integration-kotlin . :pubnub-chat-impl:jvmTest --tests 'com.pubnub.integration.*' :jvmTest
else
  run_gradle integration-kotlin pubnub-chat-impl :pubnub-chat-impl:jvmTest "$@"
fi
