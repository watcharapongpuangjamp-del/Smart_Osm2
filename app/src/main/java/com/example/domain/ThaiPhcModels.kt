package com.example.domain

/**
 * Normalized reference row supplied by ThaiPHC for review before Local DB changes.
 *
 * Nullable location fields are allowed because the external source may not provide
 * all address components.
 */
data class ThaiPhcReference(
    val firstName: String,
    val lastName: String,
    val houseNo: String? = null,
    val moo: String? = null,
    val tambon: String? = null
)

enum class ReferenceMatchStatus {
    MATCH,
    NEW,
    CONFLICT,
    UNKNOWN
}

data class ThaiPhcMatchResult(
    val reference: ThaiPhcReference,
    val status: ReferenceMatchStatus,
    val matchedPersonUuid: String? = null,
    val score: Int = 0,
    val reasons: List<String> = emptyList()
)

data class ThaiPhcImportPreview(
    val items: List<ThaiPhcMatchResult>
) {
    val total: Int
        get() = items.size

    val matched: Int
        get() = items.count { it.status == ReferenceMatchStatus.MATCH }

    val newRecords: Int
        get() = items.count { it.status == ReferenceMatchStatus.NEW }

    val conflicts: Int
        get() = items.count { it.status == ReferenceMatchStatus.CONFLICT }

    val unknown: Int
        get() = items.count { it.status == ReferenceMatchStatus.UNKNOWN }
}
