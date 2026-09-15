import re

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

# Update queueDeletion
content = content.replace("fun queueDeletion(uuid: String) {", "fun queueDeletion(uuid: String, type: String) {")
content = content.replace("pendingDeletionsPrefs.edit().putBoolean(uuid, true).apply()", "pendingDeletionsPrefs.edit().putString(uuid, type).apply()")

# Update getQueuedDeletions
content = content.replace("private fun getQueuedDeletions(): Set<String> {", "private fun getQueuedDeletions(): Map<String, String> {")
content = content.replace("return pendingDeletionsPrefs.all.keys", "return pendingDeletionsPrefs.all.mapValues { it.value.toString() }")

# Update syncFirestoreToRoom uses
content = content.replace("val allDeletedOrPending = deletedUuids + pendingDeletes", "val allDeletedOrPending = deletedUuids + pendingDeletes.keys")

# Update syncRoomToFirestore
push_sync = """    suspend fun syncRoomToFirestore(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing("กำลังเตรียมข้อมูลจาก Room Database...")
            val firestore = checkFirebaseConfiguredOrError()
            if (firestore == null) {
                val err = IllegalStateException("ระบบ Cloud (Firebase) ยังไม่ได้เชื่อมต่อในระบบนี้ (ใช้งานฐานข้อมูลภายใน Room ได้ปกติ)")
                _syncState.value = SyncState.Error(err.message ?: "", err)
                return@withContext Result.failure(err)
            }

            // Process pending deletions first
            val pendingDeletes = getQueuedDeletions()
            for ((uuid, type) in pendingDeletes) {
                if (type == "household") {
                    deleteHouseholdFromFirestore(uuid)
                } else if (type == "person") {
                    deletePersonFromFirestore(uuid)
                }
            }

            // Fetch tombstones to prevent re-uploading deleted records"""
content = re.sub(r'    suspend fun syncRoomToFirestore\(\): Result<SyncResult> = withContext\(Dispatchers\.IO\) \{.*?// Fetch tombstones to prevent re-uploading deleted records', push_sync, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
    f.write(content)
