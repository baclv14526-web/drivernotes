package com.driver.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Loại ghi chú. `key` là giá trị lưu trong database/file xuất, không đổi dù sau này đổi tên hiển thị. */
enum class PlaceType(val key: String, val label: String, val icon: ImageVector) {
    ROAD("road", "Đường", Icons.Default.Directions),
    OFFICE("office", "Tòa nhà văn phòng", Icons.Default.Business),
    SCHOOL("school", "Trường học", Icons.Default.School),
    AGENCY("agency", "Cơ quan", Icons.Default.AccountBalance),
    HOSPITAL("hospital", "Bệnh viện", Icons.Default.LocalHospital),
    APARTMENT("apartment", "Chung cư", Icons.Default.Apartment),
    MARKET("market", "Chợ / Siêu thị", Icons.Default.Storefront),
    OTHER("other", "Khác", Icons.Default.Place);

    companion object {
        fun fromKey(k: String) = values().firstOrNull { it.key == k } ?: OTHER
    }
}
