package com.example.mastercontrol.model

import org.json.JSONObject

data class CommandEnvelope(
    val id: String,
    val command: Command
) {

    sealed class Command {
        data class Install(
            val id: String,
            val packageName: String,
            val sourcePath: String,
            val displayName: String
        ) : Command()

        data class Uninstall(val id: String, val packageName: String) : Command()
        data class Start(val id: String, val packageName: String) : Command()
        data class Stop(val id: String, val packageName: String) : Command()
        data class Update(val id: String, val packageName: String, val sourcePath: String, val displayName: String, val versionCode: Long) : Command()
        data class HealthCheck(val id: String) : Command()
    }

    companion object {
        fun deserialize(payload: String): CommandEnvelope {
            val json = JSONObject(payload)
            val id = json.getString("id")
            val type = json.getString("type")
            val body = json.optJSONObject("body") ?: JSONObject()
            val command = when (type) {
                "install" -> Command.Install(
                    id = id,
                    packageName = body.getString("package"),
                    sourcePath = body.getString("source"),
                    displayName = body.optString("name", body.getString("package"))
                )
                "uninstall" -> Command.Uninstall(id, body.getString("package"))
                "start" -> Command.Start(id, body.getString("package"))
                "stop" -> Command.Stop(id, body.getString("package"))
                "update" -> Command.Update(
                    id = id,
                    packageName = body.getString("package"),
                    sourcePath = body.getString("source"),
                    displayName = body.optString("name", body.getString("package")),
                    versionCode = body.optLong("version", 0L)
                )
                "health" -> Command.HealthCheck(id)
                else -> throw IllegalArgumentException("Unsupported command type: $type")
            }
            return CommandEnvelope(id, command)
        }
    }
}
