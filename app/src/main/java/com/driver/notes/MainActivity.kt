package com.driver.notes

import android.Manifest
import androidx.activity.enableEdgeToEdge
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var mode by rememberSaveable { mutableStateOf(getSharedPreferences("settings", MODE_PRIVATE).getInt("theme", 0)) }
            AppTheme(mode) {
                var editId by rememberSaveable { mutableStateOf<Long?>(null) } // null = danh sách, 0 = thêm mới
                val cur = editId
                val onTheme = {
                    mode = (mode + 1) % 3
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("theme", mode).apply()
                    Toast.makeText(this@MainActivity, "Giao diện: " + listOf("Theo hệ thống", "Sáng", "Tối")[mode], Toast.LENGTH_SHORT).show()
                }
                if (cur == null) ListScreen(vm, mode, onTheme) { editId = it } else EditScreen(vm, cur) { editId = null }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(vm: MainViewModel, themeMode: Int, onTheme: () -> Unit, open: (Long) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    val typeF by vm.typeFilter.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    viewer?.let { ImageViewer(it.first, it.second) { viewer = null } }
    var more by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportData(it) { m -> Toast.makeText(ctx, m, Toast.LENGTH_LONG).show() } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importData(it) { m -> Toast.makeText(ctx, m, Toast.LENGTH_LONG).show() } }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Sổ tay tài xế") }, actions = {
            IconButton(onTheme) {
                Icon(when (themeMode) { 1 -> Icons.Default.LightMode; 2 -> Icons.Default.DarkMode; else -> Icons.Default.BrightnessAuto }, "Đổi giao diện")
            }
            Box {
                TextButton(onClick = { menu = true }) { Icon(Icons.Default.Sort, null); Spacer(Modifier.width(4.dp)); Text(sort.label) }
                DropdownMenu(menu, { menu = false }) {
                    Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.sort.value = s; menu = false }) }
                }
            }
            Box {
                IconButton({ more = true }) { Icon(Icons.Default.MoreVert, "Thêm") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text("Xuất dữ liệu (.zip)") }, leadingIcon = { Icon(Icons.Default.Upload, null) }, onClick = {
                        more = false
                        exportLauncher.launch("SoTayTaiXe-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip")
                    })
                    DropdownMenuItem(text = { Text("Nhập dữ liệu (.zip)") }, leadingIcon = { Icon(Icons.Default.Download, null) }, onClick = {
                        more = false
                        importLauncher.launch(arrayOf("*/*"))
                    })
                }
            }
        }) },
        floatingActionButton = { FloatingActionButton({ open(0) }) { Icon(Icons.Default.Add, "Thêm") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 12.dp)) {
            OutlinedTextField(q, { vm.query.value = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Tìm tên đường, số nhà, ghi chú, hashtag…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton({ vm.query.value = "" }) { Icon(Icons.Default.Close, null) } })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                item { FilterChip(typeF == null, { vm.typeFilter.value = null }, { Text("Tất cả") }) }
                items(PlaceType.values().toList()) { t ->
                    FilterChip(typeF == t.key, { vm.typeFilter.value = if (typeF == t.key) null else t.key }, { Text(t.label) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(PRESET_TAGS) { t ->
                    FilterChip(tag == t, { vm.tagFilter.value = if (tag == t) null else t }, { Text("#$t") })
                }
            }
            if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Chưa có ghi chú nào") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(items, key = { it.street.id }) { w ->
                    Card(Modifier.fillMaxWidth().clickable { open(w.street.id) }) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val pt = PlaceType.fromKey(w.street.type)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(pt.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(pt.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text(w.street.name, style = MaterialTheme.typography.titleMedium)
                                    if (w.street.houseNumber.isNotBlank()) Text("Số nhà: ${w.street.houseNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                                val la = w.street.lat; val ln = w.street.lng
                                if (la != null && ln != null) IconButton({ openMaps(ctx, la, ln, w.street.name) }) { Icon(Icons.Default.Map, "Mở Google Maps") }
                                IconButton({ vm.delete(w) }) { Icon(Icons.Default.Delete, "Xóa") }
                            }
                            if (w.street.note.isNotBlank()) Text(w.street.note, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                            if (w.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                                itemsIndexed(w.images) { i, im ->
                                    AsyncImage(im.path, null, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).clickable { viewer = w.images.map { it.path } to i }, contentScale = ContentScale.Crop)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                w.street.tagList().forEach { AssistChip({}, { Text("#$it") }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("MissingPermission")
@Composable
fun EditScreen(vm: MainViewModel, id: Long, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    var coord by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(PlaceType.ROAD.key) }
    var locating by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val ctx = LocalContext.current
    val tags = remember { mutableStateListOf<String>() }
    val images = remember { mutableStateListOf<String>() }
    LaunchedEffect(id) {
        if (id > 0) vm.load(id)?.let { w ->
            name = w.street.name; type = w.street.type; house = w.street.houseNumber; note = w.street.note
            val la = w.street.lat; val ln = w.street.lng
            if (la != null && ln != null) coord = String.format(Locale.US, "%.6f, %.6f", la, ln)
            tags.addAll(w.street.tagList()); images.addAll(w.images.map { it.path })
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        images.addAll(uris.map { it.toString() })
    }
    val locate = {
        locating = true
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { l ->
                locating = false
                if (l != null) coord = String.format(Locale.US, "%.6f, %.6f", l.latitude, l.longitude)
                else Toast.makeText(ctx, "Chưa lấy được vị trí, hãy bật GPS", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { locating = false; Toast.makeText(ctx, "Lỗi lấy vị trí", Toast.LENGTH_SHORT).show() }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) locate() else Toast.makeText(ctx, "Cần cấp quyền vị trí", Toast.LENGTH_SHORT).show()
    }
    val parsed = parseCoords(coord)
    viewer?.let { ImageViewer(it.first, it.second) { viewer = null } }
    BackHandler(onBack = close)
    Scaffold(topBar = { TopAppBar(
        title = { Text(if (id > 0) "Sửa ghi chú" else "Thêm địa điểm") },
        navigationIcon = { IconButton(close) { Icon(Icons.Default.ArrowBack, null) } },
        actions = { IconButton({ if (name.isNotBlank() && (coord.isBlank() || parsed != null)) vm.save(id, type, name, house, note, tags, parsed?.first, parsed?.second, images, close) }) { Icon(Icons.Default.Check, "Lưu") } }
    ) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Loại", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceType.values().forEach { t ->
                    FilterChip(type == t.key, { type = t.key }, { Text(t.label) }, leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) })
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (type == "road") "Tên đường *" else "Tên địa điểm *") }, singleLine = true)
            OutlinedTextField(house, { house = it }, Modifier.fillMaxWidth(), label = { Text(if (type == "road") "Số nhà" else "Số nhà / địa chỉ") }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Ghi chú") })

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(coord, { coord = it }, Modifier.weight(1f), label = { Text("Tọa độ GPS (vĩ độ, kinh độ)") },
                    singleLine = true, isError = coord.isNotBlank() && parsed == null)
                if (locating) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp))
                else IconButton({ permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                    Icon(Icons.Default.MyLocation, "Lấy vị trí hiện tại")
                }
            }
            if (parsed != null) OutlinedButton({ openMaps(ctx, parsed.first, parsed.second, name.ifBlank { "Vị trí" }) }) {
                Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Mở Google Maps")
            }

            Text("Hashtag", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (PRESET_TAGS + tags.filter { it !in PRESET_TAGS }).forEach { t ->
                    FilterChip(t in tags, { if (t in tags) tags.remove(t) else tags.add(t) }, { Text("#$t") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(custom, { custom = it.replace(",", "") }, Modifier.weight(1f), label = { Text("Hashtag tùy chọn") }, singleLine = true)
                IconButton({ custom.trim().removePrefix("#").takeIf { it.isNotBlank() && it !in tags }?.let(tags::add); custom = "" }) { Icon(Icons.Default.Add, "Thêm tag") }
            }

            Text("Hình ảnh (${images.size})", style = MaterialTheme.typography.titleSmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.size(96.dp)) {
                        Icon(Icons.Default.AddPhotoAlternate, "Thêm ảnh")
                    }
                }
                itemsIndexed(images.toList()) { i, p ->
                    Box {
                        AsyncImage(p, null, Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).clickable { viewer = images.toList() to i }, contentScale = ContentScale.Crop)
                        IconButton({ images.remove(p) }, Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Cancel, "Bỏ ảnh") }
                    }
                }
            }
        }
    }
}
