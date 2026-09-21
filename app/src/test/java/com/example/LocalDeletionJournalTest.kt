package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDeletionJournalTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: PersonRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PersonRepository(db, db.personDao(), db.householdDao(), db.personHistoryDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun deletingPersonCreatesPendingJournalEntry() = runBlocking {
        val householdId = repository.insertHousehold(
            Household(
                householdUuid = "H-JOURNAL-1",
                houseNo = "1/1",
                villageNo = "1",
                subdistrict = "Sub",
                district = "Dist",
                province = "Prov"
            )
        )
        repository.insert(
            Person(
                personUuid = "P-JOURNAL-1",
                householdId = householdId,
                fullName = "นาย ทดสอบ",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
                houseStatus = HouseholdRole.RESIDENT,
                personStatus = PersonStatus.ALIVE
            )
        )

        val person = repository.getPersonByUuid("P-JOURNAL-1")!!
        repository.delete(person)

        val pending = repository.getPendingDeletions()
        assertEquals(1, pending.size)
        assertEquals("PERSON", pending[0].entityType)
        assertEquals("P-JOURNAL-1", pending[0].entityUuid)
        assertNull(repository.getPersonByUuid("P-JOURNAL-1"))
    }

    @Test
    fun recreatingSamePersonClearsOldDeletionJournal() = runBlocking {
        val householdId = repository.insertHousehold(
            Household(
                householdUuid = "H-JOURNAL-2",
                houseNo = "2/1",
                villageNo = "1",
                subdistrict = "Sub",
                district = "Dist",
                province = "Prov"
            )
        )
        val person = Person(
            personUuid = "P-JOURNAL-2",
            householdId = householdId,
            fullName = "นาย ทดสอบ 2",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1991, 1, 1),
            houseStatus = HouseholdRole.RESIDENT,
            personStatus = PersonStatus.ALIVE
        )
        repository.insert(person)
        repository.delete(repository.getPersonByUuid("P-JOURNAL-2")!!)

        assertEquals(1, repository.getPendingDeletions().size)

        repository.insert(person.copy(id = 0))

        assertTrue(repository.getPendingDeletions().isEmpty())
        assertNotNull(repository.getPersonByUuid("P-JOURNAL-2"))
    }

    @Test
    fun deletingHouseholdJournalsHouseholdAndAllMembers() = runBlocking {
        val householdId = repository.insertHousehold(
            Household(
                householdUuid = "H-JOURNAL-3",
                houseNo = "3/1",
                villageNo = "1",
                subdistrict = "Sub",
                district = "Dist",
                province = "Prov"
            )
        )
        repository.insert(
            Person(
                personUuid = "P-JOURNAL-3A",
                householdId = householdId,
                fullName = "นาย คนที่หนึ่ง",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1980, 1, 1),
                houseStatus = HouseholdRole.HEAD,
                personStatus = PersonStatus.ALIVE
            )
        )
        repository.insert(
            Person(
                personUuid = "P-JOURNAL-3B",
                householdId = householdId,
                fullName = "นาง คนที่สอง",
                gender = Gender.FEMALE,
                birthDate = LocalDate.of(1985, 1, 1),
                houseStatus = HouseholdRole.RESIDENT,
                personStatus = PersonStatus.ALIVE
            )
        )

        val household = repository.getHouseholdByUuid("H-JOURNAL-3")!!
        assertTrue(repository.deleteHousehold(household).isSuccess)

        val pending = repository.getPendingDeletions()
        assertEquals(3, pending.size)
        assertEquals(
            setOf(
                "HOUSEHOLD:H-JOURNAL-3",
                "PERSON:P-JOURNAL-3A",
                "PERSON:P-JOURNAL-3B"
            ),
            pending.map { "${it.entityType}:${it.entityUuid}" }.toSet()
        )
        assertNull(repository.getHouseholdByUuid("H-JOURNAL-3"))
    }
}
