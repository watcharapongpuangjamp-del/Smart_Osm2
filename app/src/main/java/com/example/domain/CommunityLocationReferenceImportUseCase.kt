package com.example.domain

import com.example.data.CommunityLocationReference
import com.example.data.CommunityLocationReferenceDao

data class CommunityLocationImportReport(
    val parsed: Int,
    val accepted: Int,
    val rejected: Int,
    val warnings: List<String>,
    val stored: Int,
    val provinces: Int = 0,
    val districts: Int = 0,
    val subdistricts: Int = 0,
    val villages: Int = 0,
    val coordinates: Int = 0
)

class CommunityLocationReferenceImportUseCase(
    private val dao: CommunityLocationReferenceDao
) {
    /**
     * Preview is read-only: it parses and validates the complete dataset but does not touch Room.
     */
    fun previewJson(json: String, sourceVersion: String = ""): CommunityLocationImportReport {
        val parsedItems = CommunityLocationReferenceJsonImporter.parse(json, sourceVersion)
        val validation = CommunityLocationReferenceValidator.validate(parsedItems)
        return validation.toReport(parsedItems.size, stored = 0)
    }

    /**
     * Replace is atomic: Room either replaces the reference dataset completely or leaves
     * the previous dataset untouched when parsing/validation fails before the transaction.
     */
    suspend fun importJson(json: String, sourceVersion: String = ""): CommunityLocationImportReport {
        val parsedItems = CommunityLocationReferenceJsonImporter.parse(json, sourceVersion)
        val validation = CommunityLocationReferenceValidator.validate(parsedItems)

        if (parsedItems.isEmpty() || validation.accepted.isEmpty()) {
            throw IllegalStateException("ไม่พบข้อมูลชุมชนที่ผ่านการตรวจสอบสำหรับนำเข้า")
        }

        dao.replaceAll(validation.accepted)
        return validation.toReport(parsedItems.size, stored = dao.count())
    }

    private fun CommunityLocationValidation.toReport(
        parsed: Int,
        stored: Int
    ): CommunityLocationImportReport {
        val accepted = accepted
        return CommunityLocationImportReport(
            parsed = parsed,
            accepted = accepted.size,
            rejected = rejected.size,
            warnings = warnings,
            stored = stored,
            provinces = accepted.map { it.pcode }.distinct().size,
            districts = accepted.map { it.acode }.distinct().size,
            subdistricts = accepted.map { it.tcode }.distinct().size,
            villages = accepted.map { it.mcode }.distinct().size,
            coordinates = accepted.count { it.latitude != null && it.longitude != null }
        )
    }
}
