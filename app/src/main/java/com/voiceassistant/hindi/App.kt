package com.voiceassistant.hindi

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        AssistantLog.init(this)
    }
}
