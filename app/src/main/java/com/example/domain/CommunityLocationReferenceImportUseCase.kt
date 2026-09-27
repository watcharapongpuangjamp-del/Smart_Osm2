package com.example.domain

import com.example.data.CommunityLocationReferenceDao
import java.security.MessageDigest

data class CommunityLocationImportReport(
    val parsed: Int,
    val accepted: Int,
    val rejected: Int,
    val warnings: List<String>,
    val stored: Int,
    val sourceVersion: String = "",
    val sourceHash: String = "",
    val provinces: Int = 0,
    val districts: Int = 0,
    val subdistricts: Int = 0,
    val villages: Int = 0,
    val coordinates: Int = 0
)

class CommunityLocationReferenceImportUseCase(
    private val dao: CommunityLocationReferenceDao
) {
    fun previewJson(json: String, sourceVersion: String = ""): CommunityLocationImportReport {
        val sourceHash = sha256(json)
        val parsed = CommunityLocationReferenceJsonImporter.parseDetailed(json, sourceVersion, sourceHash)
        val validation = CommunityLocationReferenceValidator.validate(parsed.items)
        return validation.toReport(
            parsedCount = parsed.parsedCount,
            parserRejected = parsed.rejectedCount,
            parserWarnings = parsed.warnings,
            stored = 0,
            sourceVersion = sourceVersion,
            sourceHash = sourceHash
        )
    }

    suspend fun importJson(json: String, sourceVersion: String = ""): CommunityLocationImportReport {
        val sourceHash = sha256(json)
        val parsed = CommunityLocationReferenceJsonImporter.parseDetailed(json, sourceVersion, sourceHash)
        val validation = CommunityLocationReferenceValidator.validate(parsed.items)

        if (parsed.parsedCount == 0 || validation.accepted.isEmpty()) {
            throw IllegalStateException("ไม่พบข้อมูลชุมชนที่ผ่านการตรวจสอบสำหรับนำเข้า")
        }

        dao.replaceAll(validation.accepted)
        return validation.toReport(
            parsedCount = parsed.parsedCount,
            parserRejected = parsed.rejectedCount,
            parserWarnings = parsed.warnings,
            stored = dao.count(),
            sourceVersion = sourceVersion,
            sourceHash = sourceHash
        )
    }

    private fun CommunityLocationValidation.toReport(
        parsedCount: Int,
        parserRejected: Int,
        parserWarnings: List<String>,
        stored: Int,
        sourceVersion: String,
        sourceHash: String
    ): CommunityLocationImportReport {
        val accepted = accepted
        return CommunityLocationImportReport(
            parsed = parsedCount,
            accepted = accepted.size,
            rejected = parserRejected + rejected.size,
            warnings = parserWarnings + warnings,
            stored = stored,
            sourceVersion = sourceVersion,
            sourceHash = sourceHash,
            provinces = accepted.map { it.pcode }.distinct().size,
            districts = accepted.map { it.acode }.distinct().size,
            subdistricts = accepted.map { it.tcode }.distinct().size,
            villages = accepted.map { it.mcode }.distinct().size,
            coordinates = accepted.count { it.latitude != null && it.longitude != null }
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
