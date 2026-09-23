package com.example.domain

import com.example.data.Household
import com.example.data.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThaiPhcMatcherTest {
    private fun household(
        id: Long = 1,
        houseNo: String = "25",
        villageNo: String = "8",
        subdistrict: String = "ป่าขะ"
    ) = Household(
        id = id,
        householdUuid = "house-$id",
        houseNo = houseNo,
        villageNo = villageNo,
        subdistrict = subdistrict
    )

    private fun person(
        id: Long = 1,
        householdId: Long = 1,
        fullName: String = "วัชรพงษ์ พวงแจ่ม"
    ) = Person(
        id = id,
        personUuid = "person-$id",
        householdId = householdId,
        fullName = fullName
    )

    @Test
    fun exactNameAndLocation_returnsMatch() {
        val result = ThaiPhcMatcher.match(
            ThaiPhcReference(
                firstName = "วัชรพงษ์",
                lastName = "พวงแจ่ม",
                houseNo = "บ้านเลขที่ 25",
                moo = "หมู่ที่ 8",
                tambon = "ตำบลป่าขะ"
            ),
            listOf(person()),
            listOf(household())
        )

        assertEquals(ReferenceMatchStatus.MATCH, result.status)
        assertEquals("person-1", result.matchedPersonUuid)
        assertTrue(result.score >= 80)
    }

    @Test
    fun sameNameButDifferentHouse_isConflict() {
        val result = ThaiPhcMatcher.match(
            ThaiPhcReference(
                firstName = "วัชรพงษ์",
                lastName = "พวงแจ่ม",
                houseNo = "99",
                moo = "8",
                tambon = "ป่าขะ"
            ),
            listOf(person()),
            listOf(household())
        )

        assertEquals(ReferenceMatchStatus.CONFLICT, result.status)
        assertEquals("person-1", result.matchedPersonUuid)
        assertTrue(result.reasons.any { it.contains("บ้านเลขที่") })
    }

    @Test
    fun noCandidate_returnsNew() {
        val result = ThaiPhcMatcher.match(
            ThaiPhcReference(
                firstName = "สมชาย",
                lastName = "ไม่มีข้อมูล",
                houseNo = "99",
                moo = "8",
                tambon = "ป่าขะ"
            ),
            listOf(person()),
            listOf(household())
        )

        assertEquals(ReferenceMatchStatus.NEW, result.status)
        assertEquals(null, result.matchedPersonUuid)
    }

    @Test
    fun equalTopScores_returnsUnknown() {
        val result = ThaiPhcMatcher.match(
            ThaiPhcReference(
                firstName = "วัชรพงษ์",
                lastName = "พวงแจ่ม",
                houseNo = "25",
                moo = "8",
                tambon = "ป่าขะ"
            ),
            listOf(
                person(id = 1),
                person(id = 2, householdId = 2)
            ),
            listOf(household(id = 1), household(id = 2))
        )

        assertEquals(ReferenceMatchStatus.UNKNOWN, result.status)
        assertTrue(result.reasons.any { it.contains("คะแนนเท่ากัน") })
    }
}
