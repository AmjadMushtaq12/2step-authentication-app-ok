package com.example.secureauth2fa.data.model

import java.util.UUID

data class AuthenticatorAccount(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String,
    val accountName: String,
    val secretKey: String,
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val createdAt: Long = System.currentTimeMillis()
)
