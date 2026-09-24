package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.ReferenceMatchStatus
import com.example.domain.ThaiPhcImportPreview
import com.example.domain.ThaiPhcMatchResult

/** Review-only ThaiPHC reference screen. No Room/Firestore writes occur here. */
@Composable
fun ThaiPhcImportPreviewScreen(
    preview: ThaiPhcImportPreview,
    onApprove: (ThaiPhcMatchResult) -> Unit,
    onReject: (ThaiPhcMatchResult) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("ThaiPHC • ตรวจสอบข้อมูลอ้างอิง", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("ข้อมูลจาก ThaiPHC ยังไม่เปลี่ยนแปลงข้อมูล Local จนกว่าจะได้รับการยืนยัน")
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("สรุปข้อมูล", style = MaterialTheme.typography.titleMedium)
                Text("ทั้งหมด: ${preview.total}")
                Text("ตรงกัน: ${preview.matched}")
                Text("พบใหม่: ${preview.newRecords}")
                Text("ขัดแย้ง: ${preview.conflicts}")
                Text("ต้องตรวจสอบ: ${preview.unknown}")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack) { Text("กลับ") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(preview.items) { item ->
                ThaiPhcReviewCard(item, { onApprove(item) }, { onReject(item) })
            }
        }
    }
}

@Composable
private fun ThaiPhcReviewCard(
    item: ThaiPhcMatchResult,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val r = item.reference
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${r.firstName} ${r.lastName}", style = MaterialTheme.typography.titleMedium)
            Text("บ้าน ${r.houseNo ?: "-"} หมู่ ${r.moo ?: "-"} ต.${r.tambon ?: "-"}")
            Text("สถานะ: ${statusLabel(item.status)}")
            Text("คะแนน: ${item.score}")
            item.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReject) { Text("ไม่รับ") }
                Button(onClick = onApprove, enabled = item.status != ReferenceMatchStatus.UNKNOWN) {
                    Text("ยืนยัน")
                }
            }
        }
    }
}

private fun statusLabel(status: ReferenceMatchStatus): String = when (status) {
    ReferenceMatchStatus.MATCH -> "ตรงกัน"
    ReferenceMatchStatus.NEW -> "พบข้อมูลใหม่"
    ReferenceMatchStatus.CONFLICT -> "ข้อมูลขัดแย้ง"
    ReferenceMatchStatus.UNKNOWN -> "ต้องตรวจสอบ"
}
