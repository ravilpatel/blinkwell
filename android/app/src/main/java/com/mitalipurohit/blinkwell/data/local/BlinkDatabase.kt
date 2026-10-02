package com.mitalipurohit.blinkwell.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mitalipurohit.blinkwell.data.local.dao.BlinkDao
import com.mitalipurohit.blinkwell.data.local.entity.BlinkMinuteLogEntity
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity

@Database(
    entities = [BlinkSessionEntity::class, BlinkMinuteLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlinkDatabase : RoomDatabase() {

    abstract fun blinkDao(): BlinkDao

    companion object {
        @Volatile
        private var INSTANCE: BlinkDatabase? = null

        fun getInstance(context: Context): BlinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlinkDatabase::class.java,
                    "blinkwell_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
