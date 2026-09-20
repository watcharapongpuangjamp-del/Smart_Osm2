package com.example.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ConflictResolverTest {

    private fun meta(
        lastModified: Long,
        serverUpdatedAt: Long? = null,
        version: Long = 0L,
        isDeleted: Boolean = false
    ) = SyncMetadata(
        lastModified = lastModified,
        serverUpdatedAt = serverUpdatedAt,
        version = version,
        updatedBy = "test",
        updatedFrom = "test",
        isDeleted = isDeleted
    )

    @Test
    fun newerVersionWins() {
        val result = ConflictResolver.decide(
            meta(lastModified = 100, version = 2),
            meta(lastModified = 90, version = 3)
        )
        assertEquals(ConflictResolver.Decision.ACCEPT_REMOTE, result)
    }

    @Test
    fun newerServerTimestampWinsWhenVersionsEqual() {
        val result = ConflictResolver.decide(
            meta(lastModified = 200, serverUpdatedAt = 1000, version = 4),
            meta(lastModified = 100, serverUpdatedAt = 1001, version = 4)
        )
        assertEquals(ConflictResolver.Decision.ACCEPT_REMOTE, result)
    }

    @Test
    fun newerLastModifiedWinsWhenCloudMetadataIsUnavailable() {
        val result = ConflictResolver.decide(
            meta(lastModified = 100),
            meta(lastModified = 101)
        )
        assertEquals(ConflictResolver.Decision.ACCEPT_REMOTE, result)
    }

    @Test
    fun olderRemoteDoesNotOverwriteLocal() {
        val result = ConflictResolver.decide(
            meta(lastModified = 500, version = 8),
            meta(lastModified = 400, version = 7)
        )
        assertEquals(ConflictResolver.Decision.KEEP_LOCAL, result)
    }

    @Test
    fun newerTombstoneWinsOverOlderLiveRecord() {
        val result = ConflictResolver.decide(
            meta(lastModified = 100, version = 2, isDeleted = false),
            meta(lastModified = 200, version = 3, isDeleted = true)
        )
        assertEquals(ConflictResolver.Decision.ACCEPT_REMOTE, result)
    }

    @Test
    fun olderTombstoneDoesNotDeleteNewerLiveRecord() {
        val result = ConflictResolver.decide(
            meta(lastModified = 500, version = 8, isDeleted = false),
            meta(lastModified = 400, version = 7, isDeleted = true)
        )
        assertEquals(ConflictResolver.Decision.KEEP_LOCAL, result)
    }

    @Test
    fun exactTieIsDeterministic() {
        val result = ConflictResolver.decide(
            meta(lastModified = 100, version = 2),
            meta(lastModified = 100, version = 2)
        )
        assertEquals(ConflictResolver.Decision.ACCEPT_REMOTE, result)
    }
}
