package com.example.data.sync

/**
 * Deterministic conflict resolution for offline-first clients.
 *
 * Rules:
 * 1. A tombstone wins over an older live record.
 * 2. Higher server version wins when both versions are known.
 * 3. Otherwise newer serverUpdatedAt wins.
 * 4. Otherwise newer lastModified wins.
 * 5. Exact ties are resolved deterministically in favour of the incoming record.
 */
object ConflictResolver {

    enum class Decision {
        KEEP_LOCAL,
        ACCEPT_REMOTE
    }

    fun decide(local: SyncMetadata, remote: SyncMetadata): Decision {
        if (remote.isDeleted != local.isDeleted) {
            return if (remote.isDeleted && remoteVersionOrTime(remote) >= remoteVersionOrTime(local)) {
                Decision.ACCEPT_REMOTE
            } else {
                Decision.KEEP_LOCAL
            }
        }

        if (local.version > 0L && remote.version > 0L && local.version != remote.version) {
            return if (remote.version > local.version) Decision.ACCEPT_REMOTE else Decision.KEEP_LOCAL
        }

        val remoteServerTime = remote.serverUpdatedAt
        val localServerTime = local.serverUpdatedAt
        if (remoteServerTime != null && localServerTime != null && remoteServerTime != localServerTime) {
            return if (remoteServerTime > localServerTime) Decision.ACCEPT_REMOTE else Decision.KEEP_LOCAL
        }

        if (remote.lastModified != local.lastModified) {
            return if (remote.lastModified > local.lastModified) Decision.ACCEPT_REMOTE else Decision.KEEP_LOCAL
        }

        return Decision.ACCEPT_REMOTE
    }

    private fun remoteVersionOrTime(metadata: SyncMetadata): Long =
        when {
            metadata.version > 0L -> metadata.version
            metadata.serverUpdatedAt != null -> metadata.serverUpdatedAt
            else -> metadata.lastModified
        }
}
