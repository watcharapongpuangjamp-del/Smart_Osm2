package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.SyncState
import com.example.ui.components.ThemeQuickToggleButton
import com.example.ui.theme.EmeraldPrimary
import com.example.viewmodel.PersonViewModel
import com.example.domain.CommunityLocationReferenceImportUseCase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: PersonViewModel,
    onBack: () -> Unit,
    communityLocationImportUseCase: CommunityLocationReferenceImportUseCase
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val importScope = rememberCoroutineScope()

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val totalPersons by viewModel.totalPersonsCount.collectAsStateWithLifecycle()
    val totalHouseholds by viewModel.totalHouseholdsCount.collectAsStateWithLifecycle()

    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePreview by remember { mutableStateOf(false) }
    var backupAction by remember { mutableStateOf<String?>(null) }
    var backupInfo by remember { mutableStateOf<com.example.data.backup.BackupInfo?>(null) }
    var communityImportJson by remember { mutableStateOf<String?>(null) }
    var communityImportFileName by remember { mutableStateOf("") }
    var communityImportPreview by remember { mutableStateOf<com.example.domain.CommunityLocationImportReport?>(null) }
    var showCommunityImportPreview by remember { mutableStateOf(false) }
    var communityImportBusy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        uri?.let {
            viewModel.exportExcelData(context, it) { success, message ->
                isError = !success
                actionMessage = message
            }
        }
    }

    val secureBackupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            viewModel.exportSecureBackup(context, it, backupPassword) { success, message ->
                isError = !success
                actionMessage = message
                backupPassword = ""
            }
        }
    }

    val secureBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            backupUri = it
            backupAction = "restore"
            showBackupPasswordDialog = true
        }
    }
    
    val communityLocationJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            importScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
                        ?: throw IllegalStateException("ไม่สามารถอ่านไฟล์ JSON ได้")
                    val preview = communityLocationImportUseCase.previewJson(json, "file:${it.lastPathSegment ?: "json"}")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        communityImportJson = json
                        communityImportFileName = it.lastPathSegment ?: "community_location.json"
                        communityImportPreview = preview
                        showCommunityImportPreview = true
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        isError = true
                        actionMessage = "ตรวจสอบไฟล์ชุมชนไม่สำเร็จ: ${e.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "สำรองข้อมูลและซิงค์คลาวด์",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "กลับ", tint = Color.White)
                    }
                },
                actions = {
                    ThemeQuickToggleButton(iconTint = Color.White)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmeraldPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status & Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "สถานะฐานข้อมูลในเครื่อง (Room)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(containerColor = EmeraldPrimary) {
                            Text("พร้อมใช้งาน", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem(label = "ครัวเรือนทั้งหมด", count = "$totalHouseholds หลัง")
                        VerticalDivider(modifier = Modifier.height(30.dp))
                        StatItem(label = "ประชากรทั้งหมด", count = "$totalPersons คน")
                    }
                }
            }

            // Sync State Feedback Banner
            when (val state = syncState) {
                is SyncState.Syncing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text(text = state.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                is SyncState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Text(text = "ซิงค์สำเร็จ", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = state.result.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B5E20))
                        }
                    }
                }
                is SyncState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(text = "เกิดข้อผิดพลาดในการซิงค์", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                else -> {}
            }

            // Action Feedback Banner
            actionMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { actionMessage = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "ปิด")
                        }
                    }
                }
            }

            // Section 1: Local File Export
            Text(
                text = "1. การสำรองข้อมูลลงไฟล์ท้องถิ่น (Local Export)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ส่งออกข้อมูลทะเบียนประชากรและครัวเรือนทั้งหมดเป็นไฟล์ Excel (.xlsx) เพื่อเก็บไว้เป็นสำรองบนอุปกรณ์หรือแชร์ต่อ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { exportLauncher.launch("population_backup_${System.currentTimeMillis()}.xlsx") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ส่งออกฐานข้อมูลเป็นไฟล์ Excel (.xlsx)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "2. Local Backup แบบเข้ารหัส",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "สำรองฐานข้อมูลจากเครื่องเป็นไฟล์ Smart OSM ที่มี Identity และเข้ารหัส ไม่ต้องใช้ Google Login และไม่ใช่การ Sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { backupAction = "export"; showBackupPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("สร้าง Local Backup (.sosm)", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { secureBackupImportLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("นำเข้า / Restore จาก Local Backup", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Restore จะตรวจไฟล์และแสดงจำนวนข้อมูลก่อนเขียนทับฐานข้อมูลในเครื่อง",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 3: Community Location Reference Import
            Text(
                text = "3. ข้อมูลอ้างอิงชุมชน / หมู่บ้าน",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "นำเข้าไฟล์ JSON ข้อมูลอ้างอิงระดับหมู่บ้านเพื่อใช้เป็น Master Location/ข้อมูลประกอบ " +
                            "ระบบจะตรวจสอบก่อน และยังไม่เขียน Room จนกว่าจะกดยืนยัน"
                    )
                    Button(
                        onClick = {
                            communityLocationJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !communityImportBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("เลือกไฟล์ JSON และตรวจสอบ", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "การนำเข้าจะไม่แก้พิกัดหรือข้อมูล Household/Person",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 4: Cloud Firestore Redundancy & Sync
            Text(
                text = "4. Cloud Backup / Recovery",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cloud เป็นข้อมูลสำรองด้านข้างของ Local โดยการ Push จะส่งข้อมูลจาก Local ขึ้น Cloud เท่านั้น ส่วนการดึงจาก Cloud เป็น Recovery ที่ผู้ใช้สั่งเอง",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Push to Cloud Button
                    OutlinedButton(
                        onClick = {
                            viewModel.syncToFirestore { result ->
                                isError = result.isFailure
                                actionMessage = if (result.isSuccess) "อัปโหลดข้อมูลขึ้น Cloud สำเร็จ" else "อัปโหลดไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("สำรองข้อมูลขึ้นคลาวด์ (Push to Cloud)", fontWeight = FontWeight.Bold)
                    }

                    // Pull from Cloud Button
                    OutlinedButton(
                        onClick = {
                            viewModel.syncFromFirestore { result ->
                                isError = result.isFailure
                                actionMessage = if (result.isSuccess) "ดึงข้อมูลจาก Cloud สำเร็จ" else "ดึงข้อมูลไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary)
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("กู้คืนจาก Cloud (Explicit Recovery)", fontWeight = FontWeight.Bold)
                    }

                }
            }
        }
    }
    if (showCommunityImportPreview) {
        val preview = communityImportPreview
        AlertDialog(
            onDismissRequest = {
                if (!communityImportBusy) {
                    showCommunityImportPreview = false
                    communityImportJson = null
                    communityImportPreview = null
                }
            },
            title = { Text("ตรวจสอบข้อมูลอ้างอิงชุมชน") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(communityImportFileName, fontWeight = FontWeight.Bold)
                    preview?.let {
                        Text("รายการที่อ่านได้: ${it.parsed}")
                        Text("ผ่านการตรวจสอบ: ${it.accepted}")
                        Text("ถูกปฏิเสธ: ${it.rejected}")
                        Text("จังหวัด: ${it.provinces}  อำเภอ: ${it.districts}")
                        Text("ตำบล: ${it.subdistricts}  หมู่บ้าน: ${it.villages}")
                        Text("มีพิกัด: ${it.coordinates}")
                        if (it.warnings.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("คำเตือน ${it.warnings.size} รายการ", fontWeight = FontWeight.Bold)
                            it.warnings.take(8).forEach { warning ->
                                Text("• $warning", style = MaterialTheme.typography.bodySmall)
                            }
                            if (it.warnings.size > 8) {
                                Text("… และอีก ${it.warnings.size - 8} รายการ", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text(
                        "ยืนยันแล้วจึงจะล้างชุดข้อมูลอ้างอิงเดิมและแทนที่ด้วยชุดที่ผ่านการตรวจสอบทั้งหมด",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preview != null && preview.accepted > 0 && !communityImportBusy,
                    onClick = {
                        val json = communityImportJson ?: return@TextButton
                        communityImportBusy = true
                        importScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val result = communityLocationImportUseCase.importJson(
                                    json,
                                    "file:${communityImportFileName}"
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    communityImportBusy = false
                                    showCommunityImportPreview = false
                                    communityImportJson = null
                                    communityImportPreview = null
                                    actionMessage = "นำเข้าข้อมูลชุมชนสำเร็จ ${result.stored} หมู่บ้าน"
                                    isError = false
                                }
                            } catch (e: Exception) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    communityImportBusy = false
                                    isError = true
                                    actionMessage = "นำเข้าไม่สำเร็จ: ${e.message}"
                                }
                            }
                        }
                    }
                ) { Text(if (communityImportBusy) "กำลังนำเข้า..." else "ยืนยันนำเข้า") }
            },
            dismissButton = {
                TextButton(
                    enabled = !communityImportBusy,
                    onClick = {
                        showCommunityImportPreview = false
                        communityImportJson = null
                        communityImportPreview = null
                    }
                ) { Text("ยกเลิก") }
            }
        )
    }

    if (showBackupPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false; backupPassword = "" },
            title = { Text(if (backupAction == "export") "สร้าง Local Backup" else "เปิดไฟล์ Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("กรอกรหัสผ่านสำหรับไฟล์สำรองอย่างน้อย 8 ตัวอักษร")
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        singleLine = true,
                        label = { Text("รหัสผ่าน Backup") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassword.length >= 8,
                    onClick = {
                        showBackupPasswordDialog = false
                        if (backupAction == "export") {
                            secureBackupExportLauncher.launch("smartosm_backup_" + System.currentTimeMillis() + ".sosm")
                        } else {
                            val uri = backupUri
                            if (uri != null) {
                                viewModel.previewSecureBackup(context, uri, backupPassword) { success, info, message ->
                                    isError = !success
                                    actionMessage = message
                                    if (success) { backupInfo = info; showRestorePreview = true }
                                }
                            }
                        }
                    }
                ) { Text("ดำเนินการ") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false; backupPassword = "" }) { Text("ยกเลิก") }
            }
        )
    }

    if (showRestorePreview) {
        val info = backupInfo
        AlertDialog(
            onDismissRequest = { showRestorePreview = false; backupPassword = ""; backupUri = null },
            title = { Text("ตรวจสอบ Local Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Backup ID: " + (info?.backupId ?: "-"))
                    Text("ครัวเรือน: " + (info?.householdCount ?: 0))
                    Text("ประชากร: " + (info?.personCount ?: 0))
                    Text("ประวัติ: " + (info?.historyCount ?: 0))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("การ Restore จะเขียนทับข้อมูล Local ปัจจุบันทั้งหมด ควรสร้าง Backup ปัจจุบันก่อน", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = backupUri
                    if (uri != null) {
                        showRestorePreview = false
                        viewModel.restoreSecureBackup(context, uri, backupPassword) { success, restoredInfo, message ->
                            isError = !success
                            actionMessage = message
                            backupPassword = ""
                            backupUri = null
                            backupInfo = restoredInfo
                        }
                    }
                }) { Text("Restore ข้อมูล") }
            },
            dismissButton = {
                TextButton(onClick = { showRestorePreview = false; backupPassword = ""; backupUri = null }) { Text("ยกเลิก") }
            }
        )
    }

}

@Composable
fun StatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
