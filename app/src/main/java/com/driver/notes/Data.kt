package com.driver.notes

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity
data class Street(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val houseNumber: String = "",
    val note: String = "",
    val tags: String = "",              // các hashtag, ngăn cách bằng dấu phẩy
    val updatedAt: Long = System.currentTimeMillis()
) { fun tagList() = tags.split(",").filter { it.isNotBlank() } }

@Entity(
    foreignKeys = [ForeignKey(Street::class, ["id"], ["streetId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("streetId")]
)
data class StreetImage(@PrimaryKey(autoGenerate = true) val id: Long = 0, val streetId: Long, val path: String)

data class StreetWithImages(
    @Embedded val street: Street,
    @Relation(parentColumn = "id", entityColumn = "streetId") val images: List<StreetImage>
)

@Dao
interface StreetDao {
    @Transaction @Query("SELECT * FROM Street") fun all(): Flow<List<StreetWithImages>>
    @Transaction @Query("SELECT * FROM Street WHERE id=:id") suspend fun get(id: Long): StreetWithImages?
    @Insert suspend fun insert(s: Street): Long
    @Update suspend fun update(s: Street)
    @Delete suspend fun delete(s: Street)
    @Insert suspend fun addImages(list: List<StreetImage>)
    @Query("DELETE FROM StreetImage WHERE streetId=:id") suspend fun clearImages(id: Long)
}

@Database(entities = [Street::class, StreetImage::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): StreetDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context) = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "notes.db").build().also { inst = it }
        }
    }
}
