package com.driver.notes

import android.os.Bundle
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
        setContent {
            MaterialTheme {
                var editId by rememberSaveable { mutableStateOf<Long?>(null) } // null = danh sách, 0 = thêm mới
                if (editId == null) ListScreen(vm) { editId = it } else EditScreen(vm, editId!!) { editId = null }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListScreen(vm: MainViewModel, open: (Long) -> Unit) {
    val items by vm.items.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val tag by vm.tagFilter.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Sổ tay tài xế") }, actions = {
            Box {
                TextButton(onClick = { menu = true }) { Icon(Icons.Default.Sort, null); Spacer(Modifier.width(4.dp)); Text(sort.label) }
                DropdownMenu(menu, { menu = false }) {
                    Sort.values().forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.sort.value = s; menu = false }) }
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
                                    Text(w.street.name, style = MaterialTheme.typography.titleMedium)
                                    if (w.street.houseNumber.isNotBlank()) Text("Số nhà: ${w.street.houseNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton({ vm.delete(w) }) { Icon(Icons.Default.Delete, "Xóa") }
                            }
                            if (w.street.note.isNotBlank()) Text(w.street.note, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                            if (w.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(w.images) { AsyncImage(it.path, null, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
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
@Composable
fun EditScreen(vm: MainViewModel, id: Long, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    val images = remember { mutableStateListOf<String>() }
    LaunchedEffect(id) {
        if (id > 0) vm.load(id)?.let { w ->
            name = w.street.name; house = w.street.houseNumber; note = w.street.note
            tags.addAll(w.street.tagList()); images.addAll(w.images.map { it.path })
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        images.addAll(uris.map { it.toString() })
    }
    BackHandler(onBack = close)
    Scaffold(topBar = { TopAppBar(
        title = { Text(if (id > 0) "Sửa ghi chú" else "Thêm đường") },
        navigationIcon = { IconButton(close) { Icon(Icons.Default.ArrowBack, null) } },
        actions = { IconButton({ if (name.isNotBlank()) vm.save(id, name, house, note, tags, images, close) }) { Icon(Icons.Default.Check, "Lưu") } }
    ) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Tên đường *") }, singleLine = true)
            OutlinedTextField(house, { house = it }, Modifier.fillMaxWidth(), label = { Text("Số nhà") }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text("Ghi chú") })

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
                items(images.toList()) { p ->
                    Box {
                        AsyncImage(p, null, Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        IconButton({ images.remove(p) }, Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Default.Cancel, "Bỏ ảnh") }
                    }
                }
            }
        }
    }
}
