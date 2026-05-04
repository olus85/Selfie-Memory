package com.example.selfiememory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SelfieEntity::class], version = 1, exportSchema = false)
abstract class SelfieDatabase : RoomDatabase() {
    abstract fun selfieDao(): SelfieDao

    companion object {
        @Volatile
        private var INSTANCE: SelfieDatabase? = null

        fun getInstance(context: Context): SelfieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SelfieDatabase::class.java,
                    "selfie_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}