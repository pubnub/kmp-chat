Pod::Spec.new do |spec|
    spec.name                     = 'pubnub_chat_api'
    spec.version                  = '0.8.0-DEV'
    spec.homepage                 = ''
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = ''
    spec.vendored_frameworks      = 'build/cocoapods/framework/pubnub_chat_api.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '14'
    spec.dependency 'PubNubSwift'
                
    if !Dir.exist?('build/cocoapods/framework/pubnub_chat_api.framework') || Dir.empty?('build/cocoapods/framework/pubnub_chat_api.framework')
        raise "

        Kotlin framework 'pubnub_chat_api' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :pubnub-chat-api:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':pubnub-chat-api',
        'PRODUCT_MODULE_NAME' => 'pubnub_chat_api',
    }
                
    spec.script_phases = [
        {
            :name => 'Build pubnub_chat_api',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
                
end