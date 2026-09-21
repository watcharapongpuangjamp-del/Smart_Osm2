package com.example.data.backup

import android.net.Uri
import com.example.data.Household
import com.example.data.LocalDateAdapter
import com.example.data.Person
import com.example.data.PersonHistory
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = false)
data class BackupHistoryRecord(
    val personUuid: String,
    val action: String,
    val oldValue: String?,
    val newValue: String?,
    val timestamp: Long,
    val operatorId: String,
    val operatorName: String,
    val role: String,
    val deviceId: String,
    val source: String
)

@JsonClass(generateAdapter = false)
data class BackupPayload(
    val households: List<Household>,
    val persons: List<Person>,
    val history: List<BackupHistoryRecord>
)

@JsonClass(generateAdapter = false)
data class BackupEnvelope(
    val format: String,
    val formatVersion: Int,
    val backupId: String,
    val createdAt: Long,
    val applicationId: String,
    val databaseSchemaVersion: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val cipherText: String
)

data class BackupInfo(
    val backupId: String,
    val createdAt: Long,
    val householdCount: Int,
    val personCount: Int,
    val historyCount: Int
)

class SecureBackupManager {
    companion object {
        private const val FORMAT = "SMART_OSM_SECURE_BACKUP"
        private const val FORMAT_VERSION = 1
        private const val APPLICATION_ID = "com.aistudio.populationreg.xqzr"
        private const val DATABASE_SCHEMA_VERSION = 9
        private const val KDF = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val MAGIC = "SMARTOSM-SOSM-1"
        private const val MIN_PASSWORD_LENGTH = 8
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(LocalDateAdapter())
        .build()

    private val payloadAdapter = moshi.adapter(BackupPayload::class.java)
    private val envelopeAdapter = moshi.adapter(BackupEnvelope::class.java)

    fun writeBackup(
        output: OutputStream,
        payload: BackupPayload,
        password: CharArray
    ): BackupInfo {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "รหัสผ่านสำรองต้องมีอย่างน้อย $MIN_PASSWORD_LENGTH ตัวอักษร"
        }

        val backupId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        val plaintext = payloadAdapter.toJson(payload).toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        val envelope = BackupEnvelope(
            format = FORMAT,
            formatVersion = FORMAT_VERSION,
            backupId = backupId,
            createdAt = createdAt,
            applicationId = APPLICATION_ID,
            databaseSchemaVersion = DATABASE_SCHEMA_VERSION,
            kdf = KDF,
            iterations = ITERATIONS,
            salt = b64(salt),
            iv = b64(iv),
            cipherText = b64(ciphertext)
        )

        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(MAGIC)
            writer.newLine()
            writer.write(envelopeAdapter.toJson(envelope))
            writer.newLine()
        }

        return BackupInfo(
            backupId = backupId,
            createdAt = createdAt,
            householdCount = payload.households.size,
            personCount = payload.persons.size,
            historyCount = payload.history.size
        )
    }

    fun readBackup(
        input: InputStream,
        password: CharArray
    ): Pair<BackupInfo, BackupPayload> {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "รหัสผ่านสำรองต้องมีอย่างน้อย $MIN_PASSWORD_LENGTH ตัวอักษร"
        }

        val lines = input.bufferedReader(Charsets.UTF_8).use { reader ->
            val magic = reader.readLine()
            require(magic == MAGIC) { "ไม่ใช่ไฟล์สำรอง Smart OSM ที่รองรับ" }
            val json = reader.readLine() ?: error("ไฟล์สำรองไม่สมบูรณ์")
            json
        }

        val envelope = envelopeAdapter.fromJson(lines)
            ?: error("ไม่สามารถอ่านข้อมูลหัวไฟล์สำรองได้")

        require(envelope.format == FORMAT && envelope.formatVersion == FORMAT_VERSION) {
            "ไฟล์สำรองเป็นคนละรูปแบบหรือไม่รองรับเวอร์ชันนี้"
        }
        require(envelope.applicationId == APPLICATION_ID) {
            "ไฟล์สำรองไม่ได้มาจาก Smart OSM รุ่นนี้"
        }
        require(envelope.kdf == KDF && envelope.iterations == ITERATIONS) {
            "พารามิเตอร์ความปลอดภัยของไฟล์ไม่รองรับ"
        }

        val key = deriveKey(password, b64d(envelope.salt))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, b64d(envelope.iv))
            )
            val plaintext = cipher.doFinal(b64d(envelope.cipherText))
            val payload = payloadAdapter.fromJson(String(plaintext, Charsets.UTF_8))
                ?: error("ข้อมูลสำรองว่างหรือเสียหาย")

            return BackupInfo(
                backupId = envelope.backupId,
                createdAt = envelope.createdAt,
                householdCount = payload.households.size,
                personCount = payload.persons.size,
                historyCount = payload.history.size
            ) to payload
        } catch (e: AEADBadTagException) {
            throw SecurityException("รหัสผ่านไม่ถูกต้อง หรือไฟล์สำรองถูกแก้ไข/เสียหาย", e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun b64d(value: String): ByteArray =
        Base64.getDecoder().decode(value)
}
