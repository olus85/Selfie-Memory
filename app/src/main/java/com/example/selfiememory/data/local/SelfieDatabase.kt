package com.example.selfiememory.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SelfieEntity::class], version = 3, exportSchema = false)
abstract class SelfieDatabase : RoomDatabase() {
    abstract fun selfieDao(): SelfieDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selfies ADD COLUMN mediaUri TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selfies ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE selfies ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE selfies ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE selfies ADD COLUMN trashedAt INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_selfies_timestamp ON selfies(timestamp)")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
