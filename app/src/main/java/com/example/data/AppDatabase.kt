package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Person::class, Household::class, PersonHistory::class, SyncDeletion::class, CommunityLocationReference::class], version = 11, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun householdDao(): HouseholdDao
    abstract fun personHistoryDao(): PersonHistoryDao
    abstract fun syncDeletionDao(): SyncDeletionDao
    abstract fun communityLocationReferenceDao(): CommunityLocationReferenceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) {} }
        val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) {} }
        val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) {} }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE households ADD COLUMN villageNo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE households ADD COLUMN subdistrict TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE households ADD COLUMN district TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE households ADD COLUMN province TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS persons_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, householdId INTEGER NOT NULL, nationalId TEXT NOT NULL, fullName TEXT NOT NULL, gender TEXT NOT NULL, birthDate TEXT, houseStatus TEXT NOT NULL, personStatus TEXT NOT NULL, dataStatus TEXT NOT NULL, FOREIGN KEY(householdId) REFERENCES households(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("INSERT INTO persons_new (id, householdId, nationalId, fullName, gender, birthDate, houseStatus, personStatus, dataStatus) SELECT id, householdId, nationalId, fullName, gender, birthDate, houseStatus, personStatus, dataStatus FROM persons")
                db.execSQL("DROP TABLE persons")
                db.execSQL("ALTER TABLE persons_new RENAME TO persons")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_households_houseNo_villageNo_subdistrict_district_province ON households (houseNo, villageNo, subdistrict, district, province)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_persons_householdId ON persons (householdId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_persons_nationalId ON persons (nationalId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE households ADD COLUMN householdUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP INDEX IF EXISTS index_households_houseNo_villageNo_subdistrict_district_province")
                db.execSQL("UPDATE households SET householdUuid = lower(hex(randomblob(16)))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_households_householdUuid ON households (householdUuid)")
                db.execSQL("ALTER TABLE persons ADD COLUMN personUuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE persons SET personUuid = lower(hex(randomblob(16)))")
                db.execSQL("ALTER TABLE persons ADD COLUMN isBirthYearOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS person_history_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, personId INTEGER NOT NULL, action TEXT NOT NULL, oldValue TEXT, newValue TEXT, timestamp INTEGER NOT NULL, operatorId TEXT NOT NULL, operatorName TEXT NOT NULL, role TEXT NOT NULL, deviceId TEXT NOT NULL, source TEXT NOT NULL)")
                db.execSQL("INSERT INTO person_history_new (id, personId, action, oldValue, newValue, timestamp, operatorId, operatorName, role, deviceId, source) SELECT id, personId, action, oldValue, newValue, timestamp, 'SYSTEM', 'System', 'SYSTEM', 'local', 'SYSTEM' FROM person_history")
                db.execSQL("DROP TABLE person_history")
                db.execSQL("ALTER TABLE person_history_new RENAME TO person_history")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE persons_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        personUuid TEXT NOT NULL DEFAULT '',
                        householdId INTEGER NOT NULL,
                        nationalId TEXT,
                        fullName TEXT NOT NULL,
                        gender TEXT NOT NULL,
                        birthDate TEXT,
                        isBirthYearOnly INTEGER NOT NULL DEFAULT 0,
                        houseStatus TEXT NOT NULL,
                        personStatus TEXT NOT NULL,
                        dataStatus TEXT NOT NULL,
                        FOREIGN KEY(householdId) REFERENCES households(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    INSERT INTO persons_new (id, personUuid, householdId, nationalId, fullName, gender, birthDate, isBirthYearOnly, houseStatus, personStatus, dataStatus)
                    SELECT id, CASE WHEN personUuid IS NULL OR personUuid = '' THEN lower(hex(randomblob(16))) ELSE personUuid END,
                           householdId, CASE WHEN nationalId = '' THEN NULL ELSE nationalId END,
                           fullName, gender, birthDate, isBirthYearOnly, houseStatus, personStatus, dataStatus
                    FROM persons
                """)
                db.execSQL("DROP TABLE persons")
                db.execSQL("ALTER TABLE persons_new RENAME TO persons")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_persons_householdId ON persons (householdId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_persons_nationalId ON persons (nationalId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_persons_personUuid ON persons (personUuid)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_households_householdUuid ON households (householdUuid)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()
                db.execSQL("ALTER TABLE persons ADD COLUMN lastModified INTEGER NOT NULL DEFAULT $currentTime")
                db.execSQL("ALTER TABLE households ADD COLUMN lastModified INTEGER NOT NULL DEFAULT $currentTime")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE persons ADD COLUMN serverUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE persons ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE persons ADD COLUMN updatedBy TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN updatedFrom TEXT")
                db.execSQL("ALTER TABLE persons ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE households ADD COLUMN serverUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE households ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE households ADD COLUMN updatedBy TEXT")
                db.execSQL("ALTER TABLE households ADD COLUMN updatedFrom TEXT")
                db.execSQL("ALTER TABLE households ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }
    val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_deletion_journal (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityUuid TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_deletion_journal_entityType_entityUuid ON sync_deletion_journal (entityType, entityUuid)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS community_location_reference (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pcode TEXT NOT NULL,
                        pname TEXT NOT NULL,
                        acode TEXT NOT NULL,
                        aname TEXT NOT NULL,
                        tcode TEXT NOT NULL,
                        tname TEXT NOT NULL,
                        mcode TEXT NOT NULL,
                        mname TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        femaleCount INTEGER,
                        maleCount INTEGER,
                        populationTotal INTEGER,
                        householdTotal INTEGER,
                        localAuthority TEXT,
                        localAuthorityName TEXT,
                        roadName TEXT,
                        roadNumber TEXT,
                        roadDistance REAL,
                        riverName TEXT,
                        seaName TEXT,
                        lagoonName TEXT,
                        swampName TEXT,
                        mountainName TEXT,
                        borderName1 TEXT,
                        borderDistance1 REAL,
                        borderName2 TEXT,
                        borderDistance2 REAL,
                        housingTotal INTEGER,
                        condosTotal INTEGER,
                        sourceVersion TEXT NOT NULL DEFAULT '',
                        importedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_community_location_reference_mcode ON community_location_reference (mcode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_community_location_reference_tcode ON community_location_reference (tcode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_community_location_reference_acode ON community_location_reference (acode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_community_location_reference_pcode ON community_location_reference (pcode)")
            }
        }
    }
}

