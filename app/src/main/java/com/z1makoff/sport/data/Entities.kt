package com.z1makoff.sport.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rounds: Int,
    @ColumnInfo(name = "rest_sec") val restSec: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "exercises",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workout_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workout_id")],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    val position: Int,
    val name: String,
    val reps: String,
)

@Entity(
    tableName = "attempts",
    foreignKeys = [ForeignKey(
        entity = WorkoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["workout_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workout_id")],
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "total_ms") val totalMs: Long,
    /** Cumulative time at the end of each step, comma-separated; empty for manual entries. */
    val splits: String,
    val manual: Boolean,
)

data class WorkoutSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "best_ms") val bestMs: Long?,
    val attempts: Int,
    @ColumnInfo(name = "last_at") val lastAt: Long?,
)
