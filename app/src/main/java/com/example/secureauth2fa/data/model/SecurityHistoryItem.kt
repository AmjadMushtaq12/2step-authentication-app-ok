package com.example.secureauth2fa.data.model

import java.util.UUID

data class SecurityHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String, // "CODE_COPIED", "ACCOUNT_ADDED", "ACCOUNT_DELETED", "LOGIN", "SECURITY_CHANGE"
    val title: String,
    val description: String
)
