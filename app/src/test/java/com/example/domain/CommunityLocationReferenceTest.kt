package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CommunityLocationReferenceTest {
    @Test
    fun parsesThaiPhcStringNumbersAndPreservesVillageIdentity() {
        val json = """
            [{
              "pcode":"26","pname":"นครนายก",
              "acode":"2601","aname":"เมืองนครนายก",
              "tcode":"26010100","tname":"นครนายก",
              "mcode":"26010151","mname":"บ้านตลาดเก่า",
              "oct_side15_lat":"14.203131525494243",
              "oct_side15_lon":"101.21231094002724",
              "oct_side15_wmen":"455",
              "oct_side15_men":"519",
              "oct_side15_total":"974",
              "oct_side15_house":"306",
              "oct_side15_local":"เทศบาลเมืองนครนายก",
              "oct_side15_river_name":"แม่น้ำนครนายก"
            }]
        """.trimIndent()

        val result = CommunityLocationReferenceJsonImporter.parse(json, "test")
        assertEquals(1, result.size)
        val item = result.single()
        assertEquals("26010151", item.mcode)
        assertEquals(974, item.populationTotal)
        assertEquals(306, item.householdTotal)
        assertEquals(14.203131525494243, item.latitude!!, 0.000000001)
        assertNotNull(item.riverName)
    }

    @Test
    fun validatorKeepsPopulationMismatchAsWarningNotSilentCorrection() {
        val item = CommunityLocationReference(
            pcode = "26", pname = "นครนายก",
            acode = "2601", aname = "เมืองนครนายก",
            tcode = "26010100", tname = "นครนายก",
            mcode = "TEST", mname = "ทดสอบ",
            latitude = 14.0, longitude = 101.0,
            femaleCount = 10, maleCount = 20, populationTotal = 99,
            householdTotal = 5
        )
        val result = CommunityLocationReferenceValidator.validate(listOf(item))
        assertEquals(1, result.accepted.size)
        assertEquals(1, result.warnings.size)
        assertEquals(99, result.accepted.single().populationTotal)
    }
}
