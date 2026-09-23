package com.example.domain

import com.example.data.Household
import com.example.data.Person
import java.text.Normalizer
import java.util.Locale

object ThaiPhcNormalizer {
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("\\s+"), "")
            .replace("บ้านเลขที่", "")
            .replace("หมู่ที่", "")
            .replace("หมู่", "")
            .lowercase(Locale.ROOT)
    }

    fun normalizeName(value: String?): String {
        var result = normalize(value)
        listOf("นาย", "นางสาว", "นาง", "เด็กชาย", "เด็กหญิง").forEach { title ->
            if (result.startsWith(title)) result = result.removePrefix(title)
        }
        return result
    }

    fun normalizeHouseNo(value: String?): String =
        normalize(value).replace("/", "")

    fun normalizeMoo(value: String?): String =
        normalize(value).removePrefix("ที่")

    fun normalizeTambon(value: String?): String =
        normalize(value).removePrefix("ตำบล").removePrefix("ต.")
}

object ThaiPhcMatcher {
    private const val NAME_SCORE = 50
    private const val HOUSE_SCORE = 20
    private const val MOO_SCORE = 15
    private const val TAMBON_SCORE = 10

    fun match(
        reference: ThaiPhcReference,
        persons: List<Person>,
        households: List<Household>
    ): ThaiPhcMatchResult {
        if (reference.firstName.isBlank() || reference.lastName.isBlank()) {
            return ThaiPhcMatchResult(
                reference = reference,
                status = ReferenceMatchStatus.UNKNOWN,
                reasons = listOf("ชื่อหรือนามสกุลว่าง")
            )
        }

        val householdById = households.associateBy { it.id }
        val ranked = persons.mapNotNull { person ->
            val household = householdById[person.householdId] ?: return@mapNotNull null
            score(reference, person, household)
        }.sortedByDescending { it.first }

        val best = ranked.firstOrNull()
            ?: return ThaiPhcMatchResult(
                reference = reference,
                status = ReferenceMatchStatus.NEW,
                reasons = listOf("ไม่พบข้อมูลบุคคลใน Local")
            )

        val second = ranked.getOrNull(1)
        val (score, person, reasons) = best

        if (second != null && second.first == score) {
            return ThaiPhcMatchResult(
                reference = reference,
                status = ReferenceMatchStatus.UNKNOWN,
                score = score,
                reasons = reasons + "พบผู้สมัครที่คะแนนเท่ากันหลายราย"
            )
        }

        return when {
            score >= 80 && reasons.none { it.startsWith("ขัดแย้ง") } ->
                ThaiPhcMatchResult(
                    reference = reference,
                    status = ReferenceMatchStatus.MATCH,
                    matchedPersonUuid = person.personUuid,
                    score = score,
                    reasons = reasons
                )

            score >= 50 ->
                ThaiPhcMatchResult(
                    reference = reference,
                    status = ReferenceMatchStatus.CONFLICT,
                    matchedPersonUuid = person.personUuid,
                    score = score,
                    reasons = reasons
                )

            else ->
                ThaiPhcMatchResult(
                    reference = reference,
                    status = ReferenceMatchStatus.NEW,
                    score = score,
                    reasons = listOf("คะแนนจับคู่ต่ำเกินไป") + reasons
                )
        }
    }

    private fun score(
        reference: ThaiPhcReference,
        person: Person,
        household: Household
    ): Triple<Int, Person, List<String>> {
        val reasons = mutableListOf<String>()
        var score = 0

        val refFirst = ThaiPhcNormalizer.normalizeName(reference.firstName)
        val refLast = ThaiPhcNormalizer.normalizeName(reference.lastName)
        val localParts = person.fullName.trim().split(Regex("\\s+"), limit = 2)
        val localFirst = ThaiPhcNormalizer.normalizeName(localParts.firstOrNull())
        val localLast = ThaiPhcNormalizer.normalizeName(localParts.getOrNull(1))

        val nameMatches = refFirst.isNotEmpty() && refFirst == localFirst &&
            refLast.isNotEmpty() && refLast == localLast

        if (refFirst.isNotEmpty() && refFirst == localFirst) score += NAME_SCORE / 2
        if (refLast.isNotEmpty() && refLast == localLast) score += NAME_SCORE / 2

        if (nameMatches) {
            reasons += "ชื่อ-นามสกุลตรงกัน"
        } else {
            reasons += "ขัดแย้ง: ชื่อ-นามสกุลไม่ตรงกัน"
        }

        val refHouse = ThaiPhcNormalizer.normalizeHouseNo(reference.houseNo)
        val localHouse = ThaiPhcNormalizer.normalizeHouseNo(household.houseNo)
        if (refHouse.isNotEmpty() && refHouse == localHouse) {
            score += HOUSE_SCORE
            reasons += "บ้านเลขที่ตรงกัน"
        } else if (refHouse.isNotEmpty()) {
            reasons += "ขัดแย้ง: บ้านเลขที่ไม่ตรงกัน"
        }

        val refMoo = ThaiPhcNormalizer.normalizeMoo(reference.moo)
        val localMoo = ThaiPhcNormalizer.normalizeMoo(household.villageNo)
        if (refMoo.isNotEmpty() && refMoo == localMoo) {
            score += MOO_SCORE
            reasons += "หมู่ตรงกัน"
        }

        val refTambon = ThaiPhcNormalizer.normalizeTambon(reference.tambon)
        val localTambon = ThaiPhcNormalizer.normalizeTambon(household.subdistrict)
        if (refTambon.isNotEmpty() && refTambon == localTambon) {
            score += TAMBON_SCORE
            reasons += "ตำบลตรงกัน"
        }

        return Triple(score, person, reasons)
    }
}
