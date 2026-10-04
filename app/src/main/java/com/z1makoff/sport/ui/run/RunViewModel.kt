package com.z1makoff.sport.ui.run

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.z1makoff.sport.audio.Cues
import com.z1makoff.sport.core.AttemptTimes
import com.z1makoff.sport.core.ExerciseSpec
import com.z1makoff.sport.core.Step
import com.z1makoff.sport.core.bestAttempt
import com.z1makoff.sport.core.buildSteps
import com.z1makoff.sport.core.decodeSplits
import com.z1makoff.sport.core.encodeSplits
import com.z1makoff.sport.core.ghostDeltaMs
import com.z1makoff.sport.core.goalMs
import com.z1makoff.sport.data.AttemptEntity
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.service.TimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { Loading, Ready, Countdown, Running, Paused, Finished }

data class RunResult(val totalMs: Long, val previousBestMs: Long?, val attemptId: Long, val saved: Boolean = true) {
    val isRecord get() = previousBestMs == null || totalMs < previousBestMs
}

class RunViewModel(app: Application, private val dao: SportDao, private val workoutId: Long) : AndroidViewModel(app) {
    private val cues = Cues(app)

    var phase by mutableStateOf(Phase.Loading); private set
    var name by mutableStateOf(""); private set
    var steps by mutableStateOf(emptyList<Step>()); private set
    var rounds by mutableStateOf(1); private set
    private var restSec = 0
    var recordTotalMs by mutableStateOf<Long?>(null); private set
    var recordSplitsMs by mutableStateOf(emptyList<Long>()); private set
    val goalMs get() = goalMs(recordTotalMs)

    var countdown by mutableStateOf(3); private set
    var elapsedMs by mutableLongStateOf(0L); private set
    val splits = mutableStateListOf<Long>()
    /** Elapsed time at which the current rest ends; null when not resting. */
    var restUntilMs by mutableStateOf<Long?>(null); private set
    var result by mutableStateOf<RunResult?>(null); private set

    private var startedAtWall = 0L
    private var startedAt = 0L
    private var pausedAt = 0L
    private var pausedTotal = 0L
    private var ticker: Job? = null
    private var countdownJob: Job? = null
    private var lastRestBeepSec = -1L

    val currentStep: Step? get() = steps.getOrNull(splits.size)
    val nextStep: Step? get() = steps.getOrNull(splits.size + 1)
    val ghostMs: Long? get() = ghostDeltaMs(elapsedMs, splits, recordSplitsMs, steps.size)

    init {
        viewModelScope.launch {
            val w = dao.workoutOnce(workoutId) ?: return@launch
            val ex = dao.exercisesOnce(workoutId)
            name = w.name
            rounds = w.rounds
            restSec = w.restSec
            steps = buildSteps(ex.map { ExerciseSpec(it.name, it.reps) }, w.rounds)
            loadRecord()
            phase = Phase.Ready
        }
    }

    private suspend fun loadRecord() {
        val best = bestAttempt(dao.attemptsOnce(workoutId).map { AttemptTimes(it.id, it.totalMs, decodeSplits(it.splits)) })
        recordTotalMs = best?.totalMs
        recordSplitsMs = best?.splitsMs.orEmpty()
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun computeElapsed(): Long =
        if (phase == Phase.Paused) pausedAt - startedAt - pausedTotal else now() - startedAt - pausedTotal

    fun start() {
        if (phase != Phase.Ready || steps.isEmpty()) return
        phase = Phase.Countdown
        countdownJob = viewModelScope.launch {
            for (n in 3 downTo 1) {
                countdown = n
                cues.say(n.toString())
                delay(1000)
            }
            startedAtWall = System.currentTimeMillis()
            startedAt = now()
            pausedTotal = 0
            phase = Phase.Running
            cues.beep(long = true)
            announce(steps.first(), prefix = "Старт. ")
            TimerService.show(getApplication(), name, 0, running = true)
            startTicker()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                elapsedMs = computeElapsed()
                checkRest()
                delay(50)
            }
        }
    }

    private fun checkRest() {
        val until = restUntilMs ?: return
        if (phase != Phase.Running) return
        val left = until - elapsedMs
        val leftSec = (left + 999) / 1000
        if (leftSec in 1..3 && leftSec != lastRestBeepSec) {
            lastRestBeepSec = leftSec
            cues.beep()
        }
        if (left <= 0) endRest()
    }

    private fun endRest() {
        restUntilMs = null
        lastRestBeepSec = -1
        cues.beep(long = true)
        currentStep?.let { announce(it) }
    }

    private fun announce(step: Step, prefix: String = "") {
        val round = if (rounds > 1 && step.exerciseIndex == 0) "Раунд ${step.round + 1}. " else ""
        cues.say(prefix + round + step.name + if (step.reps.isNotBlank()) ", ${step.reps}" else "")
    }

    /** The big button: marks the current exercise done, or skips the rest. */
    fun tap() {
        if (phase != Phase.Running) return
        if (restUntilMs != null) { endRest(); return }
        val t = computeElapsed()
        elapsedMs = t
        splits.add(t)
        cues.vibrate()
        if (splits.size >= steps.size) { finish(t); return }
        val next = steps[splits.size]
        if (next.exerciseIndex == 0 && restSec > 0) {
            restUntilMs = t + restSec * 1000L
            cues.say("Отдых $restSec секунд")
        } else {
            announce(next)
        }
    }

    fun undo() {
        if (phase != Phase.Running && phase != Phase.Paused) return
        if (splits.isEmpty()) return
        restUntilMs = null
        splits.removeAt(splits.lastIndex)
        cues.vibrate(40)
    }

    fun pause() {
        if (phase != Phase.Running) return
        pausedAt = now()
        phase = Phase.Paused
        elapsedMs = computeElapsed()
        TimerService.show(getApplication(), name, elapsedMs, running = false)
    }

    fun resume() {
        if (phase != Phase.Paused) return
        pausedTotal += now() - pausedAt
        phase = Phase.Running
        TimerService.show(getApplication(), name, computeElapsed(), running = true)
    }

    private fun finish(totalMs: Long) {
        ticker?.cancel()
        phase = Phase.Finished
        TimerService.stop(getApplication())
        val prev = recordTotalMs
        viewModelScope.launch {
            val id = dao.insertAttempt(
                AttemptEntity(
                    workoutId = workoutId, startedAt = startedAtWall, totalMs = totalMs,
                    splits = encodeSplits(splits.toList()), manual = false,
                )
            )
            val r = RunResult(totalMs, prev, id)
            result = r
            cues.say(
                when {
                    prev == null -> "Финиш. Первый рекорд"
                    r.isRecord -> "Новый рекорд! Быстрее на ${spokenSeconds(prev - totalMs)}"
                    else -> "Финиш. До рекорда не хватило ${spokenSeconds(totalMs - prev)}"
                }
            )
        }
    }

    private fun spokenSeconds(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s < 60) "$s сек" else "${s / 60} мин ${s % 60} сек"
    }

    /** Throws away the finished attempt ("Не сохранять"). */
    fun discardResult() {
        val r = result ?: return
        viewModelScope.launch {
            dao.attemptsOnce(workoutId).firstOrNull { it.id == r.attemptId }?.let { dao.deleteAttempt(it) }
            result = r.copy(saved = false)
        }
    }

    fun abort() {
        countdownJob?.cancel()
        ticker?.cancel()
        TimerService.stop(getApplication())
    }

    override fun onCleared() {
        countdownJob?.cancel()
        ticker?.cancel()
        if (phase != Phase.Finished) TimerService.stop(getApplication())
        cues.release()
    }
}
