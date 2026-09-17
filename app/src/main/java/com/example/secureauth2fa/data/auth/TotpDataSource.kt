package com.example.secureauth2fa.data.auth

import android.graphics.Bitmap
import android.graphics.Color
import com.example.secureauth2fa.data.model.BackupCode
import com.example.secureauth2fa.utils.Constants
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.apache.commons.codec.binary.Base32
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

data class TotpSetupResult(
    val secretKey: String,
    val qrCodeBitmap: Bitmap,
    val otpAuthUri: String,
    val backupCodes: List<BackupCode>
)

class TotpDataSource {

    private val random = SecureRandom()
    private val base32 = Base32()

    /**
     * Generates a new 160-bit (20-byte) Base32 secret key compatible with Google Authenticator.
     */
    fun generateSecretKey(): String {
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        return base32.encodeToString(bytes).replace("=", "").trim()
    }

    /**
     * Creates an RFC 6238 standard otpauth URI.
     */
    fun getOtpAuthUri(accountEmail: String, secretKey: String): String {
        val encodedIssuer = URLEncoder.encode(Constants.ISSUER, "UTF-8").replace("+", "%20")
        val encodedEmail = URLEncoder.encode(accountEmail, "UTF-8").replace("+", "%20")
        return "otpauth://totp/$encodedIssuer:$encodedEmail?secret=$secretKey&issuer=$encodedIssuer&algorithm=SHA1&digits=${Constants.TOTP_DIGITS}&period=${Constants.TOTP_PERIOD_SECONDS}"
    }

    /**
     * Generates a QR Code Bitmap with ZXing.
     */
    fun generateQrCodeBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Prepares full 2FA setup details: Secret Key, QR Code Bitmap, and 10 recovery codes.
     */
    fun prepareSetup(accountEmail: String): TotpSetupResult {
        val secretKey = generateSecretKey()
        val otpAuthUri = getOtpAuthUri(accountEmail, secretKey)
        val qrBitmap = generateQrCodeBitmap(otpAuthUri)
        val backupCodes = generateBackupCodes()

        return TotpSetupResult(
            secretKey = secretKey,
            qrCodeBitmap = qrBitmap,
            otpAuthUri = otpAuthUri,
            backupCodes = backupCodes
        )
    }

    /**
     * Verifies 6-digit TOTP code against secret key within ±1 window (30-second interval).
     */
    fun verifyTotpCode(secretKey: String, enteredCode: String): Boolean {
        if (enteredCode.length != Constants.TOTP_DIGITS || !enteredCode.all { it.isDigit() }) {
            return false
        }

        val currentTimeSeconds = System.currentTimeMillis() / 1000L
        val currentStep = currentTimeSeconds / Constants.TOTP_PERIOD_SECONDS

        // Check ±1 window tolerance (±30s)
        for (offset in -Constants.TOTP_TOLERANCE_WINDOWS..Constants.TOTP_TOLERANCE_WINDOWS) {
            val stepToCheck = currentStep + offset
            val expectedCode = generateCodeForStep(secretKey, stepToCheck)
            if (expectedCode == enteredCode) {
                return true
            }
        }
        return false
    }

    /**
     * Generates standard 6-digit TOTP code for a given time step.
     */
    fun generateCodeForStep(secretKey: String, timeStep: Long): String {
        val keyBytes = base32.decode(secretKey.uppercase())
        val data = ByteBuffer.allocate(8).putLong(timeStep).array()

        val mac = Mac.getInstance("HmacSHA1")
        val signKey = SecretKeySpec(keyBytes, "HmacSHA1")
        mac.init(signKey)
        val hash = mac.doFinal(data)

        // RFC 4226 Dynamic Truncation
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val modulo = (10.0).pow(Constants.TOTP_DIGITS.toDouble()).toInt()
        val otp = binary % modulo
        return String.format("%0${Constants.TOTP_DIGITS}d", otp)
    }

    /**
     * Generates the current valid 6-digit TOTP code for the given secret key.
     */
    fun getCurrentTotpCode(secretKey: String): String {
        val currentTimeSeconds = System.currentTimeMillis() / 1000L
        val currentStep = currentTimeSeconds / Constants.TOTP_PERIOD_SECONDS
        return generateCodeForStep(secretKey, currentStep)
    }

    /**
     * Generates 10 one-time recovery backup codes (e.g. "8429-1940").
     */
    fun generateBackupCodes(count: Int = Constants.BACKUP_CODES_COUNT): List<BackupCode> {
        val codes = mutableListOf<BackupCode>()
        val charPool = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ" // unambiguous chars

        for (i in 0 until count) {
            val part1 = (1..4).map { charPool[random.nextInt(charPool.length)] }.joinToString("")
            val part2 = (1..4).map { charPool[random.nextInt(charPool.length)] }.joinToString("")
            val plainCode = "$part1-$part2"
            val hash = BackupCode.hash(plainCode)

            codes.add(
                BackupCode(
                    codeHash = hash,
                    isUsed = false,
                    usedAt = null,
                    plainText = plainCode
                )
            )
        }
        return codes
    }
}
