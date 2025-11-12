package com.example.mastercontrol.model

import org.json.JSONObject

sealed class CommandResult(open val id: String) {
    data class Success(override val id: String, val message: String) : CommandResult(id)
    data class Error(override val id: String, val error: String) : CommandResult(id)

    fun serialize(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("status", if (this is Success) "success" else "error")
        when (this) {
            is Success -> json.put("message", message)
            is Error -> json.put("message", error)
        }
        return json.toString()
    }
}
