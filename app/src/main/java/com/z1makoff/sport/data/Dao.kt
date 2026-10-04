package com.z1makoff.sport.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SportDao {
    @Query(
        """
        SELECT w.id, w.name, MIN(a.total_ms) AS best_ms, COUNT(a.id) AS attempts, MAX(a.started_at) AS last_at
        FROM workouts w LEFT JOIN attempts a ON a.workout_id = w.id
        GROUP BY w.id ORDER BY w.created_at
        """
    )
    abstract fun summaries(): Flow<List<WorkoutSummary>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract fun workout(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    abstract suspend fun workoutOnce(id: Long): WorkoutEntity?

    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY position")
    abstract fun exercises(workoutId: Long): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE workout_id = :workoutId ORDER BY position")
    abstract suspend fun exercisesOnce(workoutId: Long): List<ExerciseEntity>

    @Query("SELECT * FROM attempts WHERE workout_id = :workoutId ORDER BY started_at DESC")
    abstract fun attempts(workoutId: Long): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE workout_id = :workoutId")
    abstract suspend fun attemptsOnce(workoutId: Long): List<AttemptEntity>

    @Insert abstract suspend fun insertWorkout(w: WorkoutEntity): Long
    @Update abstract suspend fun updateWorkout(w: WorkoutEntity)
    @Query("DELETE FROM workouts WHERE id = :id") abstract suspend fun deleteWorkout(id: Long)

    @Insert abstract suspend fun insertExercises(e: List<ExerciseEntity>)
    @Query("DELETE FROM exercises WHERE workout_id = :workoutId") abstract suspend fun deleteExercises(workoutId: Long)

    @Insert abstract suspend fun insertAttempt(a: AttemptEntity): Long
    @Update abstract suspend fun updateAttempt(a: AttemptEntity)
    @Delete abstract suspend fun deleteAttempt(a: AttemptEntity)

    @Transaction
    open suspend fun saveWorkout(w: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val id = if (w.id == 0L) insertWorkout(w) else { updateWorkout(w); w.id }
        deleteExercises(id)
        insertExercises(exercises.mapIndexed { i, e -> e.copy(id = 0, workoutId = id, position = i) })
        return id
    }
}
