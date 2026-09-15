import re

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

# Add queue methods
queue_methods = """
    private val pendingDeletionsPrefs by lazy { context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE) }

    fun queueDeletion(uuid: String) {
        pendingDeletionsPrefs.edit().putBoolean(uuid, true).apply()
    }

    fun removeDeletionQueue(uuid: String) {
        pendingDeletionsPrefs.edit().remove(uuid).apply()
    }

    private fun getQueuedDeletions(): Set<String> {
        return pendingDeletionsPrefs.all.keys
    }
"""
content = re.sub(r'class RoomFirestoreSyncHelper\(.*?\)\s*\{', lambda m: m.group(0) + queue_methods, content, flags=re.DOTALL)

# In syncFirestoreToRoom:
sync_method = """    suspend fun syncFirestoreToRoom(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing("กำลังดึงข้อมูลจาก Cloud Firestore...")
            val firestore = checkFirebaseConfiguredOrError()
            if (firestore == null) {
                val err = IllegalStateException("ระบบ Cloud (Firebase) ยังไม่ได้เชื่อมต่อในระบบนี้ (ใช้งานฐานข้อมูลภายใน Room ได้ปกติ)")
                _syncState.value = SyncState.Error(err.message ?: "", err)
                return@withContext Result.failure(err)
            }

            // Fetch tombstones
            val tombstoneDocs = firestore.collection(COLLECTION_TOMBSTONES).get().await()
            val deletedUuids = tombstoneDocs.documents.mapNotNull { it.getString("uuid") }.toSet()
            val pendingDeletes = getQueuedDeletions()
            val allDeletedOrPending = deletedUuids + pendingDeletes

            // Remove stale local Room records that have been deleted in Cloud
            val allLocalHouseholdsToDelete = repository.getAllHouseholds().filter { deletedUuids.contains(it.householdUuid) }
            for (h in allLocalHouseholdsToDelete) {
                repository.deleteHousehold(h)
            }
            val allLocalPersonsToDelete = repository.getAllPersonsList().filter { deletedUuids.contains(it.personUuid) }
            for (p in allLocalPersonsToDelete) {
                repository.delete(p)
            }

            val householdDocs = firestore.collection(COLLECTION_HOUSEHOLDS).get().await()
            val personDocs = firestore.collection(COLLECTION_PERSONS).get().await()

            _syncState.value = SyncState.Syncing("กำลังนำเข้าข้อมูล เข้าสู่ Room...")

            var householdsImported = 0
            var personsImported = 0

            // 1. Process Households (Strict UUID matching & Tombstone filtering)
            for (doc in householdDocs.documents) {
                val household = docToHousehold(doc) ?: continue
                if (allDeletedOrPending.contains(household.householdUuid)) {
                    // Try to push the pending deletion to Cloud if it exists there
                    if (pendingDeletes.contains(household.householdUuid)) {
                        deleteHouseholdFromFirestore(household.householdUuid)
                    }
                    continue
                }"""
content = re.sub(r'    suspend fun syncFirestoreToRoom\(\): Result<SyncResult> = withContext\(Dispatchers\.IO\) \{.*?if \(deletedUuids\.contains\(household\.householdUuid\)\) \{\s*continue\s*\}', sync_method, content, flags=re.DOTALL)

sync_person = """            // 2. Process Persons (Strict UUID matching & Tombstone filtering)
            for (doc in personDocs.documents) {
                val personUuid = doc.getString("personUuid") ?: doc.id
                if (allDeletedOrPending.contains(personUuid)) {
                    if (pendingDeletes.contains(personUuid)) {
                        deletePersonFromFirestore(personUuid)
                    }
                    continue
                }"""
content = re.sub(r'            // 2\. Process Persons \(Strict UUID matching & Tombstone filtering\).*?if \(deletedUuids\.contains\(personUuid\)\) \{\s*continue\s*\}', sync_person, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
    f.write(content)
