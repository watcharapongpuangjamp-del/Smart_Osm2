package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.data.sync.RoomFirestoreSyncHelper
import com.example.viewmodel.PersonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
    fun testPersonDeleteCloudFailureKeepsLocalData() = runBlocking {
        // 1. Create Household and Person locally
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

        // 2. Initialize ViewModel with a Cloud helper that would fail if called.
        // Local CRUD must not depend on Cloud availability.
        val failingSyncHelper = object : RoomFirestoreSyncHelper(context, repository, { null }) {
            override fun isFirebaseConfigured(): Boolean = true
            override suspend fun deletePersonFromFirestore(personUuid: String): Result<Unit> {
                return Result.failure(Exception("Simulated Cloud Network Error"))
            }
        }

        val excelImportUseCase = com.example.domain.ExcelImportUseCase(db)
        val viewModel = PersonViewModel(repository, excelImportUseCase, failingSyncHelper)

        // 3. Act: delete locally; Cloud must not be required.
        var resultSuccess: Boolean? = null
        var resultMessage: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)

        viewModel.delete(insertedPerson!!) { success, message ->
            resultSuccess = success
            resultMessage = message
            latch.countDown()
        }

        // Wait for coroutines to complete
        testDispatcher.scheduler.advanceUntilIdle()
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
        testDispatcher.scheduler.advanceUntilIdle()

        // 5. Assert: local deletion succeeds without Cloud.
        assertEquals(true, resultSuccess)
        assertEquals(null, resultMessage)

        // 6. Assert: the local Room database is the source of truth.
        val afterPerson = repository.getPersonByUuid("P-DEL-FAIL-001")
        assertNull("Person should be deleted from Room independently of Cloud", afterPerson)
    }
}
