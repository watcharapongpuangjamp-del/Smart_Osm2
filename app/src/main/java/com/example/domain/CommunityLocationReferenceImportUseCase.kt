package com.example.domain

import com.example.data.CommunityLocationReference
import com.example.data.CommunityLocationReferenceDao

data class CommunityLocationImportReport(
    val parsed: Int,
    val accepted: Int,
    val rejected: Int,
    val warnings: List<String>,
    val stored: Int
)

class CommunityLocationReferenceImportUseCase(
    private val dao: CommunityLocationReferenceDao
) {
    suspend fun importJson(json: String, sourceVersion: String = ""): CommunityLocationImportReport {
        val parsedItems = CommunityLocationReferenceJsonImporter.parse(json, sourceVersion)
        val validation = CommunityLocationReferenceValidator.validate(parsedItems)

        // Reference data is replaceable as a dataset, but only validated rows are stored.
        // It never modifies Household/Person records.
        dao.deleteAll()
        if (validation.accepted.isNotEmpty()) {
            dao.upsertAll(validation.accepted)
        }

        return CommunityLocationImportReport(
            parsed = parsedItems.size,
            accepted = validation.accepted.size,
            rejected = validation.rejected.size,
            warnings = validation.warnings,
            stored = dao.count()
        )
    }
}
