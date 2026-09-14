package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.utils.ValidationUtils
import com.example.viewmodel.PersonViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterMemberScreen(
    viewModel: PersonViewModel,
    onNavigateBack: () -> Unit,
    onRegistrationSuccess: (personId: Long, householdId: Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val allHouseholdsWithPersons by viewModel.allHouseholdsWithPersons.collectAsStateWithLifecycle()

    // Form fields: Name, ID Card Number, Address
    var fullName by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var houseNo by remember { mutableStateOf("") }
    var villageNo by remember { mutableStateOf("8") }
    var subdistrict by remember { mutableStateOf("ป่าขะ") }
    var district by remember { mutableStateOf("บ้านนา") }
    var province by remember { mutableStateOf("นครนายก") }

    // Demographics & status
    var gender by remember { mutableStateOf(Gender.MALE) }
    var birthDate by remember { mutableStateOf<LocalDate?>(null) }
    var isBirthYearOnly by remember { mutableStateOf(false) }
    var houseStatus by remember { mutableStateOf(HouseholdRole.RESIDENT) }
    var personStatus by remember { mutableStateOf(PersonStatus.ALIVE) }
    var dataStatus by remember { mutableStateOf(DataStatus.VERIFIED) }

    // UI state
    var isSubmitting by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var nationalIdError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Existing household quick-picker dialog/sheet
    var showHousePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = birthDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: Instant.now().toEpochMilli()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            birthDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                            isBirthYearOnly = false
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("date_picker_confirm_button")
                ) {
                    Text("ตกลง")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.testTag("date_picker_cancel_button")
                ) {
                    Text("ยกเลิก")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ลงทะเบียนสมาชิกประชากร",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "บันทึกข้อมูลบุคคลและที่อยู่เข้าสู่ระบบ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("register_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ย้อนกลับ",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: ข้อมูลบุคคล (Personal Info: Name & National ID)
            FormSectionCard(
                title = "ข้อมูลบุคคล (Personal Info)",
                icon = Icons.Filled.Person
            ) {
                // Name Field
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("ชื่อ-นามสกุล *") },
                    placeholder = { Text("เช่น นายสมชาย ใจดี") },
                    leadingIcon = {
                        Icon(Icons.Filled.Badge, contentDescription = null, tint = EmeraldPrimary)
                    },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_name_input")
                )

                // ID Card Number Field
                OutlinedTextField(
                    value = nationalId,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 13) {
                            nationalId = digits
                            nationalIdError = null
                        }
                    },
                    label = { Text("เลขประจำตัวประชาชน (13 หลัก)") },
                    placeholder = { Text("เช่น 1234567890123") },
                    leadingIcon = {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = EmeraldPrimary)
                    },
                    isError = nationalIdError != null,
                    supportingText = {
                        if (nationalIdError != null) {
                            Text(nationalIdError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${nationalId.length}/13 หลัก (เว้นว่างได้หากไม่มี)")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_national_id_input")
                )

                // Gender Selection
                Text(
                    text = "เพศ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(Gender.MALE to "ชาย", Gender.FEMALE to "หญิง").forEach { (g, label) ->
                        val isSelected = gender == g
                        FilterChip(
                            selected = isSelected,
                            onClick = { gender = g },
                            label = { Text(label) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("member_gender_${g.name.lowercase()}_chip")
                        )
                    }
                }

                // Date of Birth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ValidationUtils.formatThaiDateDisplay(birthDate, isBirthYearOnly),
                        onValueChange = {},
                        label = { Text("วัน/เดือน/ปี เกิด") },
                        placeholder = { Text("เลือกวันเกิด") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.testTag("member_birth_date_picker_button")
                            ) {
                                Icon(Icons.Filled.CalendarMonth, contentDescription = "เลือกวันเกิด", tint = EmeraldPrimary)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showDatePicker = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isBirthYearOnly,
                        onCheckedChange = { isBirthYearOnly = it },
                        modifier = Modifier.testTag("member_birth_year_only_checkbox")
                    )
                    Text(
                        text = "บันทึกเฉพาะปีเกิด (พ.ศ.)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { isBirthYearOnly = !isBirthYearOnly }
                    )
                }
            }

            // Section 2: ข้อมูลที่อยู่ / ครัวเรือน (Address Info)
            FormSectionCard(
                title = "ข้อมูลที่อยู่ / ครัวเรือน (Address)",
                icon = Icons.Filled.Home
            ) {
                // Quick pick existing house button
                if (allHouseholdsWithPersons.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showHousePicker = !showHousePicker },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_select_existing_house_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (showHousePicker) "ซ่อนรายการบ้านเดิม" else "เลือกจากบ้านเลขที่เดิมในระบบ")
                    }

                    AnimatedVisibility(visible = showHousePicker) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "แตะเพื่อนำข้อมูลที่อยู่มาใส่ในแบบฟอร์ม:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                allHouseholdsWithPersons.take(6).forEach { item ->
                                    TextButton(
                                        onClick = {
                                            houseNo = item.household.houseNo
                                            villageNo = item.household.villageNo
                                            subdistrict = item.household.subdistrict
                                            district = item.household.district
                                            province = item.household.province
                                            addressError = null
                                            showHousePicker = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "บ้านเลขที่ ${item.household.houseNo} (หมู่ ${item.household.villageNo.ifBlank { "8" }}) - สมาชิก ${item.persons.size} คน",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // House Number (บ้านเลขที่)
                OutlinedTextField(
                    value = houseNo,
                    onValueChange = {
                        houseNo = it
                        if (it.isNotBlank()) addressError = null
                    },
                    label = { Text("บ้านเลขที่ *") },
                    placeholder = { Text("เช่น 45/1 หรือ 100/2") },
                    isError = addressError != null,
                    supportingText = addressError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_house_no_input")
                )

                // Village Number (หมู่ที่) & Subdistrict (ตำบล)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = villageNo,
                        onValueChange = { villageNo = it },
                        label = { Text("หมู่ที่") },
                        placeholder = { Text("8") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("member_village_no_input")
                    )

                    OutlinedTextField(
                        value = subdistrict,
                        onValueChange = { subdistrict = it },
                        label = { Text("ตำบล") },
                        placeholder = { Text("ป่าขะ") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("member_subdistrict_input")
                    )
                }

                // District (อำเภอ) & Province (จังหวัด)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = district,
                        onValueChange = { district = it },
                        label = { Text("อำเภอ") },
                        placeholder = { Text("บ้านนา") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("member_district_input")
                    )

                    OutlinedTextField(
                        value = province,
                        onValueChange = { province = it },
                        label = { Text("จังหวัด") },
                        placeholder = { Text("นครนายก") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("member_province_input")
                    )
                }
            }

            // Section 3: สถานะและบทบาทในครัวเรือน (Role & Status)
            FormSectionCard(
                title = "สถานะและบทบาทในครัวเรือน",
                icon = Icons.Filled.CheckCircle
            ) {
                // Role in household
                DropdownMenuField(
                    label = "บทบาทในครัวเรือน",
                    options = listOf("เจ้าบ้าน", "ผู้อยู่อาศัย"),
                    selectedOption = if (houseStatus == HouseholdRole.HEAD) "เจ้าบ้าน" else "ผู้อยู่อาศัย",
                    onOptionSelected = {
                        houseStatus = if (it == "เจ้าบ้าน") HouseholdRole.HEAD else HouseholdRole.RESIDENT
                    }
                )

                // Living status
                DropdownMenuField(
                    label = "สถานะบุคคล",
                    options = listOf("มีชีวิตอยู่", "เสียชีวิตแล้ว", "ย้ายที่อยู่"),
                    selectedOption = when (personStatus) {
                        PersonStatus.ALIVE -> "มีชีวิตอยู่"
                        PersonStatus.DEAD -> "เสียชีวิตแล้ว"
                        PersonStatus.MOVED -> "ย้ายที่อยู่"
                        PersonStatus.UNKNOWN -> "ไม่ระบุ"
                    },
                    onOptionSelected = {
                        personStatus = when (it) {
                            "เสียชีวิตแล้ว" -> PersonStatus.DEAD
                            "ย้ายที่อยู่" -> PersonStatus.MOVED
                            else -> PersonStatus.ALIVE
                        }
                    }
                )

                // Data verification status
                DropdownMenuField(
                    label = "สถานะการตรวจสอบข้อมูล",
                    options = listOf("ตรวจสอบแล้ว", "รอการตรวจสอบ"),
                    selectedOption = if (dataStatus == DataStatus.VERIFIED) "ตรวจสอบแล้ว" else "รอการตรวจสอบ",
                    onOptionSelected = {
                        dataStatus = if (it == "ตรวจสอบแล้ว") DataStatus.VERIFIED else DataStatus.NEEDS_REVIEW
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Submit Button
            Button(
                onClick = {
                    // Validation
                    var hasError = false

                    if (fullName.isBlank()) {
                        nameError = "กรุณาระบุชื่อ-นามสกุล"
                        hasError = true
                    }

                    if (houseNo.isBlank()) {
                        addressError = "กรุณาระบุบ้านเลขที่"
                        hasError = true
                    }

                    val cleanId = nationalId.filter { it.isDigit() }
                    if (cleanId.isNotEmpty() && cleanId.length != 13) {
                        nationalIdError = "เลขประจำตัวประชาชนต้องมี 13 หลัก"
                        hasError = true
                    }

                    if (hasError) {
                        Toast.makeText(context, "กรุณากรอกข้อมูลที่จำเป็นให้ถูกต้อง", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Execute registration via ViewModel
                    isSubmitting = true
                    coroutineScope.launch {
                        val result = viewModel.registerMember(
                            fullName = fullName,
                            nationalId = cleanId.ifBlank { null },
                            houseNo = houseNo,
                            villageNo = villageNo,
                            subdistrict = subdistrict,
                            district = district,
                            province = province,
                            gender = gender,
                            birthDate = birthDate,
                            isBirthYearOnly = isBirthYearOnly,
                            houseStatus = houseStatus,
                            personStatus = personStatus,
                            dataStatus = dataStatus
                        )

                        isSubmitting = false
                        result.fold(
                            onSuccess = { (person, household) ->
                                Toast.makeText(context, "ลงทะเบียน ${person.fullName} สำเร็จ", Toast.LENGTH_SHORT).show()
                                onRegistrationSuccess(person.id, household.id)
                            },
                            onFailure = { error ->
                                val msg = error.message ?: "ลงทะเบียนไม่สำเร็จ"
                                if (msg.contains("เลขประจำตัวประชาชน")) {
                                    nationalIdError = msg
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("register_member_submit_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("กำลังบันทึกข้อมูล...", style = MaterialTheme.typography.titleMedium, color = Color.White)
                } else {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ลงทะเบียนสมาชิกประชากร", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = EmeraldPrimary
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            content()
        }
    }
}
