package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AppLimitEntity::class, FocusScheduleEntity::class, SecurityLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SocialCareDatabase : RoomDatabase() {
    abstract fun dao(): SocialCareDao

    companion object {
        @Volatile
        private var INSTANCE: SocialCareDatabase? = null

        fun getDatabase(context: Context): SocialCareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SocialCareDatabase::class.java,
                    "social_care_db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
