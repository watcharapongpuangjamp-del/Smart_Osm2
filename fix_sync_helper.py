with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

# Replace the messy section between class definition and isFirebaseConfigured
old_section = """open class RoomFirestoreSyncHelper(
    private val context: Context,
    private val repository: PersonRepository,
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
    

                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp is not initialized: ${e.message}")
            null
        }
    }
) {
    companion object
    private val pendingDeletionsPrefs by lazy { context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE) }

    fun queueDeletion(uuid: String, type: String) {
        pendingDeletionsPrefs.edit().putString(uuid, type).apply()
    }

    fun removeDeletionQueue(uuid: String) {
        pendingDeletionsPrefs.edit().remove(uuid).apply()
    }

    private fun getQueuedDeletions(): Map<String, String> {
        return pendingDeletionsPrefs.all.mapValues { it.value.toString() }
    }
 {
        private const val TAG = "RoomFirestoreSyncHelper"
        const val COLLECTION_HOUSEHOLDS = "households"
        const val COLLECTION_PERSONS = "persons"
        const val COLLECTION_TOMBSTONES = "tombstones"
    }"""

new_section = """open class RoomFirestoreSyncHelper(
    private val context: Context,
    private val repository: PersonRepository,
    private val firestoreProvider: () -> FirebaseFirestore? = {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp is not initialized: ${e.message}")
            null
        }
    }
) {
    companion object {
        private const val TAG = "RoomFirestoreSyncHelper"
        const val COLLECTION_HOUSEHOLDS = "households"
        const val COLLECTION_PERSONS = "persons"
        const val COLLECTION_TOMBSTONES = "tombstones"
    }

    private val pendingDeletionsPrefs by lazy { context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE) }

    fun queueDeletion(uuid: String, type: String) {
        pendingDeletionsPrefs.edit().putString(uuid, type).apply()
    }

    fun removeDeletionQueue(uuid: String) {
        pendingDeletionsPrefs.edit().remove(uuid).apply()
    }

    private fun getQueuedDeletions(): Map<String, String> {
        return pendingDeletionsPrefs.all.mapValues { it.value.toString() }
    }"""

if old_section in content:
    content = content.replace(old_section, new_section)
    with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
        f.write(content)
    print("Successfully patched RoomFirestoreSyncHelper.kt")
else:
    print("Old section not found exactly, doing regex or manual check")
