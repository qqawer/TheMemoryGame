package iss.nus.edu.sg.fragments.courseassignment.thememorygame.db

import android.content.Context
import androidx.room.*
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.model.FetchHistory

@Dao
interface HistoryDao {
    @Query("SELECT * FROM fetch_history ORDER BY timestamp DESC")
    suspend fun getAllHistory(): List<FetchHistory>

    @Query("SELECT * FROM fetch_history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): FetchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: FetchHistory)
}

@Database(entities = [FetchHistory::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "memory_game_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
