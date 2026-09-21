package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_deletion_journal",
    indices = [Index(value = ["entityType", "entityUuid"], unique = true)]
)
data class SyncDeletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityUuid: String,
    val deletedAt: Long
)
