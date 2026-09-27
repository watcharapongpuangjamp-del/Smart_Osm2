package com.example

import com.example.data.DataStatus
import com.example.data.Household
import com.example.data.backup.BackupPayload
import com.example.data.backup.SecureBackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SecureBackupManagerTest {

    @Test
    fun backupRoundTripUsesCurrentRoomSchemaVersion() {
        val manager = SecureBackupManager()
        val household = Household(
            householdUuid = "H-BACKUP-001",
            houseNo = "1/1",
            villageNo = "8",
            subdistrict = "ป่าขะ",
            district = "บ้านนา",
            province = "นครนายก",
            dataStatus = DataStatus.VERIFIED
        )
        val payload = BackupPayload(
            households = listOf(household),
            persons = emptyList(),
            history = emptyList(),
            deletions = emptyList()
        )
        val output = ByteArrayOutputStream()
        val password = "SmartOsmTest".toCharArray()

        val info = manager.writeBackup(output, payload, password)
        val raw = output.toString(Charsets.UTF_8.name())
        assertTrue(raw.contains("\"databaseSchemaVersion\":11"))
        assertEquals(1, info.householdCount)

        val (readInfo, restored) = manager.readBackup(
            ByteArrayInputStream(output.toByteArray()),
            "SmartOsmTest".toCharArray()
        )
        assertEquals(info.backupId, readInfo.backupId)
        assertEquals(1, restored.households.size)
        assertEquals("H-BACKUP-001", restored.households.single().householdUuid)
    }
}
