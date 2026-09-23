package com.example.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "community_location_reference",
    indices = [
        Index(value = ["mcode"], unique = true),
        Index(value = ["tcode"]),
        Index(value = ["acode"]),
        Index(value = ["pcode"])
    ]
)
data class CommunityLocationReference(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pcode: String,
    val pname: String,
    val acode: String,
    val aname: String,
    val tcode: String,
    val tname: String,
    val mcode: String,
    val mname: String,
    val latitude: Double?,
    val longitude: Double?,
    val femaleCount: Int?,
    val maleCount: Int?,
    val populationTotal: Int?,
    val householdTotal: Int?,
    val localAuthority: String?,
    val localAuthorityName: String?,
    val roadName: String?,
    val roadNumber: String?,
    val roadDistance: Double?,
    val riverName: String?,
    val seaName: String?,
    val lagoonName: String?,
    val swampName: String?,
    val mountainName: String?,
    val borderName1: String?,
    val borderDistance1: Double?,
    val borderName2: String?,
    val borderDistance2: Double?,
    val housingTotal: Int?,
    val condosTotal: Int?,
    val sourceVersion: String = "",
    val importedAt: Long = System.currentTimeMillis()
)
