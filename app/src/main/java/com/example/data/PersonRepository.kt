package com.example.data

import android.util.Log
import com.example.data.backup.BackupHistoryRecord
import com.example.data.backup.BackupDeletionRecord
import com.example.data.backup.BackupPayload
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

import androidx.room.withTransaction

class LocalDateAdapter {
    @ToJson
    fun toJson(value: LocalDate?): String? = value?.toString()

    @FromJson
    fun fromJson(value: String?): LocalDate? = value?.let {
        try { LocalDate.parse(it) } catch (e: Exception) { null }
    }
}

class PersonRepository(
    private val db: AppDatabase,
    private val personDao: PersonDao,
    private val householdDao: HouseholdDao,
    private val personHistoryDao: PersonHistoryDao,
    private val syncDeletionDao: SyncDeletionDao = db.syncDeletionDao()
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .add(LocalDateAdapter())
        .build()
    private val personAdapter = moshi.adapter(Person::class.java)

    val allPersons: Flow<List<Person>> = personDao.getAllPersons()
    val allHouseholdsWithPersons: Flow<List<HouseholdWithPersons>> = householdDao.getHouseholdsWithPersons()
    val houseSummary: Flow<List<HouseSummary>> = householdDao.getHouseSummary()
    
    val totalPersonsCount: Flow<Int> = personDao.getTotalPersonsCount()
    val totalHouseholdsCount: Flow<Int> = householdDao.getTotalHouseholdsCount()

    // Household Operations
    suspend fun insertHousehold(household: Household): Long {
        return db.withTransaction {
            syncDeletionDao.delete("HOUSEHOLD", household.householdUuid)
            householdDao.insert(household)
        }
    }

    suspend fun updateHousehold(household: Household) {
        db.withTransaction {
            syncDeletionDao.delete("HOUSEHOLD", household.householdUuid)
            householdDao.update(household)
        }
    }

    suspend fun deleteHousehold(household: Household): Result<Unit> {
        return try {
            db.withTransaction {
                val persons = personDao.getPersonsByHouseholdIdList(household.id)
                householdDao.delete(household)
                syncDeletionDao.upsert(SyncDeletion(entityType = "HOUSEHOLD", entityUuid = household.householdUuid, deletedAt = System.currentTimeMillis()))
                persons.forEach { person ->
                    syncDeletionDao.upsert(SyncDeletion(entityType = "PERSON", entityUuid = person.personUuid, deletedAt = System.currentTimeMillis()))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PersonRepository", "Failed to delete household id: ${household.id}", e)
            Result.failure(e)
        }
    }

    suspend fun getHouseholdById(id: Long): Household? {
        return householdDao.getHouseholdById(id)
    }
    
    suspend fun getHouseholdByNo(houseNo: String): Household? {
        return householdDao.getHouseholdByNo(houseNo)
    }

    suspend fun getHouseholdByUuid(uuid: String): Household? {
        return householdDao.getHouseholdByUuid(uuid)
    }
    
    fun getHouseholdWithPersonsById(id: Long): Flow<HouseholdWithPersons?> {
        return householdDao.getHouseholdWithPersonsById(id)
    }

    // Person Operations
    suspend fun insert(person: Person) {
        db.withTransaction {
            syncDeletionDao.delete("PERSON", person.personUuid)
            val newId = personDao.insertPerson(person)
            val insertedPerson = person.copy(id = newId)
            personHistoryDao.insert(
                PersonHistory(
                    personId = newId,
                    action = "CREATE",
                    oldValue = null,
                    newValue = personAdapter.toJson(insertedPerson)
                )
            )
        }
    }

    suspend fun update(person: Person) {
        db.withTransaction {
            val oldPerson = personDao.getPersonById(person.id)
            syncDeletionDao.delete("PERSON", person.personUuid)
            personDao.updatePerson(person)
            personHistoryDao.insert(
                PersonHistory(
                    personId = person.id,
                    action = "UPDATE",
                    oldValue = oldPerson?.let { personAdapter.toJson(it) },
                    newValue = personAdapter.toJson(person)
                )
            )
        }
    }

    suspend fun delete(person: Person) {
        db.withTransaction {
            val oldPerson = personDao.getPersonById(person.id)
            personDao.deletePerson(person)
            syncDeletionDao.upsert(SyncDeletion(entityType = "PERSON", entityUuid = person.personUuid, deletedAt = System.currentTimeMillis()))
            personHistoryDao.insert(
                PersonHistory(
                    personId = person.id,
                    action = "DELETE",
                    oldValue = oldPerson?.let { personAdapter.toJson(it) },
                    newValue = null
                )
            )
        }
    }
    
    suspend fun getPersonById(id: Long): Person? {
        return personDao.getPersonById(id)
    }
    
    suspend fun getPersonByNationalId(nationalId: String): Person? {
        return personDao.getPersonByNationalId(nationalId)
    }

    suspend fun getPersonByUuid(uuid: String): Person? {
        return personDao.getPersonByUuid(uuid)
    }
    
    fun getHistoryForPerson(personId: Long): Flow<List<PersonHistory>> {
        return personHistoryDao.getHistoryForPerson(personId)
    }

    suspend fun getAllHouseholds(): List<Household> = householdDao.getAllHouseholds()
    suspend fun getAllPersonsList(): List<Person> = personDao.getAllPersonsList()

    suspend fun getPendingDeletions(): List<SyncDeletion> = syncDeletionDao.getAll()

    suspend fun clearPendingDeletion(deletion: SyncDeletion) {
        syncDeletionDao.delete(deletion.entityType, deletion.entityUuid)
    }

    suspend fun clearAllPendingDeletions() {
        syncDeletionDao.deleteAll()
    }

    suspend fun createBackupPayload(): BackupPayload {
        val households = householdDao.getAllHouseholds()
        val persons = personDao.getAllPersonsList()
        val personUuidById = persons.associate { it.id to it.personUuid }
        val history = personHistoryDao.getAllHistory().first().mapNotNull { item ->
            val uuid = personUuidById[item.personId] ?: return@mapNotNull null
            BackupHistoryRecord(
                personUuid = uuid,
                action = item.action,
                oldValue = item.oldValue,
                newValue = item.newValue,
                timestamp = item.timestamp,
                operatorId = item.operatorId,
                operatorName = item.operatorName,
                role = item.role,
                deviceId = item.deviceId,
                source = item.source
            )
        }
        val deletions = syncDeletionDao.getAll().map {
            BackupDeletionRecord(it.entityType, it.entityUuid, it.deletedAt)
        }
        return BackupPayload(households = households, persons = persons, history = history, deletions = deletions)
    }

    suspend fun restoreBackupPayload(payload: BackupPayload) {
        db.withTransaction {
            personHistoryDao.deleteAll()
            personDao.deleteAll()
            householdDao.deleteAll()
            syncDeletionDao.deleteAll()

            val householdIdByUuid = mutableMapOf<String, Long>()
            payload.households.forEach { household ->
                val newId = householdDao.insert(household.copy(id = 0))
                householdIdByUuid[household.householdUuid] = newId
            }

            val personIdByUuid = mutableMapOf<String, Long>()
            payload.persons.forEach { person ->
                val originalHouseholdUuid = payload.households
                    .firstOrNull { it.id == person.householdId }
                    ?.householdUuid
                    ?: throw IllegalStateException("Backup ไม่พบครัวเรือนของบุคคล " + person.personUuid)
                val householdId = householdIdByUuid[originalHouseholdUuid]
                    ?: throw IllegalStateException("Backup ไม่สามารถสร้างครัวเรือน " + originalHouseholdUuid + " ได้")
                val newId = personDao.insertPerson(person.copy(id = 0, householdId = householdId))
                personIdByUuid[person.personUuid] = newId
            }

            val restoredHistory = payload.history.mapNotNull { item ->
                val personId = personIdByUuid[item.personUuid] ?: return@mapNotNull null
                PersonHistory(
                    personId = personId,
                    action = item.action,
                    oldValue = item.oldValue,
                    newValue = item.newValue,
                    timestamp = item.timestamp,
                    operatorId = item.operatorId,
                    operatorName = item.operatorName,
                    role = item.role,
                    deviceId = item.deviceId,
                    source = item.source
                )
            }
            if (restoredHistory.isNotEmpty()) personHistoryDao.insertAll(restoredHistory)

            payload.deletions.forEach { deletion ->
                syncDeletionDao.upsert(
                    SyncDeletion(
                        entityType = deletion.entityType,
                        entityUuid = deletion.entityUuid,
                        deletedAt = deletion.deletedAt
                    )
                )
            }
        }
    }
}
