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
import com.example.ui.theme.MintAccent
import com.example.viewmodel.PersonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: PersonViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val totalPersons by viewModel.totalPersonsCount.collectAsStateWithLifecycle()
    val totalHouseholds by viewModel.totalHouseholdsCount.collectAsStateWithLifecycle()

    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

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

            // Section 2: Cloud Firestore Redundancy & Sync
            Text(
                text = "2. การซิงค์และสำรองข้อมูลบนคลาวด์ (Cloud Redundancy)",
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
                        text = "เชื่อมต่อและสำรองข้อมูลขึ้นระบบ Google Cloud Firestore เพื่อความปลอดภัย ป้องกันข้อมูลสูญหาย และซิงค์ระหว่างอุปกรณ์",
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
                        Text("กู้คืนข้อมูลจากคลาวด์ (Pull from Cloud)", fontWeight = FontWeight.Bold)
                    }

                    // Bidirectional Sync Button
                    Button(
                        onClick = {
                            viewModel.bidirectionalSync { result ->
                                isError = result.isFailure
                                actionMessage = if (result.isSuccess) "ซิงค์ 2 ทาง (Bidirectional) สำเร็จ" else "ซิงค์ไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintAccent)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ซิงค์แบบ 2 ทาง (Bidirectional Sync)", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
