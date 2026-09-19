package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HouseSummary
import com.example.data.Household
import com.example.ui.theme.*
import com.example.viewmodel.PersonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

enum class PopulationFilter(val label: String) {
    ALL("ทั้งหมด"),
    HIGH_DENSITY("หนาแน่น (4+ คน)"),
    ELDERLY("มีผู้สูงอายุ (60+)"),
    CHILDREN("มีเด็กเล็ก (0-12)"),
    LOW_DENSITY("1-2 คน")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: PersonViewModel,
    targetHouseholdId: Long = -1L,
    onHouseClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val houseSummary by viewModel.houseSummary.collectAsStateWithLifecycle()
    val allHouseholdsWithPersons by viewModel.allHouseholdsWithPersons.collectAsStateWithLifecycle()

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedHouse by remember { mutableStateOf<HouseSummary?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(PopulationFilter.ALL) }
    var showStatsPanel by remember { mutableStateOf(false) }

    // Pinning state
    var isPinningMode by remember { mutableStateOf(false) }
    var pinningTargetHousehold by remember { mutableStateOf<Household?>(null) }
    var pendingPinLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var showHouseholdPickerDialog by remember { mutableStateOf(false) }
    var showUnpinnedHousesSheet by remember { mutableStateOf(false) }
    var houseToClearLocation by remember { mutableStateOf<HouseSummary?>(null) }

    val mappedHouses = remember(houseSummary) { houseSummary.filter { it.latitude != null && it.longitude != null } }
    val unmappedHouses = remember(houseSummary) { houseSummary.filter { it.latitude == null || it.longitude == null } }

    val filteredHouses = remember(mappedHouses, activeFilter, searchQuery) {
        val byFilter = when (activeFilter) {
            PopulationFilter.ALL -> mappedHouses
            PopulationFilter.HIGH_DENSITY -> mappedHouses.filter { it.totalMembers >= 4 }
            PopulationFilter.ELDERLY -> mappedHouses.filter { it.elderly > 0 }
            PopulationFilter.CHILDREN -> mappedHouses.filter { it.children > 0 }
            PopulationFilter.LOW_DENSITY -> mappedHouses.filter { it.totalMembers in 1..2 }
        }
        if (searchQuery.isBlank()) byFilter
        else byFilter.filter { it.houseNo.contains(searchQuery.trim(), ignoreCase = true) }
    }

    // Population statistics for distribution analysis
    val totalMappedPopulation = remember(mappedHouses) { mappedHouses.sumOf { it.totalMembers } }
    val totalVillagePopulation = remember(houseSummary) { houseSummary.sumOf { it.totalMembers } }
    val mappedElderly = remember(mappedHouses) { mappedHouses.sumOf { it.elderly } }
    val mappedChildren = remember(mappedHouses) { mappedHouses.sumOf { it.children } }
    val mappedMales = remember(mappedHouses) { mappedHouses.sumOf { it.males } }
    val mappedFemales = remember(mappedHouses) { mappedHouses.sumOf { it.females } }
    val avgPerHouse = remember(mappedHouses) {
        if (mappedHouses.isNotEmpty()) totalMappedPopulation.toDouble() / mappedHouses.size else 0.0
    }
    val coveragePercent = remember(mappedHouses, houseSummary) {
        if (houseSummary.isNotEmpty()) (mappedHouses.size * 100) / houseSummary.size else 0
    }

    val firstLocation = mappedHouses.firstOrNull()
    val initialLat = firstLocation?.latitude ?: 14.2155
    val initialLon = firstLocation?.longitude ?: 101.0723

