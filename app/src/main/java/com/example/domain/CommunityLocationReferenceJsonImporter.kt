package com.example.domain

import com.example.data.CommunityLocationReference
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class CommunityLocationReferenceJsonRow(
    val pcode: String? = null,
    val pname: String? = null,
    val acode: String? = null,
    val aname: String? = null,
    val tcode: String? = null,
    val tname: String? = null,
    val mcode: String? = null,
    val mname: String? = null,
    @Json(name = "oct_side15_lat") val latitude: String? = null,
    @Json(name = "oct_side15_lon") val longitude: String? = null,
    @Json(name = "oct_side15_wmen") val femaleCount: String? = null,
    @Json(name = "oct_side15_men") val maleCount: String? = null,
    @Json(name = "oct_side15_total") val populationTotal: String? = null,
    @Json(name = "oct_side15_house") val householdTotal: String? = null,
    @Json(name = "oct_side15_local") val localAuthority: String? = null,
    @Json(name = "oct_side15_local_name") val localAuthorityName: String? = null,
    @Json(name = "oct_side15_road_name") val roadName: String? = null,
    @Json(name = "oct_side15_road_num") val roadNumber: String? = null,
    @Json(name = "oct_side15_road_distance") val roadDistance: String? = null,
    @Json(name = "oct_side15_river_name") val riverName: String? = null,
    @Json(name = "oct_side15_sea_name") val seaName: String? = null,
    @Json(name = "oct_side15_lagoon_name") val lagoonName: String? = null,
    @Json(name = "oct_side15_swamp_name") val swampName: String? = null,
    @Json(name = "oct_side15_mountain_name") val mountainName: String? = null,
    @Json(name = "oct_side15_border_name1") val borderName1: String? = null,
    @Json(name = "oct_side15_border_distance1") val borderDistance1: String? = null,
    @Json(name = "oct_side15_border_name2") val borderName2: String? = null,
    @Json(name = "oct_side15_border_distance2") val borderDistance2: String? = null,
    @Json(name = "oct_side15_housing_total") val housingTotal: String? = null,
    @Json(name = "oct_side15_condos_total") val condosTotal: String? = null
)

object CommunityLocationReferenceJsonImporter {
    private val adapter = Moshi.Builder()
        .build()
        .adapter<List<CommunityLocationReferenceJsonRow>>(
            Types.newParameterizedType(
                List::class.java,
                CommunityLocationReferenceJsonRow::class.java
            )
        )

    fun parse(json: String, sourceVersion: String = ""): List<CommunityLocationReference> {
        val now = System.currentTimeMillis()
        return adapter.fromJson(json).orEmpty().mapNotNull { row ->
            val pcode = row.pcode?.trim().orEmpty()
            val acode = row.acode?.trim().orEmpty()
            val tcode = row.tcode?.trim().orEmpty()
            val mcode = row.mcode?.trim().orEmpty()
            if (pcode.isBlank() || acode.isBlank() || tcode.isBlank() || mcode.isBlank()) {
                null
            } else {
                CommunityLocationReference(
                    pcode = pcode,
                    pname = row.pname?.trim().orEmpty(),
                    acode = acode,
                    aname = row.aname?.trim().orEmpty(),
                    tcode = tcode,
                    tname = row.tname?.trim().orEmpty(),
                    mcode = mcode,
                    mname = row.mname?.trim().orEmpty(),
                    latitude = row.latitude?.toDoubleOrNull(),
                    longitude = row.longitude?.toDoubleOrNull(),
                    femaleCount = row.femaleCount?.toIntOrNull(),
                    maleCount = row.maleCount?.toIntOrNull(),
                    populationTotal = row.populationTotal?.toIntOrNull(),
                    householdTotal = row.householdTotal?.toIntOrNull(),
                    localAuthority = row.localAuthority?.trim(),
                    localAuthorityName = row.localAuthorityName?.trim(),
                    roadName = row.roadName?.trim(),
                    roadNumber = row.roadNumber?.trim(),
                    roadDistance = row.roadDistance?.toDoubleOrNull(),
                    riverName = row.riverName?.trim(),
                    seaName = row.seaName?.trim(),
                    lagoonName = row.lagoonName?.trim(),
                    swampName = row.swampName?.trim(),
                    mountainName = row.mountainName?.trim(),
                    borderName1 = row.borderName1?.trim(),
                    borderDistance1 = row.borderDistance1?.toDoubleOrNull(),
                    borderName2 = row.borderName2?.trim(),
                    borderDistance2 = row.borderDistance2?.toDoubleOrNull(),
                    housingTotal = row.housingTotal?.toIntOrNull(),
                    condosTotal = row.condosTotal?.toIntOrNull(),
                    sourceVersion = sourceVersion,
                    importedAt = now
                )
            }
        }
    }
}
