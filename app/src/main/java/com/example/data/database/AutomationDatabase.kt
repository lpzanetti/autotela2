package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.AutomationAction
import com.example.data.model.AutomationTemplate

@Database(
    entities = [AutomationTemplate::class, AutomationAction::class],
    version = 1,
    exportSchema = false
)
abstract class AutomationDatabase : RoomDatabase() {
    abstract val dao: AutomationDao

    companion object {
        @Volatile
        private var INSTANCE: AutomationDatabase? = null

        fun getInstance(context: Context): AutomationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDatabase::class.java,
                    "automation_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
