package com.driver.notes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale
import kotlin.math.*

/** Đọc "21.0285, 105.8542" (dán từ Google Maps cũng được). Trả null nếu sai định dạng. */
fun parseCoords(text: String): Pair<Double, Double>? {
    val p = text.trim().split(Regex("[,;\\s]+")).filter { it.isNotEmpty() }
    if (p.size != 2) return null
    val lat = p[0].toDoubleOrNull() ?: return null
    val lng = p[1].toDoubleOrNull() ?: return null
    return if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
}

/** Mở Google Maps tại tọa độ; nếu máy không có app thì mở bằng trình duyệt. */
fun openMaps(ctx: Context, lat: Double, lng: Double, label: String) {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))
        .setPackage("com.google.android.apps.maps")
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
    try { ctx.startActivity(geo) } catch (e: ActivityNotFoundException) {
        try { ctx.startActivity(web) } catch (e2: ActivityNotFoundException) {
            Toast.makeText(ctx, "Không tìm thấy ứng dụng bản đồ", Toast.LENGTH_SHORT).show()
        }
    }
}

/** Khoảng cách (mét) giữa 2 tọa độ theo công thức Haversine. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.getDefault(), "%.1f km", m / 1000)
