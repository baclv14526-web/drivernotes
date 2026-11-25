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
import java.text.Normalizer
import java.util.UUID

enum class Sort(val label: String) { NEWEST("Mới nhất"), NAME("Tên A→Z"), HOUSE("Số nhà") }

val PRESET_TAGS = listOf("Đang sửa chữa", "Hỏng đèn giao thông", "Tắc đường", "Ngập nước",
    "Cấm đường", "Đường một chiều", "Đường hẹp", "Cấm rẽ trái", "Cấm rẽ phải",
    "Đường 1km", "Đường 5km", "Đường 10km", "Đường 20km", "Dễ đón khách", "Camera phạt nguội")

/** Bỏ dấu tiếng Việt để tìm kiếm không cần gõ dấu. */
fun String.fold(): String = Normalizer.normalize(lowercase(), Normalizer.Form.NFD)
    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").replace('đ', 'd')

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val query = MutableStateFlow("")
    val sort = MutableStateFlow(Sort.NEWEST)
    val tagFilter = MutableStateFlow<String?>(null)

    val items: StateFlow<List<StreetWithImages>> =
        combine(dao.all(), query, sort, tagFilter) { all, q, s, tag ->
            val k = q.fold().trim()
            all.filter { w ->
                val st = w.street
                (tag == null || tag in st.tagList()) &&
                    (k.isEmpty() || listOf(st.name, st.houseNumber, st.note, st.tags).any { it.fold().contains(k) })
            }.sortedWith(when (s) {
                Sort.NEWEST -> compareByDescending { it.street.updatedAt }
                Sort.NAME -> compareBy { it.street.name.fold() }
                Sort.HOUSE -> compareBy { it.street.houseNumber.fold() }
            })
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun load(id: Long) = dao.get(id)

    /** images: đường dẫn file cũ hoặc content:// uri mới; ảnh mới được copy vào bộ nhớ app. */
    fun save(id: Long, name: String, house: String, note: String, tags: List<String>, lat: Double?, lng: Double?, images: List<String>, done: () -> Unit) =
        viewModelScope.launch {
            val paths = withContext(Dispatchers.IO) { images.map { if (it.startsWith("content://")) copyIn(Uri.parse(it)) else it } }
            val s = Street(if (id > 0) id else 0, name.trim(), house.trim(), note.trim(), tags.joinToString(","), lat = lat, lng = lng)
            val sid = if (id > 0) { dao.update(s); dao.clearImages(id); id } else dao.insert(s)
            dao.addImages(paths.map { StreetImage(streetId = sid, path = it) })
            done()
        }

    fun delete(w: StreetWithImages) = viewModelScope.launch {
        dao.delete(w.street); withContext(Dispatchers.IO) { w.images.forEach { File(it.path).delete() } }
    }

    private fun copyIn(uri: Uri): String {
        val dir = File(getApplication<Application>().filesDir, "img").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        getApplication<Application>().contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return f.absolutePath
    }
}
