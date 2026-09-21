package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.DataStatus
import com.example.data.Gender
import com.example.data.Household
import com.example.data.HouseholdRole
import com.example.data.Person
import com.example.data.PersonRepository
import com.example.data.PersonStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Await extension for Firebase Tasks in coroutines.
 */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}

/**
 * Summary metrics of a sync operation between Room and Cloud Firestore.
 */
data class SyncResult(
    val householdsSynced: Int = 0,
    val personsSynced: Int = 0,
    val deletionsSynced: Int = 0,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * State representation for Room-to-Firestore synchronization.
 */
sealed interface SyncState {
    object Idle : SyncState
    data class Syncing(val message: String) : SyncState
    data class Success(val result: SyncResult) : SyncState
    data class Error(val message: String, val throwable: Throwable? = null) : SyncState
}

/**
 * Room-to-Firestore synchronization helper class.
 *
 * Provides bidirectional and push/pull synchronization between the local Room database
 * and Cloud Firestore collections ("households" and "persons") with UUID mapping for idempotent writes.
 */
open class RoomFirestoreSyncHelper(
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

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    open fun isFirebaseConfigured(): Boolean {
        return try {
            firestoreProvider() != null
        } catch (e: Exception) {
            false
        }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    private fun getFirestore(): FirebaseFirestore {
        return firestoreProvider()
            ?: throw IllegalStateException("ระบบ Cloud (Firebase) ยังไม่ได้ตั้งค่าในโปรเจกต์นี้ กรุณาใช้งานฐานข้อมูลภายใน (Room) แทน")
    }

    @androidx.annotation.VisibleForTesting
    internal open fun requireAuthenticatedFirebaseUser() {
        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (e: Exception) {
            null
        }
        if (user == null) {
            throw IllegalStateException(
                "FIREBASE_AUTH_REQUIRED: กรุณาเข้าสู่ระบบ Firebase ก่อนซิงค์ข้อมูล Cloud"
            )
        }
    }


    private fun checkFirebaseConfiguredOrError(): FirebaseFirestore? {
        return try {
            firestoreProvider()
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // ROOM -> FIRESTORE (Upload / Persist)
    // =========================================================================
    private fun remoteMetadata(doc: DocumentSnapshot): SyncMetadata = SyncMetadata(
        lastModified = doc.getLong("updatedAt") ?: 0L,
        serverUpdatedAt = doc.getLong("serverUpdatedAt"),
        version = doc.getLong("version") ?: 0L,
        updatedBy = doc.getString("updatedBy"),
        updatedFrom = doc.getString("updatedFrom"),
        isDeleted = doc.getBoolean("isDeleted") == true
    )

    private fun localMetadata(household: Household): SyncMetadata = SyncMetadata(
        lastModified = household.lastModified,
        serverUpdatedAt = household.serverUpdatedAt,
        version = household.version,
        updatedBy = household.updatedBy,
        updatedFrom = household.updatedFrom,
        isDeleted = household.isDeleted
    )

    private fun localMetadata(person: Person): SyncMetadata = SyncMetadata(
        lastModified = person.lastModified,
        serverUpdatedAt = person.serverUpdatedAt,
        version = person.version,
        updatedBy = person.updatedBy,
        updatedFrom = person.updatedFrom,
        isDeleted = person.isDeleted
    )

    private fun shouldUpload(local: SyncMetadata, remote: SyncMetadata): Boolean =
        ConflictResolver.decide(remote, local) == ConflictResolver.Decision.ACCEPT_REMOTE

    private fun shouldAcceptRemote(local: SyncMetadata, remote: SyncMetadata): Boolean =
        ConflictResolver.decide(local, remote) == ConflictResolver.Decision.ACCEPT_REMOTE

    private fun nextVersion(localVersion: Long, remoteVersion: Long?): Long =
        maxOf(localVersion, remoteVersion ?: 0L) + 1L

    private suspend fun transactionalPersonSave(
        person: Person,
        householdUuid: String,
        householdHouseNo: String
    ) {
        val firestore = getFirestore()
        val ref = firestore.collection(COLLECTION_PERSONS).document(person.personUuid)
        firestore.runTransaction { tx ->
            val remote = tx.get(ref)
            if (remote.exists() && !shouldUpload(localMetadata(person), remoteMetadata(remote))) {
                throw IllegalStateException("Cloud มีข้อมูลบุคคลใหม่กว่า จึงไม่เขียนทับ")
            }
            tx.set(ref, personToVersionedMap(person, householdUuid, householdHouseNo, remote), SetOptions.merge())
            null
        }.await()
    }

    private suspend fun transactionalHouseholdSave(
        household: Household
    ) {
        val firestore = getFirestore()
        val ref = firestore.collection(COLLECTION_HOUSEHOLDS).document(household.householdUuid)
        firestore.runTransaction { tx ->
            val remote = tx.get(ref)
            if (remote.exists() && !shouldUpload(localMetadata(household), remoteMetadata(remote))) {
                throw IllegalStateException("Cloud มีข้อมูลครัวเรือนใหม่กว่า จึงไม่เขียนทับ")
            }
            tx.set(ref, householdToVersionedMap(household, remote), SetOptions.merge())
            null
        }.await()
    }

    private fun householdToVersionedMap(
        household: Household,
        remote: DocumentSnapshot?
    ): Map<String, Any?> {
        val now = System.currentTimeMillis()
        return householdToMap(household) + mapOf(
            "version" to nextVersion(household.version, remote?.getLong("version")),
            "serverUpdatedAt" to now,
            "updatedAt" to now,
            "updatedFrom" to (household.updatedFrom ?: "android")
        )
    }

    private fun personToVersionedMap(
        person: Person,
        householdUuid: String,
        householdHouseNo: String,
        remote: DocumentSnapshot?
    ): Map<String, Any?> {
        val now = System.currentTimeMillis()
        return personToMap(person, householdUuid, householdHouseNo) + mapOf(
            "version" to nextVersion(person.version, remote?.getLong("version")),
            "serverUpdatedAt" to now,
            "updatedAt" to now,
            "updatedFrom" to (person.updatedFrom ?: "android")
        )
    }


    /**
     * Uploads all local Room households and registered citizens to Cloud Firestore,
     * skipping any records that have deletion tombstones.
     * Uses batch writes for high efficiency and atomic updates, respecting Firestore batch limits (max 500).
     */
    suspend fun syncRoomToFirestore(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedFirebaseUser()
            _syncState.value = SyncState.Syncing("กำลังเตรียมข้อมูลจาก Room Database...")
            val firestore = checkFirebaseConfiguredOrError()
            if (firestore == null) {
                val err = IllegalStateException("ระบบ Cloud (Firebase) ยังไม่ได้เชื่อมต่อในระบบนี้ (ใช้งานฐานข้อมูลภายใน Room ได้ปกติ)")
                _syncState.value = SyncState.Error(err.message ?: "", err)
                return@withContext Result.failure(err)
            }

            // Local deletions are journaled so Push can converge Cloud to the Local master.
            // Clear each journal entry only after its Firestore deletion/tombstone succeeds.
            val pendingDeletions = repository.getPendingDeletions()
            var deletionsSynced = 0
            if (pendingDeletions.isNotEmpty()) {
                _syncState.value = SyncState.Syncing("กำลังส่งรายการลบจากฐานข้อมูลภายในไปยัง Cloud...")
                for (deletion in pendingDeletions) {
                    val result = when (deletion.entityType) {
                        "HOUSEHOLD" -> deleteHouseholdFromFirestore(deletion.entityUuid)
                        "PERSON" -> deletePersonFromFirestore(deletion.entityUuid)
                        else -> Result.failure(IllegalStateException("ไม่รู้จักประเภทการลบ: ${deletion.entityType}"))
                    }
                    if (result.isFailure) {
                        throw result.exceptionOrNull()
                            ?: IllegalStateException("ไม่สามารถส่งรายการลบ ${deletion.entityType}:${deletion.entityUuid} ไปยัง Cloud")
                    }
                    repository.clearPendingDeletion(deletion)
                    deletionsSynced++
                }
            }

            // Refresh tombstones after processing the local deletion journal.
            val tombstoneDocs = firestore.collection(COLLECTION_TOMBSTONES).get().await()
            val deletedUuids = tombstoneDocs.documents.mapNotNull { it.getString("uuid") }.toSet()

            val households = repository.getAllHouseholds().filter { !deletedUuids.contains(it.householdUuid) }
            val persons = repository.getAllPersonsList().filter { !deletedUuids.contains(it.personUuid) }

            val cloudHouseholds = firestore.collection(COLLECTION_HOUSEHOLDS).get().await().documents.associateBy { it.id }
            val cloudPersons = firestore.collection(COLLECTION_PERSONS).get().await().documents.associateBy { it.id }

            _syncState.value = SyncState.Syncing("กำลังตรวจสอบเวอร์ชันข้อมูลก่อนส่งขึ้น Firestore...")

            val householdMap = households.associateBy { it.id }

            // Write households and persons in batches (Firestore max 500 per batch, we use 400 safely)
            var batch = firestore.batch()
            var opsInBatch = 0
            var householdsSynced = 0
            var personsSynced = 0

            for (h in households) {
                val remote = cloudHouseholds[h.householdUuid]
                if (remote != null && !shouldUpload(localMetadata(h), remoteMetadata(remote))) continue
                val docRef = firestore.collection(COLLECTION_HOUSEHOLDS).document(h.householdUuid)
                val data = householdToVersionedMap(h, remote)
                batch.set(docRef, data, SetOptions.merge())
                opsInBatch++
                householdsSynced++

                if (opsInBatch >= 400) {
                    batch.commit().await()
                    batch = firestore.batch()
                    opsInBatch = 0
                }
            }

            for (p in persons) {
                val parentHousehold = householdMap[p.householdId] ?: continue
                val remote = cloudPersons[p.personUuid]
                if (remote != null && !shouldUpload(localMetadata(p), remoteMetadata(remote))) continue
                val docRef = firestore.collection(COLLECTION_PERSONS).document(p.personUuid)
                val data = personToVersionedMap(p, parentHousehold.householdUuid, parentHousehold.houseNo, remote)
                batch.set(docRef, data, SetOptions.merge())
                opsInBatch++
                personsSynced++

                if (opsInBatch >= 400) {
                    batch.commit().await()
                    batch = firestore.batch()
                    opsInBatch = 0
                }
            }

            if (opsInBatch > 0) {
                batch.commit().await()
            }

            val result = SyncResult(
                householdsSynced = householdsSynced,
                personsSynced = personsSynced,
                deletionsSynced = deletionsSynced,
                message = "ซิงค์ข้อมูลไปยัง Firestore สำเร็จ ($householdsSynced ครัวเรือน, $personsSynced คน, ลบ $deletionsSynced รายการ)"
            )
            _syncState.value = SyncState.Success(result)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Room to Firestore", e)
            val errorMsg = e.message ?: "เกิดข้อผิดพลาดในการซิงค์ข้อมูลกับ Firestore"
            _syncState.value = SyncState.Error(errorMsg, e)
            Result.failure(e)
        }
    }

    /**
     * Persists a single household and its members to Cloud Firestore.
     */
    suspend fun syncHouseholdToFirestore(
        household: Household,
        persons: List<Person> = emptyList()
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val firestore = getFirestore()
            
            // Check if household has been tombstoned
            val hTombstoneRef = firestore.collection(COLLECTION_TOMBSTONES)
                .document("household_${household.householdUuid}")
                .get().await()
            if (hTombstoneRef.exists()) {
                return@withContext Result.failure(IllegalStateException("Cannot sync: Household ${household.householdUuid} was deleted on Cloud."))
            }

            // Filter out tombstoned persons
            val tombstonedPersonUuids = mutableSetOf<String>()
            if (persons.isNotEmpty()) {
                for (chunk in persons.map { it.personUuid }.chunked(30)) {
                    val docs = firestore.collection(COLLECTION_TOMBSTONES)
                        .whereIn("uuid", chunk)
                        .get().await()
                    for (doc in docs.documents) {
                        tombstonedPersonUuids.add(doc.getString("uuid") ?: "")
                    }
                }
            }
            
            val validPersons = persons.filter { !tombstonedPersonUuids.contains(it.personUuid) }

            val batch = firestore.batch()

            val hRef = firestore.collection(COLLECTION_HOUSEHOLDS).document(household.householdUuid)
            val remoteHousehold = hRef.get().await()
            if (remoteHousehold.exists() && !shouldUpload(localMetadata(household), remoteMetadata(remoteHousehold))) {
                return@withContext Result.failure(IllegalStateException("Cloud มีข้อมูลครัวเรือนใหม่กว่า จึงไม่เขียนทับ"))
            }
            val householdData = householdToVersionedMap(household, remoteHousehold)
            batch.set(hRef, householdData, SetOptions.merge())

            for (p in validPersons) {
                val pRef = firestore.collection(COLLECTION_PERSONS).document(p.personUuid)
                val remotePerson = pRef.get().await()
                if (remotePerson.exists() && !shouldUpload(localMetadata(p), remoteMetadata(remotePerson))) continue
                batch.set(pRef, personToVersionedMap(p, household.householdUuid, household.houseNo, remotePerson), SetOptions.merge())
            }

            batch.commit().await()

            val result = SyncResult(
                householdsSynced = 1,
                personsSynced = validPersons.size,
                message = "บันทึกครัวเรือนเลขที่ ${household.houseNo} ไปยัง Firestore เรียบร้อย"
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync household ${household.houseNo}", e)
            Result.failure(e)
        }
    }

    @androidx.annotation.VisibleForTesting
    internal open suspend fun checkTombstoneExists(uuid: String, type: String): Boolean {
        return getFirestore().collection(COLLECTION_TOMBSTONES)
            .document("${type}_${uuid}")
            .get().await().exists()
    }

    @androidx.annotation.VisibleForTesting
    internal open suspend fun performPersonSave(person: Person, householdUuid: String, householdHouseNo: String) {
        transactionalPersonSave(person, householdUuid, householdHouseNo)
    }

    /**
     * Persists a single citizen record to Cloud Firestore.
     */
    suspend fun syncPersonToFirestore(
        person: Person,
        householdUuid: String,
        householdHouseNo: String = ""
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedFirebaseUser()
            // Check if person has been tombstoned
            if (checkTombstoneExists(person.personUuid, "person")) {
                return@withContext Result.failure(IllegalStateException("Cannot sync: Person ${person.personUuid} was deleted on Cloud."))
            }

            // Check if parent household has been tombstoned
            if (checkTombstoneExists(householdUuid, "household")) {
                return@withContext Result.failure(IllegalStateException("Cannot sync: Parent Household $householdUuid was deleted on Cloud."))
            }
            
            performPersonSave(person, householdUuid, householdHouseNo)

            val result = SyncResult(
                householdsSynced = 0,
                personsSynced = 1,
                message = "บันทึกข้อมูล ${person.fullName} ไปยัง Firestore เรียบร้อย"
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync person ${person.fullName}", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a household and its associated persons from Firestore,
     * and writes tombstones to prevent resurrection during bidirectional sync.
     * Uses chunking to stay well within Firestore's 500 write limit per batch.
     */
    private suspend fun transactionalDelete(
        collection: String,
        uuid: String,
        type: String
    ) {
        val firestore = getFirestore()
        val ref = firestore.collection(collection).document(uuid)
        val tombstoneRef = firestore.collection(COLLECTION_TOMBSTONES).document(type + "_" + uuid)
        firestore.runTransaction { tx ->
            val remote = tx.get(ref)
            val remoteVersion = remote.getLong("version") ?: 0L
            val tombstone = tx.get(tombstoneRef)
            val tombstoneVersion = tombstone.getLong("version") ?: 0L
            val next = maxOf(remoteVersion, tombstoneVersion) + 1L
            val now = System.currentTimeMillis()
            tx.set(
                tombstoneRef,
                mapOf(
                    "uuid" to uuid,
                    "type" to type,
                    "deletedAt" to now,
                    "serverUpdatedAt" to now,
                    "version" to next,
                    "updatedFrom" to "android",
                    "isDeleted" to true
                ),
                SetOptions.merge()
            )
            if (remote.exists()) tx.delete(ref)
            null
        }.await()
    }

    suspend fun deleteHouseholdFromFirestore(householdUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedFirebaseUser()
            val firestore = getFirestore()
            val personDocs = firestore.collection(COLLECTION_PERSONS)
                .whereEqualTo("householdUuid", householdUuid)
                .get().await()

            transactionalDelete(COLLECTION_HOUSEHOLDS, householdUuid, "household")
            for (doc in personDocs.documents) {
                val uuid = doc.getString("personUuid") ?: doc.id
                transactionalDelete(COLLECTION_PERSONS, uuid, "person")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete household $householdUuid", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a person from Firestore by UUID and writes a tombstone atomically.
     */
    open suspend fun deletePersonFromFirestore(personUuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedFirebaseUser()
            transactionalDelete(COLLECTION_PERSONS, personUuid, "person")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete person $personUuid from Firestore", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // FIRESTORE -> ROOM (Download / Restore)
    // =========================================================================

    /**
     * Fetches all registered data from Cloud Firestore and updates the local Room database,
     * skipping any records marked with deletion tombstones and strictly using UUID matching.
     */
    suspend fun syncFirestoreToRoom(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            requireAuthenticatedFirebaseUser()
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

            // Remove stale local Room records that have been deleted in Cloud
            val allLocalHouseholdsToDelete = repository.getAllHouseholds().filter { deletedUuids.contains(it.householdUuid) }
            for (h in allLocalHouseholdsToDelete) {
                repository.deleteHouseholdFromCloudRecovery(h)
            }
            val allLocalPersonsToDelete = repository.getAllPersonsList().filter { deletedUuids.contains(it.personUuid) }
            for (p in allLocalPersonsToDelete) {
                repository.deletePersonFromCloudRecovery(p)
            }

            val householdDocs = firestore.collection(COLLECTION_HOUSEHOLDS).get().await()
            val personDocs = firestore.collection(COLLECTION_PERSONS).get().await()

            _syncState.value = SyncState.Syncing("กำลังนำเข้าข้อมูล เข้าสู่ Room...")

            var householdsImported = 0
            var personsImported = 0

            // 1. Process Households (Strict UUID matching & Tombstone filtering)
            for (doc in householdDocs.documents) {
                val household = docToHousehold(doc) ?: continue
                if (deletedUuids.contains(household.householdUuid)) {
                    continue
                }

                val existing = repository.getHouseholdByUuid(household.householdUuid)

                if (existing != null) {
                    if (shouldAcceptRemote(localMetadata(existing), localMetadata(household))) {
                        repository.updateHousehold(household.copy(id = existing.id))
                        householdsImported++
                    }
                } else {
                    repository.insertHousehold(household.copy(id = 0))
                    householdsImported++
                }
            }

            // 2. Process Persons (Strict UUID matching & Tombstone filtering)
            val allLocalHouseholds = repository.getAllHouseholds()
            val householdByUuid = allLocalHouseholds.associateBy { it.householdUuid }

            for (doc in personDocs.documents) {
                val personUuid = doc.getString("personUuid") ?: doc.id
                if (deletedUuids.contains(personUuid)) {
                    continue
                }

                val parentHouseholdUuid = doc.getString("householdUuid") ?: ""
                val localHousehold = householdByUuid[parentHouseholdUuid]

                if (localHousehold == null) {
                    Log.w(TAG, "Skipping person $personUuid: parent household not found (UUID: $parentHouseholdUuid)")
                    continue
                }

                val person = docToPerson(doc, localHousehold.id) ?: continue
                val existing = repository.getPersonByUuid(person.personUuid)

                if (existing != null) {
                    if (shouldAcceptRemote(localMetadata(existing), localMetadata(person))) {
                        repository.update(person.copy(id = existing.id, householdId = localHousehold.id))
                        personsImported++
                    }
                } else {
                    repository.insert(person.copy(id = 0, householdId = localHousehold.id))
                    personsImported++
                }
            }

            val result = SyncResult(
                householdsSynced = householdsImported,
                personsSynced = personsImported,
                message = "นำเข้าข้อมูลจาก Firestore สำเร็จ ($householdsImported ครัวเรือน, $personsImported คน)"
            )
            _syncState.value = SyncState.Success(result)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing Firestore to Room", e)
            val errorMsg = e.message ?: "เกิดข้อผิดพลาดในการดึงข้อมูลจาก Firestore"
            _syncState.value = SyncState.Error(errorMsg, e)
            Result.failure(e)
        }
    }

    /**
     * Bidirectional synchronization: Pulls cloud records to Room, then pushes local records to Firestore.
     */
    suspend fun bidirectionalSync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing("เริ่มการซิงค์แบบ 2 ทาง (Pull & Push)...")
            val pullResult = syncFirestoreToRoom()
            if (pullResult.isFailure) {
                return@withContext pullResult
            }
            val pushResult = syncRoomToFirestore()
            pushResult
        } catch (e: Exception) {
            Log.e(TAG, "Error during bidirectional sync", e)
            Result.failure(e)
        }
    }

    // =========================================================================
    // MAPPERS & UTILITIES
    // =========================================================================

    private fun householdToMap(household: Household): Map<String, Any?> {
        return mapOf(
            "householdUuid" to household.householdUuid,
            "houseNo" to household.houseNo,
            "villageNo" to household.villageNo,
            "subdistrict" to household.subdistrict,
            "district" to household.district,
            "province" to household.province,
            "latitude" to household.latitude,
            "longitude" to household.longitude,
            "locationAccuracy" to household.locationAccuracy?.toDouble(),
            "locationCapturedAt" to household.locationCapturedAt,
            "locationProvider" to household.locationProvider,
            "dataStatus" to household.dataStatus.name,
            "updatedAt" to household.lastModified,
            "serverUpdatedAt" to (household.serverUpdatedAt ?: household.lastModified),
            "version" to household.version,
            "updatedBy" to household.updatedBy,
            "updatedFrom" to (household.updatedFrom ?: "android"),
            "isDeleted" to household.isDeleted
        )
    }

    private fun personToMap(person: Person, householdUuid: String, householdHouseNo: String): Map<String, Any?> {
        return mapOf(
            "personUuid" to person.personUuid,
            "householdUuid" to householdUuid,
            "householdHouseNo" to householdHouseNo,
            "nationalId" to person.nationalId,
            "fullName" to person.fullName,
            "gender" to person.gender.name,
            "birthDate" to person.birthDate?.toString(),
            "isBirthYearOnly" to person.isBirthYearOnly,
            "houseStatus" to person.houseStatus.name,
            "personStatus" to person.personStatus.name,
            "dataStatus" to person.dataStatus.name,
            "updatedAt" to person.lastModified,
            "serverUpdatedAt" to (person.serverUpdatedAt ?: person.lastModified),
            "version" to person.version,
            "updatedBy" to person.updatedBy,
            "updatedFrom" to (person.updatedFrom ?: "android"),
            "isDeleted" to person.isDeleted
        )
    }

    private fun docToHousehold(doc: DocumentSnapshot): Household? {
        val houseNo = doc.getString("houseNo") ?: return null
        val uuid = doc.getString("householdUuid") ?: doc.id

        val lat = doc.getDouble("latitude")
        val lon = doc.getDouble("longitude")
        val accuracy = doc.getDouble("locationAccuracy")?.toFloat()
        val capturedAt = doc.getLong("locationCapturedAt")
        val provider = doc.getString("locationProvider")

        val statusStr = doc.getString("dataStatus")
        val dataStatus = statusStr?.let {
            try { DataStatus.valueOf(it) } catch (e: Exception) { DataStatus.NEEDS_REVIEW }
        } ?: DataStatus.NEEDS_REVIEW

        val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        val serverUpdatedAt = doc.getLong("serverUpdatedAt")
        val version = doc.getLong("version") ?: 0L
        val updatedBy = doc.getString("updatedBy")
        val updatedFrom = doc.getString("updatedFrom")
        val isDeleted = doc.getBoolean("isDeleted") == true

        return Household(
            id = 0,
            householdUuid = uuid,
            houseNo = houseNo,
            villageNo = doc.getString("villageNo") ?: "",
            subdistrict = doc.getString("subdistrict") ?: "",
            district = doc.getString("district") ?: "",
            province = doc.getString("province") ?: "",
            latitude = lat,
            longitude = lon,
            locationAccuracy = accuracy,
            locationCapturedAt = capturedAt,
            locationProvider = provider,
            dataStatus = dataStatus,
            lastModified = updatedAt,
            serverUpdatedAt = serverUpdatedAt,
            version = version,
            updatedBy = updatedBy,
            updatedFrom = updatedFrom,
            isDeleted = isDeleted
        )
    }

    private fun docToPerson(doc: DocumentSnapshot, localHouseholdId: Long): Person? {
        val fullName = doc.getString("fullName") ?: return null
        val uuid = doc.getString("personUuid") ?: doc.id
        val nationalId = doc.getString("nationalId")

        val genderStr = doc.getString("gender")
        val gender = genderStr?.let {
            try { Gender.valueOf(it) } catch (e: Exception) { Gender.MALE }
        } ?: Gender.MALE

        val birthDateStr = doc.getString("birthDate")
        val birthDate = birthDateStr?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }
        val isBirthYearOnly = doc.getBoolean("isBirthYearOnly") ?: false

        val houseStatusStr = doc.getString("houseStatus")
        val houseStatus = houseStatusStr?.let {
            try { HouseholdRole.valueOf(it) } catch (e: Exception) { HouseholdRole.RESIDENT }
        } ?: HouseholdRole.RESIDENT

        val personStatusStr = doc.getString("personStatus")
        val personStatus = personStatusStr?.let {
            try { PersonStatus.valueOf(it) } catch (e: Exception) { PersonStatus.ALIVE }
        } ?: PersonStatus.ALIVE

        val dataStatusStr = doc.getString("dataStatus")
        val dataStatus = dataStatusStr?.let {
            try { DataStatus.valueOf(it) } catch (e: Exception) { DataStatus.NEEDS_REVIEW }
        } ?: DataStatus.NEEDS_REVIEW

        val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        val serverUpdatedAt = doc.getLong("serverUpdatedAt")
        val version = doc.getLong("version") ?: 0L
        val updatedBy = doc.getString("updatedBy")
        val updatedFrom = doc.getString("updatedFrom")
        val isDeleted = doc.getBoolean("isDeleted") == true

        return Person(
            id = 0,
            personUuid = uuid,
            householdId = localHouseholdId,
            nationalId = nationalId,
            fullName = fullName,
            gender = gender,
            birthDate = birthDate,
            isBirthYearOnly = isBirthYearOnly,
            houseStatus = houseStatus,
            personStatus = personStatus,
            dataStatus = dataStatus,
            lastModified = updatedAt,
            serverUpdatedAt = serverUpdatedAt,
            version = version,
            updatedBy = updatedBy,
            updatedFrom = updatedFrom,
            isDeleted = isDeleted
        )
    }
}