    // Handle incoming targetHouseholdId (e.g. from HouseDetailScreen or form)
    LaunchedEffect(targetHouseholdId, houseSummary) {
        if (targetHouseholdId != -1L && houseSummary.isNotEmpty()) {
            val target = houseSummary.find { it.householdId == targetHouseholdId }
            if (target != null) {
                if (target.latitude != null && target.longitude != null) {
                    selectedHouse = target
                    mapViewRef?.controller?.animateTo(GeoPoint(target.latitude, target.longitude))
                    mapViewRef?.controller?.setZoom(17.0)
                } else {
                    val hh = viewModel.getHouseholdById(targetHouseholdId)
                    if (hh != null) {
                        pinningTargetHousehold = hh
                        isPinningMode = true
                        val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                        pendingPinLocation = center
                        Toast.makeText(context, "แตะบนแผนที่เพื่อระบุพิกัดบ้านเลขที่ ${hh.houseNo}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "แผนที่พิกัดครัวเรือน (GIS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isPinningMode) "โหมดปักหมุดตำแหน่งครัวเรือน" else "กระจายตัวประชากร: $totalMappedPopulation/$totalVillagePopulation คน",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    // Toggle population distribution statistics card
                    IconButton(onClick = { showStatsPanel = !showStatsPanel }) {
                        Icon(
                            Icons.Filled.Analytics,
                            contentDescription = "สถิติการกระจายตัว",
                            tint = if (showStatsPanel) MintAccent else Color.White
                        )
                    }
                    // Pin Mode button
                    IconButton(
                        onClick = {
                            if (isPinningMode) {
                                isPinningMode = false
                                pinningTargetHousehold = null
                                pendingPinLocation = null
                            } else {
                                isPinningMode = true
                                pinningTargetHousehold = null
                                val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                                pendingPinLocation = center
                                Toast.makeText(context, "แตะตำแหน่งบนแผนที่ หรือลากหมุดสีแดง แล้วกดบันทึกพิกัด", Toast.LENGTH_LONG).show()
                            }
                        }
                    ) {
                        Icon(
                            if (isPinningMode) Icons.Filled.Close else Icons.Filled.AddLocationAlt,
                            contentDescription = if (isPinningMode) "ยกเลิกปักหมุด" else "ปักหมุดใหม่",
                            tint = if (isPinningMode) Color(0xFFFF6B6B) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isPinningMode) Color(0xFF065F46) else EmeraldPrimary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // OpenStreetMap View
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val config = Configuration.getInstance()
                    config.load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
                    config.userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(GeoPoint(initialLat, initialLon))
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapViewRef = mapView
                    mapView.overlays.removeAll { it is Marker || it is MapEventsOverlay }

                    // Add Touch Events Overlay for interactive map tapping and long press
                    val mapEventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            if (isPinningMode) {
                                pendingPinLocation = p
                                return true
                            }
                            selectedHouse = null
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint): Boolean {
                            pendingPinLocation = p
                            isPinningMode = true
                            Toast.makeText(context, "เลือกพิกัดแล้ว กดบันทึกเพื่อกำหนดครัวเรือน", Toast.LENGTH_SHORT).show()
                            return true
                        }
                    }
                    mapView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))

                    // Add markers for filtered mapped houses
                    filteredHouses.forEach { house ->
                        val lat = house.latitude ?: return@forEach
                        val lon = house.longitude ?: return@forEach
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(lat, lon)
                            title = "บ้านเลขที่ ${house.houseNo}"
                            snippet = "ประชากร ${house.totalMembers} คน (ชาย ${house.males}, หญิง ${house.females})"
                            subDescription = if (house.elderly > 0) "ผู้สูงอายุ: ${house.elderly} คน" else null
                            icon = createHouseholdMarkerDrawable(
                                context = context,
                                totalMembers = house.totalMembers,
                                hasElderly = house.elderly > 0,
                                hasChildren = house.children > 0,
                                isSelected = selectedHouse?.householdId == house.householdId
                            )
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        marker.setOnMarkerClickListener { _, _ ->
                            if (!isPinningMode) {
                                selectedHouse = house
                                mapView.controller.animateTo(GeoPoint(lat, lon))
                            }
                            true
                        }
                        mapView.overlays.add(marker)
                    }

                    // Add pending pin marker when in pinning mode
                    if (isPinningMode && pendingPinLocation != null) {
                        val pendingMarker = Marker(mapView).apply {
                            position = pendingPinLocation
                            title = if (pinningTargetHousehold != null) "ปักหมุดบ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "หมุดตำแหน่งใหม่"
                            snippet = "แตะบนแผนที่เพื่อย้ายตำแหน่ง หรือลากหมุดนี้"
                            icon = createPendingPinMarkerDrawable(context)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            isDraggable = true
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(m: Marker) {}
                                override fun onMarkerDragEnd(m: Marker) {
                                    pendingPinLocation = m.position
                                }
                                override fun onMarkerDragStart(m: Marker) {}
                            })
                        }
                        mapView.overlays.add(pendingMarker)
                    }

                    mapView.invalidate()
                }
            )

            // Top Search & Filter Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pinning mode status banner
                AnimatedVisibility(
                    visible = isPinningMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(14.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF047857)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.PinDrop, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pinningTargetHousehold != null) "กำลังปักหมุด: บ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "โหมดปักหมุดพิกัดครัวเรือน",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    pendingPinLocation?.let { "Lat: ${String.format("%.5f", it.latitude)}, Lon: ${String.format("%.5f", it.longitude)}" }
                                        ?: "แตะตำแหน่งบนแผนที่เพื่อวางหมุด",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    isPinningMode = false
                                    pinningTargetHousehold = null
                                    pendingPinLocation = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "ยกเลิก", tint = Color.White)
                            }
                        }
                    }
                }

                // Search Bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(16.dp), spotColor = CardShadowTint),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("ค้นหาบ้านเลขที่บนแผนที่...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "ล้างค้นหา", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Population Distribution Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(PopulationFilter.values()) { filter ->
                        val count = when (filter) {
                            PopulationFilter.ALL -> mappedHouses.size
                            PopulationFilter.HIGH_DENSITY -> mappedHouses.count { it.totalMembers >= 4 }
                            PopulationFilter.ELDERLY -> mappedHouses.count { it.elderly > 0 }
                            PopulationFilter.CHILDREN -> mappedHouses.count { it.children > 0 }
                            PopulationFilter.LOW_DENSITY -> mappedHouses.count { it.totalMembers in 1..2 }
                        }
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text("${filter.label} ($count)") },
                            leadingIcon = if (activeFilter == filter) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    // Unpinned houses shortcut chip
                    item {
                        ActionChip(
                            label = "ยังไม่ระบุพิกัด (${unmappedHouses.size})",
                            icon = Icons.Filled.LocationOff,
                            tint = if (unmappedHouses.isNotEmpty()) Color(0xFFE11D48) else Color.Gray,
                            onClick = { showUnpinnedHousesSheet = true }
                        )
                    }
                }

                // Collapsible Population Distribution Overview Card
                AnimatedVisibility(
                    visible = showStatsPanel,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(18.dp), spotColor = CardShadowTint),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.Analytics, contentDescription = null, tint = EmeraldPrimary)
                                    Text(
                                        "สรุปการกระจายตัวของประชากร",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "ครอบคลุม $coveragePercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                            }

                            // Progress Bar
                            LinearProgressIndicator(
                                progress = { if (houseSummary.isNotEmpty()) mappedHouses.size.toFloat() / houseSummary.size.toFloat() else 0f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = EmeraldPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            // Population Breakdown Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "ประชากรบนแผนที่",
                                    value = "$totalMappedPopulation คน",
                                    subtext = "ชาย $mappedMales | หญิง $mappedFemales",
                                    color = EmeraldPrimary
                                )
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "ผู้สูงอายุ (60+)",
                                    value = "$mappedElderly คน",
                                    subtext = "กลุ่มเปราะบาง",
                                    color = Color(0xFF7C3AED)
                                )
                                StatMiniCard(
                                    modifier = Modifier.weight(1f),
                                    title = "เด็กเล็ก (0-12)",
                                    value = "$mappedChildren คน",
                                    subtext = "วัยเจริญเติบโต",
                                    color = Color(0xFF0284C7)
                                )
                            }

                            // Legend and Unpinned Action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    LegendDot(color = Color(0xFF059669), label = "1-3 คน")
                                    LegendDot(color = Color(0xFFEA580C), label = "4+ คน")
                                    LegendDot(color = Color(0xFF7C3AED), label = "ผู้สูงอายุ")
                                }
                                if (unmappedHouses.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showUnpinnedHousesSheet = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("ปักหมุดบ้านที่เหลือ (${unmappedHouses.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Right-side Floating Control Buttons
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Zoom In
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomIn() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "ซูมเข้า", modifier = Modifier.size(20.dp))
                }

                // Zoom Out
                FloatingActionButton(
                    onClick = { mapViewRef?.controller?.zoomOut() },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).shadow(4.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "ซูมออก", modifier = Modifier.size(20.dp))
                }

                // Pin Here via GPS (When in pinning mode)
                if (isPinningMode) {
                    FloatingActionButton(
                        onClick = {
                            if (locationPermissionState.status.isGranted) {
                                coroutineScope.launch {
                                    try {
                                        @SuppressLint("MissingPermission")
                                        val req = com.google.android.gms.location.CurrentLocationRequest.Builder()
                                            .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                                            .build()
                                        @SuppressLint("MissingPermission")
                                        val loc = fusedLocationClient.getCurrentLocation(req, null).await()
                                        if (loc != null) {
                                            val pt = GeoPoint(loc.latitude, loc.longitude)
                                            pendingPinLocation = pt
                                            mapViewRef?.controller?.animateTo(pt)
                                            mapViewRef?.controller?.setZoom(17.0)
                                            Toast.makeText(context, "วางหมุดที่ตำแหน่ง GPS ปัจจุบันแล้ว", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "เกิดข้อผิดพลาด: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                locationPermissionState.launchPermissionRequest()
                            }
                        },
                        containerColor = Color(0xFFEA580C),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(50.dp).shadow(6.dp, CircleShape)
                    ) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = "วางหมุดตาม GPS ปัจจุบัน")
                    }
                }

                // My Location
                FloatingActionButton(
                    onClick = {
                        if (locationPermissionState.status.isGranted) {
                            coroutineScope.launch {
                                try {
                                    Toast.makeText(context, "กำลังค้นหาตำแหน่งของคุณ...", Toast.LENGTH_SHORT).show()
                                    @SuppressLint("MissingPermission")
                                    val locationRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                                        .build()
                                    @SuppressLint("MissingPermission")
                                    val location = fusedLocationClient.getCurrentLocation(locationRequest, null).await()
                                    if (location != null) {
                                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                                        mapViewRef?.controller?.animateTo(geoPoint)
                                        mapViewRef?.controller?.setZoom(17.0)
                                        Toast.makeText(context, "ย้ายไปยังตำแหน่งปัจจุบันแล้ว", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ไม่พบตำแหน่งปัจจุบัน", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "เกิดข้อผิดพลาดในการดึงพิกัด: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    },
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp).shadow(6.dp, CircleShape)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "ตำแหน่งของฉัน")
                }
            }

            // Bottom Floating Card 1: Pinning Mode Confirmation Bar
            AnimatedVisibility(
                visible = isPinningMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = CardShadowTint),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, EmeraldPrimary)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFEF2F2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PinDrop, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (pinningTargetHousehold != null) "ปักหมุดบ้านเลขที่ ${pinningTargetHousehold?.houseNo}" else "พิกัดที่เลือกบนแผนที่",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    pendingPinLocation?.let { "Lat: ${String.format("%.5f", it.latitude)}, Lon: ${String.format("%.5f", it.longitude)}" } ?: "กรุณาแตะบนแผนที่",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isPinningMode = false
                                    pinningTargetHousehold = null
                                    pendingPinLocation = null
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ยกเลิก")
                            }

                            Button(
                                onClick = {
                                    val loc = pendingPinLocation
                                    if (loc == null) {
                                        Toast.makeText(context, "กรุณาแตะบนแผนที่เพื่อวางหมุดก่อน", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (pinningTargetHousehold != null) {
                                        val hh = pinningTargetHousehold!!
                                        viewModel.updateHouseholdLocation(
                                            householdId = hh.id,
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            provider = "MANUAL_PIN"
                                        ) { success, msg ->
                                            if (success) {
                                                Toast.makeText(context, "บันทึกพิกัดบ้านเลขที่ ${hh.houseNo} สำเร็จ", Toast.LENGTH_SHORT).show()
                                                isPinningMode = false
                                                pinningTargetHousehold = null
                                                pendingPinLocation = null
                                            } else {
                                                Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        showHouseholdPickerDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (pinningTargetHousehold != null) "บันทึกพิกัดนี้" else "เลือกครัวเรือน...")
                            }
                        }
                    }
                }
            }

            // Bottom Floating Card 2: Selected House Preview Card (Normal Mode)
            AnimatedVisibility(
                visible = selectedHouse != null && !isPinningMode,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                selectedHouse?.let { house ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = CardShadowTint),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.5f))
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(EmeraldPrimary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Home, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
                                    }
                                    Column {
                                        Text(
                                            text = "บ้านเลขที่ ${house.houseNo}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "ประชากร ${house.totalMembers} คน (เจ้าบ้าน ${house.owners}, ผู้อาศัย ${house.residents})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { selectedHouse = null }) {
                                    Icon(Icons.Filled.Close, contentDescription = "ปิด", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Demographic Badges Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MiniBadge(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Man,
                                    label = "ชาย ${house.males}",
                                    tint = Color.Blue
                                )
                                MiniBadge(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Woman,
                                    label = "หญิง ${house.females}",
                                    tint = Color.Magenta
                                )
                                if (house.elderly > 0) {
                                    MiniBadge(
                                        modifier = Modifier.weight(1.2f),
                                        icon = Icons.Filled.Elderly,
                                        label = "สูงอายุ ${house.elderly}",
                                        tint = Color(0xFF7C3AED)
                                    )
                                }
                                if (house.children > 0) {
                                    MiniBadge(
                                        modifier = Modifier.weight(1.1f),
                                        icon = Icons.Filled.ChildCare,
                                        label = "เด็ก ${house.children}",
                                        tint = Color(0xFF0284C7)
                                    )
                                }
                            }

                            // GPS Coordinates Display
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "Lat: ${house.latitude}, Lon: ${house.longitude}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = { houseToClearLocation = house },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("ลบพิกัด", color = Color.Red, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val hh = viewModel.getHouseholdById(house.householdId)
                                            if (hh != null) {
                                                pinningTargetHousehold = hh
                                                pendingPinLocation = GeoPoint(house.latitude ?: initialLat, house.longitude ?: initialLon)
                                                isPinningMode = true
                                                selectedHouse = null
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.EditLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ย้ายหมุด")
                                }

                                Button(
                                    onClick = { onHouseClick(house.householdId) },
                                    modifier = Modifier.weight(1.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                ) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ดูข้อมูลครัวเรือน", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Household Picker (When dropping pin and selecting which house to assign)
    if (showHouseholdPickerDialog) {
        HouseholdPickerDialog(
            households = allHouseholdsWithPersons.map { it.household },
            unmappedHouseholdIds = unmappedHouses.map { it.householdId }.toSet(),
            onDismiss = { showHouseholdPickerDialog = false },
            onSelect = { household ->
                val loc = pendingPinLocation
                if (loc != null) {
                    viewModel.updateHouseholdLocation(
                        householdId = household.id,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        provider = "MANUAL_PIN"
                    ) { success, msg ->
                        if (success) {
                            Toast.makeText(context, "บันทึกพิกัดบ้านเลขที่ ${household.houseNo} สำเร็จ", Toast.LENGTH_SHORT).show()
                            isPinningMode = false
                            pinningTargetHousehold = null
                            pendingPinLocation = null
                            showHouseholdPickerDialog = false
                        } else {
                            Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // Bottom Sheet / Dialog: Unpinned Houses List
    if (showUnpinnedHousesSheet) {
        UnpinnedHousesDialog(
            unmappedHouses = unmappedHouses,
            onDismiss = { showUnpinnedHousesSheet = false },
            onPinHouse = { houseSummaryItem ->
                coroutineScope.launch {
                    val hh = viewModel.getHouseholdById(houseSummaryItem.householdId)
                    if (hh != null) {
                        pinningTargetHousehold = hh
                        isPinningMode = true
                        val center = mapViewRef?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(initialLat, initialLon)
                        pendingPinLocation = center
                        showUnpinnedHousesSheet = false
                        Toast.makeText(context, "แตะบนแผนที่เพื่อระบุตำแหน่งบ้านเลขที่ ${hh.houseNo}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Dialog: Confirm Clear Pin Location
    houseToClearLocation?.let { house ->
        AlertDialog(
            onDismissRequest = { houseToClearLocation = null },
            title = { Text("ยืนยันการลบพิกัด") },
            text = { Text("คุณต้องการลบพิกัด GPS ของบ้านเลขที่ ${house.houseNo} ใช่หรือไม่? (ข้อมูลสมาชิกและประวัติครัวเรือนจะยังคงอยู่ครบถ้วน)") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeHouseholdLocation(house.householdId) { success, msg ->
                            if (success) {
                                Toast.makeText(context, "ลบพิกัดเรียบร้อย", Toast.LENGTH_SHORT).show()
                                selectedHouse = null
                            } else {
                                Toast.makeText(context, msg ?: "เกิดข้อผิดพลาด", Toast.LENGTH_SHORT).show()
                            }
                        }
                        houseToClearLocation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("ลบพิกัด")
                }
            },
            dismissButton = {
                TextButton(onClick = { houseToClearLocation = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}

@Composable
fun HouseholdPickerDialog(
    households: List<Household>,
    unmappedHouseholdIds: Set<Long>,
    onDismiss: () -> Unit,
    onSelect: (Household) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(if (unmappedHouseholdIds.isNotEmpty()) 0 else 1) }

    val filteredList = remember(households, search, selectedTab) {
        val byTab = when (selectedTab) {
            0 -> households.filter { unmappedHouseholdIds.contains(it.id) }
            else -> households
        }
        if (search.isBlank()) byTab
        else byTab.filter { it.houseNo.contains(search.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("เลือกครัวเรือนที่จะปักหมุด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "ปิด")
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("ยังไม่มีพิกัด (${unmappedHouseholdIds.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("ทั้งหมด (${households.size})") }
                    )
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("ค้นหาบ้านเลขที่...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("ไม่พบรายการครัวเรือน", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList) { hh ->
                            val hasLoc = hh.latitude != null && hh.longitude != null
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(hh) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasLoc) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else EmeraldPrimary.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("บ้านเลขที่ ${hh.houseNo}", fontWeight = FontWeight.Bold)
                                        val villageInfo = if (hh.villageNo.isNotBlank()) "หมู่ ${hh.villageNo} " else ""
                                        val subdistrictInfo = if (hh.subdistrict.isNotBlank()) "ต.${hh.subdistrict}" else ""
                                        if (villageInfo.isNotBlank() || subdistrictInfo.isNotBlank()) {
                                            Text("$villageInfo$subdistrictInfo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (hasLoc) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("มีพิกัดเดิม", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        Surface(shape = RoundedCornerShape(6.dp), color = EmeraldPrimary.copy(alpha = 0.2f)) {
                                            Text("ยังไม่มีพิกัด", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnpinnedHousesDialog(
    unmappedHouses: List<HouseSummary>,
    onDismiss: () -> Unit,
    onPinHouse: (HouseSummary) -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(unmappedHouses, search) {
        if (search.isBlank()) unmappedHouses
        else unmappedHouses.filter { it.houseNo.contains(search.trim(), ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("บ้านที่ยังไม่ได้ระบุพิกัด", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("เลือกบ้านเพื่อนำหมุดไปวางบนแผนที่", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "ปิด")
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("ค้นหาบ้านเลขที่...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("ทุกครัวเรือนได้รับการปักหมุดครบถ้วนแล้ว 🎉", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { house ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPinHouse(house) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("บ้านเลขที่ ${house.houseNo}", fontWeight = FontWeight.Bold)
                                        Text("สมาชิก ${house.totalMembers} คน (สูงอายุ ${house.elderly}, เด็ก ${house.children})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(
                                        onClick = { onPinHouse(house) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                                    ) {
                                        Icon(Icons.Filled.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ปักหมุด", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(subtext, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniBadge(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun createHouseholdMarkerDrawable(
    context: Context,
    totalMembers: Int,
    hasElderly: Boolean,
    hasChildren: Boolean,
    isSelected: Boolean
): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (42 * density).toInt()
    val height = (50 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Color based on population density and vulnerability
    val pinColor = when {
        isSelected -> android.graphics.Color.rgb(16, 185, 129)
        hasElderly -> android.graphics.Color.rgb(124, 58, 237) // Purple for Elderly
        totalMembers >= 4 -> android.graphics.Color.rgb(234, 88, 12) // Orange for High Density
        hasChildren -> android.graphics.Color.rgb(2, 132, 199) // Sky Blue for Children
        else -> android.graphics.Color.rgb(5, 150, 105) // Standard Emerald
    }

    // Shadow
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(50, 0, 0, 0)
    }
    val shadowRadius = 15 * density
    canvas.drawCircle(width / 2f, 18 * density, shadowRadius + (2 * density), shadowPaint)

    // Pin shape
    val path = Path()
    val circleCenterY = 18 * density
    val circleRadius = 15 * density

    path.addCircle(width / 2f, circleCenterY, circleRadius, Path.Direction.CW)
    val trianglePath = Path().apply {
        moveTo((width / 2f) - (8 * density), circleCenterY + (9 * density))
        lineTo(width / 2f, height - (2 * density))
        lineTo((width / 2f) + (8 * density), circleCenterY + (9 * density))
        close()
    }
    path.op(trianglePath, Path.Op.UNION)

    paint.color = pinColor
    paint.style = Paint.Style.FILL
    canvas.drawPath(path, paint)

    // White border
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    canvas.drawPath(path, strokePaint)

    // Text (Number of population members)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 12 * density
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val text = if (totalMembers > 99) "99+" else "$totalMembers"
    val textBounds = Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)
    val textY = circleCenterY + (textBounds.height() / 2f)
    canvas.drawText(text, width / 2f, textY, textPaint)

    // Elderly indicator badge dot
    if (hasElderly) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(251, 191, 36)
            style = Paint.Style.FILL
        }
        canvas.drawCircle((width / 2f) + (10 * density), circleCenterY - (10 * density), 4.5f * density, badgePaint)
        val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle((width / 2f) + (10 * density), circleCenterY - (10 * density), 4.5f * density, badgeBorder)
    }

    return BitmapDrawable(context.resources, bitmap)
}

fun createPendingPinMarkerDrawable(context: Context): Drawable {
    val density = context.resources.displayMetrics.density
    val width = (46 * density).toInt()
    val height = (56 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pinColor = android.graphics.Color.rgb(220, 38, 38) // Bright red for pending pin

    val path = Path()
    val circleCenterY = 19 * density
    val circleRadius = 16 * density
    path.addCircle(width / 2f, circleCenterY, circleRadius, Path.Direction.CW)
    val trianglePath = Path().apply {
        moveTo((width / 2f) - (9 * density), circleCenterY + (10 * density))
        lineTo(width / 2f, height - (2 * density))
        lineTo((width / 2f) + (9 * density), circleCenterY + (10 * density))
        close()
    }
    path.op(trianglePath, Path.Op.UNION)

    paint.color = pinColor
    paint.style = Paint.Style.FILL
    canvas.drawPath(path, paint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    canvas.drawPath(path, strokePaint)

    // Center white dot
    val whiteDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(width / 2f, circleCenterY, 5.5f * density, whiteDotPaint)

    return BitmapDrawable(context.resources, bitmap)
}

