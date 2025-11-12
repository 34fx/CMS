package com.example.mastercontrol

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.mastercontrol.orchestration.CommandOrchestrator
import com.example.mastercontrol.server.CommandClient

class MasterApplication : Application() {

    lateinit var orchestrator: CommandOrchestrator
        private set

    override fun onCreate() {
        super.onCreate()
        val commandClient = CommandClient(context = this)
        orchestrator = CommandOrchestrator(
            context = this,
            commandClient = commandClient,
            lifecycle = ProcessLifecycleOwner.get().lifecycle
        )
        orchestrator.initialize()
    }
}
