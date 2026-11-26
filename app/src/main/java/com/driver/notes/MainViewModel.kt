package com.driver.notes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

enum class Sort(val label: String) { NEWEST("Mới nhất"), NAME("Tên A→Z"), HOUSE("Số nhà"), TYPE("Loại") }

val PRESET_TAGS = listOf("Đang sửa chữa", "Hỏng đèn giao thông", "Tắc đường", "Ngập nước",
    "Cấm đường", "Đường một chiều", "Đường hẹp", "Cấm rẽ trái", "Cấm rẽ phải",
    "Đường 1km", "Đường 5km", "Đường 10km", "Đường 20km", "Dễ đón khách", "Camera phạt nguội",
    "Có chỗ đỗ xe", "Cấm dừng đỗ", "Cổng sau")

/** Bỏ dấu tiếng Việt để tìm kiếm không cần gõ dấu. */
fun String.fold(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").replace('đ', 'd')

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(Sort.NEWEST)
    val tagFilter = MutableStateFlow<String?>(null)
    val typeFilter = MutableStateFlow<String?>(null)

    val items: StateFlow<List<StreetWithImages>> =
        combine(dao.all(), query, sort, tagFilter, typeFilter) { all, q, s, tag, ty ->
            val k = q.fold().trim()
            all.filter { w ->
                val st = w.street
                (tag == null || tag in st.tagList()) && (ty == null || st.type == ty) &&
                    (k.isEmpty() || listOf(st.name, st.houseNumber, st.note, st.tags, PlaceType.fromKey(st.type).label).any { it.fold().contains(k) })
            }.sortedWith(when (s) {
                Sort.NEWEST -> compareByDescending { it.street.updatedAt }
                Sort.NAME -> compareBy { it.street.name.fold() }
                Sort.HOUSE -> compareBy { it.street.houseNumber.fold() }
                Sort.TYPE -> compareBy<StreetWithImages>({ PlaceType.fromKey(it.street.type).ordinal }, { it.street.name.fold() })
            })
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun load(id: Long) = dao.get(id)

    /** images: đường dẫn file cũ hoặc content:// uri mới; ảnh mới được copy vào bộ nhớ app. */
    fun save(id: Long, type: String, name: String, house: String, note: String, tags: List<String>, lat: Double?, lng: Double?, images: List<String>, done: () -> Unit) =
        viewModelScope.launch {
            val paths = withContext(Dispatchers.IO) { images.map { if (it.startsWith("content://")) copyIn(Uri.parse(it)) else it } }
            val s = Street(if (id > 0) id else 0, name.trim(), house.trim(), note.trim(), tags.joinToString(","), lat = lat, lng = lng, type = type)
            val sid = if (id > 0) { dao.update(s); dao.clearImages(id); id } else dao.insert(s)
            dao.addImages(paths.map { StreetImage(streetId = sid, path = it) })
            done()
        }

    fun delete(w: StreetWithImages) = viewModelScope.launch {
        dao.delete(w.street); withContext(Dispatchers.IO) { w.images.forEach { File(it.path).delete() } }
    }

    private fun copyIn(uri: Uri): String =
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use { saveStream(it) }

    private fun saveStream(i: InputStream): String {
        val dir = File(getApplication<Application>().filesDir, "img").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        f.outputStream().use { i.copyTo(it) }
        return f.absolutePath
    }

    /** Xuất toàn bộ ghi chú + ảnh ra 1 file .zip (data.json + thư mục images/). */
    fun exportData(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        val msg = withContext(Dispatchers.IO) {
            runCatching {
                val all = dao.all().first()
                val arr = JSONArray()
                getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { os ->
                    ZipOutputStream(os.buffered()).use { zip ->
                        all.forEach { w ->
                            val imgs = JSONArray()
                            w.images.forEach { im ->
                                val f = File(im.path)
                                if (f.exists()) {
                                    val n = "images/${w.street.id}_${im.id}.jpg"
                                    zip.putNextEntry(ZipEntry(n)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                                    imgs.put(n)
                                }
                            }
                            val s = w.street
                            arr.put(JSONObject().put("name", s.name).put("houseNumber", s.houseNumber).put("note", s.note)
                                .put("tags", s.tags).put("lat", s.lat ?: JSONObject.NULL).put("lng", s.lng ?: JSONObject.NULL)
                                .put("type", s.type).put("updatedAt", s.updatedAt).put("images", imgs))
                        }
                        zip.putNextEntry(ZipEntry("data.json"))
                        zip.write(JSONObject().put("version", 1).put("streets", arr).toString().toByteArray())
                        zip.closeEntry()
                    }
                }
                "Đã xuất ${all.size} ghi chú"
            }.getOrElse { "Lỗi xuất dữ liệu: ${it.message}" }
        }
        done(msg)
    }

    /** Nhập từ file .zip đã xuất. Ghi chú trùng (cùng tên, số nhà, thời gian) sẽ được bỏ qua. */
    fun importData(uri: Uri, done: (String) -> Unit) = viewModelScope.launch {
        val msg = withContext(Dispatchers.IO) {
            runCatching {
                val app = getApplication<Application>()
                val tmp = File(app.cacheDir, "import.zip")
                app.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                try {
                    ZipFile(tmp).use { zip ->
                        val entry = zip.getEntry("data.json") ?: error("Không phải file sao lưu hợp lệ")
                        val arr = JSONObject(zip.getInputStream(entry).readBytes().toString(Charsets.UTF_8)).getJSONArray("streets")
                        val existing = dao.list().map { Triple(it.name, it.houseNumber, it.updatedAt) }.toMutableSet()
                        var added = 0; var skipped = 0
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val key = Triple(o.getString("name"), o.optString("houseNumber"), o.optLong("updatedAt"))
                            if (key in existing) { skipped++; continue }
                            val paths = mutableListOf<String>()
                            o.optJSONArray("images")?.let { imgs ->
                                for (j in 0 until imgs.length()) {
                                    val e = zip.getEntry(imgs.getString(j)) ?: continue
                                    paths += saveStream(zip.getInputStream(e))
                                }
                            }
                            val sid = dao.insert(Street(
                                name = key.first, houseNumber = key.second, note = o.optString("note"), tags = o.optString("tags"),
                                lat = if (o.isNull("lat")) null else o.getDouble("lat"),
                                lng = if (o.isNull("lng")) null else o.getDouble("lng"),
                                type = o.optString("type").ifBlank { "road" }, updatedAt = key.third))
                            dao.addImages(paths.map { StreetImage(streetId = sid, path = it) })
                            existing += key; added++
                        }
                        "Đã nhập $added ghi chú" + if (skipped > 0) ", bỏ qua $skipped ghi chú trùng" else ""
                    }
                } finally { tmp.delete() }
            }.getOrElse { "Lỗi nhập dữ liệu: ${it.message}" }
        }
        done(msg)
    }
}
