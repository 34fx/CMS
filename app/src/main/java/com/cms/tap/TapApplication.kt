package com.cms.tap

import android.app.Application

class TapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure the automation service is started as soon as the process is created
        TapService.start(this)
    }
}
