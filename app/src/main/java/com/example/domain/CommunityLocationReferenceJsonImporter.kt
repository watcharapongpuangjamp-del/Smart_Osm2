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
    @Json(name = "oct_side15_lat") val latitude: Double? = null,
    @Json(name = "oct_side15_lon") val longitude: Double? = null,
    @Json(name = "oct_side15_wmen") val femaleCount: Int? = null,
    @Json(name = "oct_side15_men") val maleCount: Int? = null,
    @Json(name = "oct_side15_total") val populationTotal: Int? = null,
    @Json(name = "oct_side15_house") val householdTotal: Int? = null,
    @Json(name = "oct_side15_local") val localAuthority: String? = null,
    @Json(name = "oct_side15_local_name") val localAuthorityName: String? = null,
    @Json(name = "oct_side15_road_name") val roadName: String? = null,
    @Json(name = "oct_side15_road_num") val roadNumber: String? = null,
    @Json(name = "oct_side15_road_distance") val roadDistance: Double? = null,
    @Json(name = "oct_side15_river_name") val riverName: String? = null,
    @Json(name = "oct_side15_sea_name") val seaName: String? = null,
    @Json(name = "oct_side15_lagoon_name") val lagoonName: String? = null,
    @Json(name = "oct_side15_swamp") val swampName: String? = null,
    @Json(name = "oct_side15_mountain_name") val mountainName: String? = null,
    @Json(name = "oct_side15_border_name1") val borderName1: String? = null,
    @Json(name = "oct_side15_border_distance1") val borderDistance1: Double? = null,
    @Json(name = "oct_side15_border_name2") val borderName2: String? = null,
    @Json(name = "oct_side15_border_distance2") val borderDistance2: Double? = null,
    @Json(name = "oct_side15_housing_total") val housingTotal: Int? = null,
    @Json(name = "oct_side15_condos_total") val condosTotal: Int? = null
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
                    latitude = row.latitude,
                    longitude = row.longitude,
                    femaleCount = row.femaleCount,
                    maleCount = row.maleCount,
                    populationTotal = row.populationTotal,
                    householdTotal = row.householdTotal,
                    localAuthority = row.localAuthority,
                    localAuthorityName = row.localAuthorityName,
                    roadName = row.roadName,
                    roadNumber = row.roadNumber,
                    roadDistance = row.roadDistance,
                    riverName = row.riverName,
                    seaName = row.seaName,
                    lagoonName = row.lagoonName,
                    swampName = row.swampName,
                    mountainName = row.mountainName,
                    borderName1 = row.borderName1,
                    borderDistance1 = row.borderDistance1,
                    borderName2 = row.borderName2,
                    borderDistance2 = row.borderDistance2,
                    housingTotal = row.housingTotal,
                    condosTotal = row.condosTotal,
                    sourceVersion = sourceVersion,
                    importedAt = now
                )
            }
        }
    }
}
