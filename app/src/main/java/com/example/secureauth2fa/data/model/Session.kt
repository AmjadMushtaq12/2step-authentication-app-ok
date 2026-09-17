package com.example.secureauth2fa.data.model

data class Session(
    val sessionId: String = "",
    val deviceName: String = "",
    val osVersion: String = "",
    val lastActiveTime: Long = System.currentTimeMillis(),
    val isCurrentDevice: Boolean = false,
    val isRemembered: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "sessionId" to sessionId,
            "deviceName" to deviceName,
            "osVersion" to osVersion,
            "lastActiveTime" to lastActiveTime,
            "isCurrentDevice" to isCurrentDevice,
            "isRemembered" to isRemembered
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): Session {
            return Session(
                sessionId = map["sessionId"] as? String ?: "",
                deviceName = map["deviceName"] as? String ?: "Android Device",
                osVersion = map["osVersion"] as? String ?: "Android",
                lastActiveTime = (map["lastActiveTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isCurrentDevice = map["isCurrentDevice"] as? Boolean ?: false,
                isRemembered = map["isRemembered"] as? Boolean ?: false
            )
        }
    }
}
