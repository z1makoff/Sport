package com.z1makoff.sport.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutEntity::class, ExerciseEntity::class, AttemptEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SportDatabase : RoomDatabase() {
    abstract fun dao(): SportDao

    companion object {
        fun create(context: Context): SportDatabase =
            Room.databaseBuilder(context, SportDatabase::class.java, "sport.db").build()
    }
}
