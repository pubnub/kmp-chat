//
//  ContentView.swift
//  Sample Chat app
//
//  Created by Wojciech Kaliciński on 11/04/2024.
//

import SwiftUI
import PubNubChat

struct ContentView: View {
    init(){
        var chatConf = PNConfiguration(userId: UserId(value: "aaa"), subscribeKey: "demo", publishKey: "demo")
        var pubnub = PubNub(configuration: chatConf)
        pubnub.publish(channel: "chan", message: "mess", meta: nil, shouldStore: true, usePost: false, replicate: true, ttl: nil).async(callback: { (s1: Any?) -> Void in
            
            if let movie = s1 as? PNPublishResult {
                print(movie.timetoken)
            }
        })
        
//        var config = PNConfiguration(userId: UserId(value: "abcd"))
//        config.subscribeKey = "sub-c-33f55052-190b-11e6-bfbc-02ee2ddab7fe"
//        config.publishKey = "pub-c-739aa0fc-3ed5-472b-af26-aca1b333ec52"
//        let pn = PubNub(configuration: config)
//        pn.publish(channel: "abc", message: "\"Hi from Swift\"", meta: nil, shouldStore: false, usePost: false, replicate: true, ttl: nil).async { (result: PNPublishResult?, status: PNStatus) in
//            print(result)
//        }
        
    }
    var body: some View {
        VStack {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text("Hello, world!")
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
