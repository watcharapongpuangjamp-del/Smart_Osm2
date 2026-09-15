with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

content = content.replace("""            removeDeletionQueue(householdUuid)\n            removeDeletionQueue(personUuid)\n            Result.success(Unit)""", """            removeDeletionQueue(householdUuid)\n            Result.success(Unit)""")

content = content.replace("""            batch.commit().await()\n            Result.success(Unit)""", """            batch.commit().await()\n            removeDeletionQueue(personUuid)\n            Result.success(Unit)""")

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
    f.write(content)
