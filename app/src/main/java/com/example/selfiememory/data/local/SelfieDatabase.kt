package com.example.selfiememory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SelfieEntity::class], version = 2, exportSchema = false)
abstract class SelfieDatabase : RoomDatabase() {
    abstract fun selfieDao(): SelfieDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selfies ADD COLUMN mediaUri TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: SelfieDatabase? = null

        fun getInstance(context: Context): SelfieDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SelfieDatabase::class.java,
                    "selfie_database"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
