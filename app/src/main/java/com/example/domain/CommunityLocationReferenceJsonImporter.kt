package com.example.domain

import com.example.data.CommunityLocationReference
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

data class CommunityLocationParseResult(
    val items: List<CommunityLocationReference>,
    val parsedCount: Int,
    val rejectedCount: Int,
    val warnings: List<String>
)

object CommunityLocationReferenceJsonImporter {
    private val adapter: JsonAdapter<List<Map<String, Any?>>> = run {
        val rowType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = Types.newParameterizedType(List::class.java, rowType)
        Moshi.Builder().build().adapter(listType)
    }

    fun parse(json: String, sourceVersion: String = "", sourceHash: String = ""): List<CommunityLocationReference> =
        parseDetailed(json, sourceVersion, sourceHash).items

    fun parseDetailed(
        json: String,
        sourceVersion: String = "",
        sourceHash: String = ""
    ): CommunityLocationParseResult {
        val rows = adapter.fromJson(json).orEmpty()
        val now = System.currentTimeMillis()
        var rejectedCount = 0
        val warnings = mutableListOf<String>()

        val items = rows.mapIndexedNotNull { index, row ->
            val pcode = stringValue(row["pcode"])
            val acode = stringValue(row["acode"])
            val tcode = stringValue(row["tcode"])
            val mcode = stringValue(row["mcode"])

            if (pcode.isBlank() || acode.isBlank() || tcode.isBlank() || mcode.isBlank()) {
                rejectedCount++
                warnings += "รายการที่ " + (index + 1) + " ถูกตัดออก: pcode/acode/tcode/mcode ไม่ครบ"
                return@mapIndexedNotNull null
            }

            fun numberString(key: String): String? =
                stringValue(row[key]).takeIf { it.isNotBlank() }

            CommunityLocationReference(
                pcode = pcode,
                pname = stringValue(row["pname"]),
                acode = acode,
                aname = stringValue(row["aname"]),
                tcode = tcode,
                tname = stringValue(row["tname"]),
                mcode = mcode,
                mname = stringValue(row["mname"]),
                latitude = numberString("oct_side15_lat")?.toDoubleOrNullWithWarning("oct_side15_lat", index, warnings),
                longitude = numberString("oct_side15_lon")?.toDoubleOrNullWithWarning("oct_side15_lon", index, warnings),
                femaleCount = numberString("oct_side15_wmen")?.toIntOrNullFlexible("oct_side15_wmen", index, warnings),
                maleCount = numberString("oct_side15_men")?.toIntOrNullFlexible("oct_side15_men", index, warnings),
                populationTotal = numberString("oct_side15_total")?.toIntOrNullFlexible("oct_side15_total", index, warnings),
                householdTotal = numberString("oct_side15_house")?.toIntOrNullFlexible("oct_side15_house", index, warnings),
                localAuthority = stringValue(row["oct_side15_local"]).takeIf { it.isNotBlank() },
                localAuthorityName = stringValue(row["oct_side15_local_name"]).takeIf { it.isNotBlank() },
                roadName = stringValue(row["oct_side15_road_name"]).takeIf { it.isNotBlank() },
                roadNumber = stringValue(row["oct_side15_road_num"]).takeIf { it.isNotBlank() },
                roadDistance = numberString("oct_side15_road_distance")?.toDoubleOrNullWithWarning("oct_side15_road_distance", index, warnings),
                riverName = stringValue(row["oct_side15_river_name"]).takeIf { it.isNotBlank() },
                seaName = stringValue(row["oct_side15_sea_name"]).takeIf { it.isNotBlank() },
                lagoonName = stringValue(row["oct_side15_lagoon_name"]).takeIf { it.isNotBlank() },
                swampName = stringValue(row["oct_side15_swamp_name"]).takeIf { it.isNotBlank() },
                mountainName = stringValue(row["oct_side15_mountain_name"]).takeIf { it.isNotBlank() },
                borderName1 = stringValue(row["oct_side15_border_name1"]).takeIf { it.isNotBlank() },
                borderDistance1 = numberString("oct_side15_border_distance1")?.toDoubleOrNullWithWarning("oct_side15_border_distance1", index, warnings),
                borderName2 = stringValue(row["oct_side15_border_name2"]).takeIf { it.isNotBlank() },
                borderDistance2 = numberString("oct_side15_border_distance2")?.toDoubleOrNullWithWarning("oct_side15_border_distance2", index, warnings),
                housingTotal = numberString("oct_side15_housing_total")?.toIntOrNullFlexible("oct_side15_housing_total", index, warnings),
                condosTotal = numberString("oct_side15_condos_total")?.toIntOrNullFlexible("oct_side15_condos_total", index, warnings),
                sourceVersion = sourceVersion,
                sourceHash = sourceHash,
                importedAt = now
            )
        }

        return CommunityLocationParseResult(
            items = items,
            parsedCount = rows.size,
            rejectedCount = rejectedCount,
            warnings = warnings
        )
    }

    private fun stringValue(value: Any?): String = when (value) {
        null -> ""
        is String -> value.trim()
        is Number -> value.toString()
        else -> value.toString().trim()
    }

    private fun String.toDoubleOrNullWithWarning(
        key: String,
        rowIndex: Int,
        warnings: MutableList<String>
    ): Double? = toDoubleOrNull().also {
        if (it == null) warnings += "รายการที่ " + (rowIndex + 1) + ": ค่า " + key + " ไม่ใช่ตัวเลขที่อ่านได้"
    }

    private fun String.toIntOrNullFlexible(
        key: String,
        rowIndex: Int,
        warnings: MutableList<String>
    ): Int? {
        val direct = toIntOrNull()
        if (direct != null) return direct
        val decimal = toDoubleOrNull()
        if (decimal != null && decimal.isFinite() && decimal % 1.0 == 0.0) return decimal.toInt()
        warnings += "รายการที่ " + (rowIndex + 1) + ": ค่า " + key + " ไม่ใช่จำนวนเต็มที่อ่านได้"
        return null
    }
}
