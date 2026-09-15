import re

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'r') as f:
    content = f.read()

# Add removeDeletionQueue to deleteHouseholdFromFirestore
content = content.replace("Result.success(Unit)", "removeDeletionQueue(householdUuid)\n            Result.success(Unit)", 1)

# Add removeDeletionQueue to deletePersonFromFirestore
content = content.replace("Result.success(Unit)", "removeDeletionQueue(personUuid)\n            Result.success(Unit)", 1)

with open('app/src/main/java/com/example/data/sync/RoomFirestoreSyncHelper.kt', 'w') as f:
    f.write(content)
