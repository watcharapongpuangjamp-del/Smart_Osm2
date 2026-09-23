package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommunityLocationReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CommunityLocationReference>)

    @Query("SELECT * FROM community_location_reference ORDER BY pname, aname, tname, mname")
    suspend fun getAll(): List<CommunityLocationReference>

    @Query("SELECT * FROM community_location_reference WHERE mcode = :mcode LIMIT 1")
    suspend fun findByMcode(mcode: String): CommunityLocationReference?

    @Query("SELECT * FROM community_location_reference WHERE tcode = :tcode ORDER BY mname")
    suspend fun findByTcode(tcode: String): List<CommunityLocationReference>

    @Query("DELETE FROM community_location_reference")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM community_location_reference")
    suspend fun count(): Int

    @androidx.room.Transaction
    suspend fun replaceAll(items: List<CommunityLocationReference>) {
        deleteAll()
        if (items.isNotEmpty()) upsertAll(items)
    }
}
