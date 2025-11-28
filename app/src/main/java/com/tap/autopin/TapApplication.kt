package com.tap.autopin

import android.app.Application
import com.tap.autopin.service.TapService

class TapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure the automation service is started as soon as the process is created
        TapService.start(this)
    }
}
