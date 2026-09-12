package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HouseSummary
import com.example.ui.theme.CardShadowTint
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.viewmodel.PersonViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    viewModel: PersonViewModel,
    onHouseClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val houseSummary by viewModel.houseSummary.collectAsStateWithLifecycle()

    val locationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var selectedHouse by remember { mutableStateOf<HouseSummary?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val mappedHouses = remember(houseSummary) { houseSummary.filter { it.latitude != null && it.longitude != null } }
    val filteredHouses = remember(mappedHouses, searchQuery) {
        if (searchQuery.isBlank()) mappedHouses
        else mappedHouses.filter { it.houseNo.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val firstLocation = mappedHouses.firstOrNull()
    val initialLat = firstLocation?.latitude ?: 13.7563
    val initialLon = firstLocation?.longitude ?: 100.5018

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
                            "แสดงตำแหน่ง GPS บ้านเรือนในพื้นที่รับผิดชอบ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EmeraldPrimary,
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
                    Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
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
                    mapView.overlays.removeAll { it is Marker }

                    mappedHouses.forEach { house ->
                        val lat = house.latitude ?: return@forEach
                        val lon = house.longitude ?: return@forEach
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(lat, lon)
                            title = "บ้านเลขที่ ${house.houseNo}"
                            subDescription = "${house.totalMembers} สมาชิก (แตะเพื่อดูข้อมูล)"
                        }
                        marker.setOnMarkerClickListener { _, _ ->
                            selectedHouse = house
                            mapView.controller.animateTo(GeoPoint(lat, lon))
                            true
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                }
            )

            // Top Search & Stats Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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

                // Stats Chip
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                        Text(
                            text = "บันทึกพิกัดแล้ว ${mappedHouses.size} จาก ${houseSummary.size} ครัวเรือน",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Right-side Floating Control Buttons (Zoom In, Zoom Out, My Location)
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

            // Bottom Selected House Card Preview
            AnimatedVisibility(
                visible = selectedHouse != null,
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
                            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = CardShadowTint)
                            .clickable { onHouseClick(house.householdId) },
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldPrimary.copy(alpha = 0.5f))
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
                                            text = "สมาชิกทั้งหมด ${house.totalMembers} คน",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { selectedHouse = null }) {
                                    Icon(Icons.Filled.Close, contentDescription = "ปิด", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Filled.Man, contentDescription = null, tint = Color.Blue, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ชาย: ${house.males}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Filled.Woman, contentDescription = null, tint = Color.Magenta, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("หญิง: ${house.females}", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            Button(
                                onClick = { onHouseClick(house.householdId) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ดูรายละเอียดครัวเรือนและสมาชิก", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
