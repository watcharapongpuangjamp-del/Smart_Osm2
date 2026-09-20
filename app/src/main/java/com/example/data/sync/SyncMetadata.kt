package com.example.data.sync

/**
 * Cloud synchronization metadata shared by Android and future web clients.
 *
 * The local lastModified value is retained for offline editing.
 * serverUpdatedAt/version are cloud conflict-control metadata.
 */
data class SyncMetadata(
    val lastModified: Long,
    val serverUpdatedAt: Long? = null,
    val version: Long = 0L,
    val updatedBy: String? = null,
    val updatedFrom: String? = null,
    val isDeleted: Boolean = false
)
