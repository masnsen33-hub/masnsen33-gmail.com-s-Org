package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Entity(tableName = "dictionary_entries")
data class DictionaryEntryEntity(
    @PrimaryKey val id: String,
    val frenchTerm: String,
    val tamazightTerm: String,
    val tamazightPlural: String,
    val tamazightVariants: String,
    val tifinagh: String,
    val englishMeaning: String,
    val arabicMeaning: String,
    val frenchGloss: String,
    val etymologyNote: String,
    val dialectTags: String,
    val refCodes: String,
    val category: String,
    val sourcePage: Int,
    val aiEnriched: Boolean = false
)

@Dao
interface DictionaryDao {
    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM dictionary_entries ORDER BY frenchTerm COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DictionaryEntryEntity>>

    @Query("SELECT * FROM dictionary_entries WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<DictionaryEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntryEntity>)

    @Query(
        """
        UPDATE dictionary_entries
        SET arabicMeaning = :arabicMeaning,
            englishMeaning = :englishMeaning,
            tifinagh = CASE WHEN :tifinagh != '' THEN :tifinagh ELSE tifinagh END,
            etymologyNote = CASE WHEN :etymologyNote != '' THEN :etymologyNote ELSE etymologyNote END,
            aiEnriched = 1
        WHERE id = :id
        """
    )
    suspend fun updateMultilingualTranslation(
        id: String,
        arabicMeaning: String,
        englishMeaning: String,
        tifinagh: String,
        etymologyNote: String
    )
}

@Database(entities = [DictionaryEntryEntity::class], version = 1, exportSchema = false)
abstract class TasniremtDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var INSTANCE: TasniremtDatabase? = null

        fun getInstance(context: Context): TasniremtDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TasniremtDatabase::class.java,
                    "tasniremt_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class LocalDictionaryRepository(
    private val context: Context,
    private val dao: DictionaryDao
) {
    fun observeAllEntries(): Flow<List<DictionaryEntryEntity>> = dao.observeAll()

    fun observeEntry(id: String): Flow<DictionaryEntryEntity?> = dao.observeById(id)

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        val jsonText = context.assets.open("tasniremt_dictionary.json")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        val array = JSONArray(jsonText)
        val entities = ArrayList<DictionaryEntryEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val variantsArr = obj.optJSONArray("tamazightVariants")
            val variants = if (variantsArr != null) {
                (0 until variantsArr.length()).joinToString(" • ") { variantsArr.getString(it) }
            } else ""
            val dialectsArr = obj.optJSONArray("dialectTags")
            val dialects = if (dialectsArr != null) {
                (0 until dialectsArr.length()).joinToString(", ") { dialectsArr.getString(it) }
            } else ""
            val refsArr = obj.optJSONArray("refCodes")
            val refs = if (refsArr != null) {
                (0 until refsArr.length()).joinToString(", ") { "[${refsArr.getString(it)}]" }
            } else ""

            entities.add(
                DictionaryEntryEntity(
                    id = obj.getString("id"),
                    frenchTerm = obj.getString("frenchTerm"),
                    tamazightTerm = obj.getString("tamazightTerm"),
                    tamazightPlural = obj.optString("tamazightPlural", ""),
                    tamazightVariants = variants,
                    tifinagh = obj.optString("tifinagh", ""),
                    englishMeaning = obj.optString("englishMeaning", ""),
                    arabicMeaning = obj.optString("arabicMeaning", ""),
                    frenchGloss = obj.optString("frenchGloss", ""),
                    etymologyNote = obj.optString("etymologyNote", ""),
                    dialectTags = dialects,
                    refCodes = refs,
                    category = obj.optString("category", "Society & Culture"),
                    sourcePage = obj.optInt("sourcePage", 11),
                    aiEnriched = false
                )
            )
        }
        // Insert in batches so SQLite bindings stay comfortably below limits
        entities.chunked(400).forEach { chunk ->
            dao.insertAll(chunk)
        }
    }

    suspend fun cacheTranslation(
        entryId: String,
        arabicMeaning: String,
        englishMeaning: String,
        tifinagh: String,
        etymologyNote: String
    ) {
        dao.updateMultilingualTranslation(
            id = entryId,
            arabicMeaning = arabicMeaning,
            englishMeaning = englishMeaning,
            tifinagh = tifinagh,
            etymologyNote = etymologyNote
        )
    }
}
