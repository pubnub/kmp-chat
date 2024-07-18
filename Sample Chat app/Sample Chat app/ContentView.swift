//
//  ContentView.swift
//  Sample Chat app
//
//  Created by Wojciech Kaliciński on 11/04/2024.
//

import SwiftUI
import PubNub
import pubnub_chat

struct ContentView: View {
    init(){
//        var pn = PubNub(configuration: <#T##PubNubConfiguration#>)
//        var pubnub = PubNubImpl.init(pubNubObjC: PubNubObjC(pubnub: pn))
//        var chatConf = ChatCompanion().doInit(chatConfiguration: <#T##ChatConfiguration#>, pubnub: <#T##PubNub#>)

        
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
