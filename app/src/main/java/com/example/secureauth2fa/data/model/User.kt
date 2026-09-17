package com.example.secureauth2fa.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val isTwoFactorEnabled: Boolean = false,
    val isEmailVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val rememberedDeviceToken: String? = null,
    val rememberedDeviceExpiry: Long? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "uid" to uid,
            "email" to email,
            "displayName" to displayName,
            "isTwoFactorEnabled" to isTwoFactorEnabled,
            "isEmailVerified" to isEmailVerified,
            "createdAt" to createdAt,
            "rememberedDeviceToken" to rememberedDeviceToken,
            "rememberedDeviceExpiry" to rememberedDeviceExpiry
        )
    }

    companion object {
        fun fromMap(uid: String, map: Map<String, Any?>): User {
            return User(
                uid = uid,
                email = map["email"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                isTwoFactorEnabled = map["isTwoFactorEnabled"] as? Boolean ?: false,
                isEmailVerified = map["isEmailVerified"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                rememberedDeviceToken = map["rememberedDeviceToken"] as? String,
                rememberedDeviceExpiry = (map["rememberedDeviceExpiry"] as? Number)?.toLong()
            )
        }
    }
}
