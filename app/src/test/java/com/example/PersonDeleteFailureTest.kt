package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.sync.RoomFirestoreSyncHelper
import com.example.viewmodel.PersonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonDeleteFailureTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository
    private lateinit var context: Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PersonRepository(db, db.personDao(), db.householdDao(), db.personHistoryDao())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun testFirestoreDeleteSuccessDeletesLocalRecord() = runTest {
        val household = Household(
            householdUuid = "H-DEL-SUCC-001",
            houseNo = "111/1",
            villageNo = "1",
            subdistrict = "Sub",
            district = "Dist",
            province = "Prov"
        )
        val householdId = repository.insertHousehold(household)
        val person = Person(
            personUuid = "P-DEL-SUCC-001",
            householdId = householdId,
            fullName = "นาย Success",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
            houseStatus = HouseholdRole.RESIDENT,
            personStatus = PersonStatus.ALIVE
        )
        repository.insert(person)
        val insertedPerson = repository.getPersonByUuid("P-DEL-SUCC-001")
        assertNotNull(insertedPerson)

        val successSyncHelper = object : RoomFirestoreSyncHelper(context, repository, { null }) {
            override fun isFirebaseConfigured(): Boolean = true
            override suspend fun deletePersonFromFirestore(personUuid: String): Result<Unit> {
                return Result.success(Unit)
            }
        }
        val excelImportUseCase = com.example.domain.ExcelImportUseCase(db)
        val viewModel = PersonViewModel(repository, excelImportUseCase, successSyncHelper)

        var resultSuccess: Boolean? = null
        var resultMessage: String? = null

        viewModel.delete(insertedPerson!!) { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        advanceUntilIdle()

        assertTrue(resultSuccess == true)
        assertNull(repository.getPersonByUuid("P-DEL-SUCC-001"))
    }

    @Test
    fun testFirestoreDeleteFailureStillDeletesLocalRecord() = runTest {
        val household = Household(
            householdUuid = "H-DEL-FAIL-001",
            houseNo = "999/2",
            villageNo = "1",
            subdistrict = "Sub",
            district = "Dist",
            province = "Prov"
        )
        val householdId = repository.insertHousehold(household)
        val person = Person(
            personUuid = "P-DEL-FAIL-001",
            householdId = householdId,
            fullName = "นาย Cloud Fail",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
            houseStatus = HouseholdRole.RESIDENT,
            personStatus = PersonStatus.ALIVE
        )
        repository.insert(person)
        val insertedPerson = repository.getPersonByUuid("P-DEL-FAIL-001")
        assertNotNull(insertedPerson)

        val failingSyncHelper = object : RoomFirestoreSyncHelper(context, repository, { null }) {
            override fun isFirebaseConfigured(): Boolean = true
            override suspend fun deletePersonFromFirestore(personUuid: String): Result<Unit> {
                return Result.failure(Exception("Simulated Cloud Network Error"))
            }
        }
        val excelImportUseCase = com.example.domain.ExcelImportUseCase(db)
        val viewModel = PersonViewModel(repository, excelImportUseCase, failingSyncHelper)

        var resultSuccess: Boolean? = null
        var resultMessage: String? = null

        viewModel.delete(insertedPerson!!) { success, message ->
            resultSuccess = success
            resultMessage = message
        }

        advanceUntilIdle()

        // Local deletion succeeds even if cloud deletion fails
        assertTrue(resultSuccess == true)
        assertNotNull(resultMessage)
        assertTrue(resultMessage?.contains("Simulated Cloud Network Error") == true)

        // Local Room record must be deleted
        val afterPerson = repository.getPersonByUuid("P-DEL-FAIL-001")
        assertNull("Person MUST be deleted from Room locally even if Cloud sync fails", afterPerson)
    }

    @Test
    fun testDeletedRecordCannotResurrectDuringSync() = runTest {
        val household = Household(
            householdUuid = "H-RESURRECT-001",
            houseNo = "222/2",
            villageNo = "1",
            subdistrict = "Sub",
            district = "Dist",
            province = "Prov"
        )
        val householdId = repository.insertHousehold(household)
        val person = Person(
            personUuid = "P-RESURRECT-001",
            householdId = householdId,
            fullName = "นาย No Resurrect",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
            houseStatus = HouseholdRole.RESIDENT,
            personStatus = PersonStatus.ALIVE
        )
        repository.insert(person)
        val insertedPerson = repository.getPersonByUuid("P-RESURRECT-001")
        assertNotNull(insertedPerson)

        // Delete person locally and queue deletion
        repository.delete(insertedPerson!!)
        assertNull(repository.getPersonByUuid("P-RESURRECT-001"))

        val syncHelper = RoomFirestoreSyncHelper(context, repository, { null })
        syncHelper.queueDeletion("P-RESURRECT-001", "person")

        // Verify person does not resurrect when getting all persons list
        val personsList = repository.getAllPersonsList()
        assertTrue(personsList.none { it.personUuid == "P-RESURRECT-001" })
    }
}
