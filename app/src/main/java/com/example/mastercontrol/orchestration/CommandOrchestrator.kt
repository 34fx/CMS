package com.example.mastercontrol.orchestration

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.mastercontrol.model.CommandEnvelope
import com.example.mastercontrol.model.CommandResult
import com.example.mastercontrol.server.CommandClient
import com.example.mastercontrol.service.MasterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CommandOrchestrator(
    private val context: Context,
    private val commandClient: CommandClient,
    private val lifecycle: Lifecycle
) : DefaultLifecycleObserver {

    private var pollingJob: Job? = null
    private var lifecycleScope: CoroutineScope? = null

    fun initialize() {
        lifecycle.addObserver(this)
        MasterService.start(context)
    }

    fun bindServiceScope() {
        lifecycleScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollingJob = lifecycleScope?.launch {
            commandClient.commandStream().collectLatest { envelope ->
                val result = processCommand(envelope)
                commandClient.reportResult(result)
            }
        }
    }

    fun unbindServiceScope() {
        pollingJob?.cancel()
        lifecycleScope?.cancel()
        pollingJob = null
        lifecycleScope = null
    }

    override fun onStart(owner: LifecycleOwner) {
        MasterService.start(context)
    }

    override fun onStop(owner: LifecycleOwner) {
        // No-op, service keeps process alive.
    }

    private suspend fun processCommand(envelope: CommandEnvelope): CommandResult {
        return when (val command = envelope.command) {
            is CommandEnvelope.Command.Install -> SlaveAppManager.install(context, command)
            is CommandEnvelope.Command.Uninstall -> SlaveAppManager.uninstall(context, command)
            is CommandEnvelope.Command.Start -> SlaveAppManager.start(context, command)
            is CommandEnvelope.Command.Stop -> SlaveAppManager.stop(context, command)
            is CommandEnvelope.Command.Update -> SlaveAppManager.update(context, command)
            is CommandEnvelope.Command.HealthCheck -> CommandResult.Success(envelope.id, "ok")
        }
    }
}
