package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(deletion: SyncDeletion)

    @Query("SELECT * FROM sync_deletion_journal ORDER BY deletedAt ASC")
    suspend fun getAll(): List<SyncDeletion>

    @Query("DELETE FROM sync_deletion_journal WHERE entityType = :entityType AND entityUuid = :entityUuid")
    suspend fun delete(entityType: String, entityUuid: String)

    @Query("DELETE FROM sync_deletion_journal")
    suspend fun deleteAll()
}
