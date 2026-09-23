package com.example.domain

import com.example.data.CommunityLocationReference

data class CommunityLocationValidation(
    val accepted: List<CommunityLocationReference>,
    val rejected: List<CommunityLocationReference>,
    val warnings: List<String>
)

object CommunityLocationReferenceValidator {
    fun validate(items: List<CommunityLocationReference>): CommunityLocationValidation {
        val warnings = mutableListOf<String>()
        val rejected = mutableListOf<CommunityLocationReference>()
        val accepted = mutableListOf<CommunityLocationReference>()
        val seenCodes = mutableSetOf<String>()

        items.forEach { item ->
            val invalidCode = item.pcode.isBlank() || item.acode.isBlank() ||
                item.tcode.isBlank() || item.mcode.isBlank()
            val invalidCoordinates = item.latitude != null &&
                item.longitude != null &&
                (item.latitude !in -90.0..90.0 || item.longitude !in -180.0..180.0)
            val negativePopulation = listOf(
                item.femaleCount, item.maleCount, item.populationTotal, item.householdTotal
            ).any { it != null && it < 0 }

            when {
                invalidCode || invalidCoordinates || negativePopulation -> {
                    rejected += item
                }
                !seenCodes.add(item.mcode) -> {
                    warnings += "พบ mcode ซ้ำ: " + item.mcode
                    rejected += item
                }
                item.femaleCount != null && item.maleCount != null &&
                    item.populationTotal != null &&
                    item.femaleCount + item.maleCount != item.populationTotal -> {
                    warnings += "จำนวนประชากรชาย+หญิงไม่เท่ากับยอดรวม: " + item.mcode
                    accepted += item
                }
                else -> accepted += item
            }
        }

        return CommunityLocationValidation(accepted, rejected, warnings)
    }
}
