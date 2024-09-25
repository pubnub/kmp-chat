# PubNub Kotlin Multiplatform Chat SDK

<p align="center">
  <img src="https://raw.githubusercontent.com/pubnub/rust/master/logo.svg" alt="PubNub" width="300"/>
</p>


[![Maven Central](https://img.shields.io/maven-central/v/com.pubnub/pubnub-chat.svg)](https://maven-badges.herokuapp.com/maven-central/com.pubnub/pubnub-kotlin)

## Features
PubNub takes care of the infrastructure and APIs needed for the realtime communication layer of your application. Work on your app's logic and let PubNub handle sending and receiving data across the world in less than 100ms.

This SDK offers a set of handy methods to create your own feature-rich chat or add a chat to your existing application.

It exposes various PubNub APIs to Kotlin with twists:

* Tailored specifically to the chat use case by offering easy-to-use methods that let you do exactly what you want, like startTyping() (a message) or join() (a channel).
* Meant to be easy & intuitive to use as it focuses on features you would most likely build in your chat app, not PubNub APIs and all the technicalities behind them.
* Offers new chat options, like quotes, threads, or read receipts, that let you build a full-fledged app quickly.

## Get keys

You will need the publish and subscribe keys to authenticate your app. Get your keys from the [Admin Portal](https://admin.pubnub.com/#/login).

## Configure PubNub

1. Integrate the Kotlin SDK into your project:

    * for Maven, add the following dependency in your `pom.xml`:
      ```xml
      <dependency>
         <groupId>com.pubnub</groupId>
         <artifactId>pubnub-chat</artifactId>
         <version>0.8.1</version>
      </dependency>
      ```

    * for Gradle, add the following dependency in your `gradle.build`:
      ```groovy
      implementation 'com.pubnub:pubnub-chat:0.8.1'
      ```

2. Configure your keys and create Chat instance:

    ```kotlin
        val chatConfig = ChatConfiguration(logLevel = LogLevel.DEBUG)
        val pnConfiguration =
            PNConfiguration.builder(userId = UserId("myUserId"), subscribeKey = "mySubscribeKey").build()
        var chat: Chat? = null

        Chat.init(chatConfig, pnConfiguration).async { result: Result<Chat> ->
            result.onSuccess { initializedChat: Chat ->
                println("Chat successfully initialized having logLevel: ${chatConfig.logLevel}")
                chat = initializedChat
            }.onFailure { exception: PubNubException ->
                println("Exception initialising chat: ${exception.message}")
            }
        }
    ```


## Documentation

You'll find all the information about working with Kotlin Chat SDK in the official [PubNub Kotlin Chat SDK documentation](https://www.pubnub.com/docs/chat/kotlin-chat-sdk/overview).

You'll find all the information about working with Swift Chat SDK in the official [PubNub Swift Chat SDK documentation](https://www.pubnub.com/docs/chat/swift-chat-sdk/overview).

## Support

If you **need help** or have a **general question**, contact [support@pubnub.com](mailto:support@pubnub.com).

## License

This project is licensed under a [custom MIT license](https://github.com/pubnub/kmp-chat/blob/master/LICENSE). For more details about the license, refer to the [License FAQ](https://www.pubnub.com/docs/sdks/license-faq).
