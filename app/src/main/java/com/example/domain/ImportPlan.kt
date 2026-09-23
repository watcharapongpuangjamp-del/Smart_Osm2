package com.example.domain

import com.example.data.Household
import com.example.data.Person
import com.example.utils.NationalIdStatus

enum class ImportAction {
    INSERT,
    UPDATE,
    SKIP,
    NEEDS_REVIEW
}

data class PlannedPersonImport(
    val rowNum: Int,
    var action: ImportAction,
    val personData: Person,
    val householdData: Household,
    val isNewHousehold: Boolean,
    val nationalIdStatus: NationalIdStatus,
    val isAmbiguousHousehold: Boolean = false,
    val isDuplicateUuid: Boolean = false,
    val reviewReasons: MutableList<String> = mutableListOf()
)

data class ImportPlan(
    val plannedItems: List<PlannedPersonImport>,
    val totalRows: Int,
    val insertCount: Int,
    val updateCount: Int,
    val skipCount: Int,
    val needsReviewCount: Int,
    val errors: List<ImportError>
)

/**
 * External reference data must never become the Local Master automatically.
 * ThaiPHC is represented as reference data first; a user-approved import is
 * required before changing Smart OSM2 Local records.
 */
enum class ReferenceSource {
    THAI_PHC
}

enum class ReferenceMatchStatus {
    MATCH,
    NEW,
    CONFLICT,
    UNKNOWN
}

data class ThaiPhcReference(
    val externalId: String? = null,
    val firstName: String,
    val lastName: String,
    val gender: String? = null,
    val houseNo: String? = null,
    val moo: String? = null,
    val tambon: String? = null,
    val status: String? = null,
    val relationship: String? = null,
    val source: ReferenceSource = ReferenceSource.THAI_PHC,
    val sourceUpdatedAt: Long? = null
)

data class ThaiPhcMatchResult(
    val reference: ThaiPhcReference,
    val status: ReferenceMatchStatus,
    val matchedPersonUuid: String? = null,
    val score: Int = 0,
    val reasons: List<String> = emptyList()
)

data class ThaiPhcImportPreview(
    val source: ReferenceSource = ReferenceSource.THAI_PHC,
    val total: Int,
    val matched: Int,
    val newRecords: Int,
    val conflicts: Int,
    val unknown: Int,
    val items: List<ThaiPhcMatchResult>
)
