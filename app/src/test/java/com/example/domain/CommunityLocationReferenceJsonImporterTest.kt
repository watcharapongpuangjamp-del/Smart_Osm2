package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityLocationReferenceJsonImporterTest {
    @Test
    fun parsesTheProvidedThaiPhcFieldNames() {
        val json = """
            [{
              "pcode":"26","pname":"นครนายก",
              "acode":"2601","aname":"เมืองนครนายก",
              "tcode":"26010100","tname":"นครนายก",
              "mcode":"26010151","mname":"บ้านตลาดเก่า",
              "oct_side15_lat":"14.2031315",
              "oct_side15_lon":"101.2123109",
              "oct_side15_wmen":"455",
              "oct_side15_men":"519",
              "oct_side15_total":"974",
              "oct_side15_house":"306",
              "oct_side15_swamp_name":"คลองทดสอบ"
            }]
        """.trimIndent()

        val result = CommunityLocationReferenceJsonImporter.parse(json, "provided-json")
        assertEquals(1, result.size)
        assertEquals("26010151", result.single().mcode)
        assertEquals("คลองทดสอบ", result.single().swampName)
        assertEquals(455, result.single().femaleCount)
        assertEquals(519, result.single().maleCount)
        assertEquals(974, result.single().populationTotal)
    }

    @Test
    fun missingRequiredCodesAreRejectedByParser() {
        val json = """[{"pcode":"26","acode":"","tcode":"26010100","mcode":"26010151"}]"""
        assertTrue(CommunityLocationReferenceJsonImporter.parse(json).isEmpty())
    }
}
