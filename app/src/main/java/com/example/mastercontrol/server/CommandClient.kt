package com.example.mastercontrol.server

import android.content.Context
import android.util.Log
import com.example.mastercontrol.model.CommandEnvelope
import com.example.mastercontrol.model.CommandResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class CommandClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    fun commandStream(): Flow<CommandEnvelope> = flow {
        while (true) {
            try {
                val envelope = pollServer()
                if (envelope != null) {
                    emit(envelope)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Polling failure", t)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    suspend fun reportResult(result: CommandResult) {
        try {
            val request = Request.Builder()
                .url("http://127.0.0.1:8080/results")
                .post(result.serialize().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Failed to report result: ${response.code}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Reporting failure", t)
        }
    }

    private fun pollServer(): CommandEnvelope? {
        val request = Request.Builder()
            .url("http://127.0.0.1:8080/commands")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Command poll failed: ${response.code}")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return runCatching { CommandEnvelope.deserialize(body) }
                .onFailure { Log.e(TAG, "Invalid command payload", it) }
                .getOrNull()
        }
    }

    companion object {
        private const val TAG = "CommandClient"
        private const val POLL_INTERVAL_MS = 5_000L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
