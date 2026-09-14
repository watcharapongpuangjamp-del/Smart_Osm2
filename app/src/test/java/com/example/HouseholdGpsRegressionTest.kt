package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DataStatus
import com.example.data.Household
import com.example.data.PersonRepository
import com.example.data.firestore.FirestoreManager
import com.example.data.sync.RoomFirestoreSyncHelper
import com.example.viewmodel.PersonViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HouseholdGpsRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository
    private lateinit var viewModel: PersonViewModel
    private lateinit var syncHelper: RoomFirestoreSyncHelper
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Use in-memory database
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            
        FirestoreManager.resetForTesting(null)
        repository = PersonRepository(db, db.personDao(), db.householdDao(), db.personHistoryDao())
        syncHelper = RoomFirestoreSyncHelper(context, repository, { null })
        val excelUseCase = com.example.domain.ExcelImportUseCase(db)
        viewModel = PersonViewModel(repository, excelUseCase, syncHelper)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun existingHousehold_addGps_save_reload_gpsPersistsAndOriginalFieldsUnchanged() = runTest {
        // 1. Create existing household with specific original fields
        val originalUuid = java.util.UUID.randomUUID().toString()
        val originalHousehold = Household(
            householdUuid = originalUuid,
            houseNo = "99/9",
            villageNo = "หมู่ 1",
            subdistrict = "ตำบล A",
            district = "อำเภอ B",
            province = "จังหวัด C",
            dataStatus = DataStatus.VERIFIED,
            lastModified = 1000L,
            latitude = null,
            longitude = null
        )
        
        val householdId = repository.insertHousehold(originalHousehold)
        
        // 2. Add GPS and save via ViewModel logic
        val loadedHousehold = repository.getHouseholdById(householdId)
        assertNotNull(loadedHousehold)
        
        // Simulating the update logic in HouseholdFormScreen + ViewModel
        val householdToSave = loadedHousehold!!.copy(
            latitude = 13.7563,
            longitude = 100.5018,
            locationAccuracy = 5.0f,
            locationCapturedAt = 2000L,
            locationProvider = "gps"
        )
        
        // Simulate what PersonViewModel.updateHousehold does
        val modifiedTimeBeforeUpdate = System.currentTimeMillis()
        repository.updateHousehold(householdToSave.copy(lastModified = System.currentTimeMillis()))
        
        // 3. Reload from DB
        val reloadedHousehold = repository.getHouseholdById(householdId)
        assertNotNull(reloadedHousehold)
        
        // 4. Assertions
        // Original fields unchanged
        assertEquals("UUID must remain unchanged", originalUuid, reloadedHousehold!!.householdUuid)
        assertEquals("HouseNo must remain unchanged", "99/9", reloadedHousehold.houseNo)
        assertEquals("VillageNo must remain unchanged", "หมู่ 1", reloadedHousehold.villageNo)
        assertEquals("Subdistrict must remain unchanged", "ตำบล A", reloadedHousehold.subdistrict)
        assertEquals("District must remain unchanged", "อำเภอ B", reloadedHousehold.district)
        assertEquals("Province must remain unchanged", "จังหวัด C", reloadedHousehold.province)
        assertEquals("DataStatus must remain unchanged", DataStatus.VERIFIED, reloadedHousehold.dataStatus)
        
        // GPS fields updated
        assertEquals("Latitude must be updated", 13.7563, reloadedHousehold.latitude)
        assertEquals("Longitude must be updated", 100.5018, reloadedHousehold.longitude)
        assertEquals("LocationAccuracy must be updated", 5.0f, reloadedHousehold.locationAccuracy)
        assertEquals("LocationCapturedAt must be updated", 2000L, reloadedHousehold.locationCapturedAt)
        assertEquals("LocationProvider must be updated", "gps", reloadedHousehold.locationProvider)
        
        // lastModified should be updated by PersonViewModel behavior
        assertTrue("LastModified must be updated", reloadedHousehold.lastModified >= modifiedTimeBeforeUpdate)
    }
}
