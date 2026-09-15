import re

# Fix RoomFirestoreSyncHelper.kt
with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

# Remove the incorrectly placed methods
queue_methods = """    private val pendingDeletionsPrefs by lazy { context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE) }

    fun queueDeletion(uuid: String, type: String) {
        pendingDeletionsPrefs.edit().putString(uuid, type).apply()
    }

    fun removeDeletionQueue(uuid: String) {
        pendingDeletionsPrefs.edit().remove(uuid).apply()
    }

    private fun getQueuedDeletions(): Map<String, String> {
        return pendingDeletionsPrefs.all.mapValues { it.value.toString() }
    }"""
    
content = content.replace(queue_methods, "")

# Insert queue_methods after `companion object { ... }` or just at the beginning of the class
# Find `open class RoomFirestoreSyncHelper( ... ) {` by finding `) {`
match_class = re.search(r'\)\s*\{\s*companion object', content)
if match_class:
    content = content[:match_class.end()] + "\n" + queue_methods + "\n" + content[match_class.end():]

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
    f.write(content)
