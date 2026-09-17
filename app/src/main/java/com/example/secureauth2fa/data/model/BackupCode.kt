package com.example.secureauth2fa.data.model

import java.security.MessageDigest

data class BackupCode(
    val codeHash: String = "",
    val isUsed: Boolean = false,
    val usedAt: Long? = null,
    val plainText: String? = null // Only populated in-memory when newly generated
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "codeHash" to codeHash,
            "isUsed" to isUsed,
            "usedAt" to usedAt
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): BackupCode {
            return BackupCode(
                codeHash = map["codeHash"] as? String ?: "",
                isUsed = map["isUsed"] as? Boolean ?: false,
                usedAt = (map["usedAt"] as? Number)?.toLong(),
                plainText = null
            )
        }

        fun hash(code: String): String {
            val sanitized = code.replace("-", "").trim().uppercase()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(sanitized.toByteArray(Charsets.UTF_8))
            return digest.fold("") { str, it -> str + "%02x".format(it) }
        }
    }
}
