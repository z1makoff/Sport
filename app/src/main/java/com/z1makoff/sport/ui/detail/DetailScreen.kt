package com.z1makoff.sport.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z1makoff.sport.core.formatDelta
import com.z1makoff.sport.core.formatTime
import com.z1makoff.sport.core.goalMs
import com.z1makoff.sport.core.parseTime
import com.z1makoff.sport.data.AttemptEntity
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.ui.formatDateTime
import com.z1makoff.sport.ui.theme.Ahead
import com.z1makoff.sport.ui.theme.Behind
import kotlinx.coroutines.launch

/** What to show in the time dialog: a new manual result, or an existing attempt to fix. */
private sealed interface TimeDialog {
    data object New : TimeDialog
    data class Fix(val attempt: AttemptEntity) : TimeDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(dao: SportDao, workoutId: Long, onStart: () -> Unit, onEdit: () -> Unit, onBack: () -> Unit) {
    val workoutFlow = remember(workoutId) { dao.workout(workoutId) }
    val exercisesFlow = remember(workoutId) { dao.exercises(workoutId) }
    val attemptsFlow = remember(workoutId) { dao.attempts(workoutId) }
    val workout by workoutFlow.collectAsStateWithLifecycle(initialValue = null)
    val exercises by exercisesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val attempts by attemptsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<TimeDialog?>(null) }

    val best = attempts.minWithOrNull(compareBy<AttemptEntity> { it.totalMs }.thenBy { it.id })
    // Attempts that set a new record at the moment they were made.
    val recordIds = remember(attempts) {
        var bestSoFar = Long.MAX_VALUE
        attempts.sortedWith(compareBy<AttemptEntity> { it.startedAt }.thenBy { it.id }).mapNotNull {
            if (it.totalMs < bestSoFar) { bestSoFar = it.totalMs; it.id } else null
        }.toSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(workout?.name ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    IconButton(onClick = { dialog = TimeDialog.New }) { Icon(Icons.Default.Add, "Добавить результат вручную") }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Изменить") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onStart,
                enabled = exercises.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(64.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Text("  Старт", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        },
    ) { pad ->
        val w = workout ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (best == null) {
                            Text("Рекорда пока нет", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Первая попытка станет рекордом",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Сегодня побей", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatTime(goalMs(best.totalMs)!!),
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 56.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text("Рекорд ${formatTime(best.totalMs)} · ${formatDateTime(best.startedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                val roundsText = if (w.rounds > 1) "${w.rounds} раунда(ов)" else "1 раунд"
                val restText = if (w.restSec > 0 && w.rounds > 1) ", отдых ${w.restSec} сек" else ""
                Text("$roundsText$restText", style = MaterialTheme.typography.titleMedium)
                Text(
                    exercises.joinToString("\n") { e -> if (e.reps.isBlank()) "• ${e.name}" else "• ${e.name} — ${e.reps}" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (attempts.isNotEmpty()) {
                item { Text("История", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(attempts, key = { it.id }) { a ->
                    AttemptRow(a, best, isRecordThen = a.id in recordIds) { dialog = TimeDialog.Fix(a) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }

    dialog?.let { d ->
        TimeEditDialog(
            title = if (d is TimeDialog.Fix) "Исправить время" else "Добавить результат",
            initial = (d as? TimeDialog.Fix)?.attempt?.totalMs,
            canDelete = d is TimeDialog.Fix,
            onDismiss = { dialog = null },
            onSave = { ms ->
                scope.launch {
                    when (d) {
                        TimeDialog.New -> dao.insertAttempt(
                            AttemptEntity(
                                workoutId = workoutId, startedAt = System.currentTimeMillis(),
                                totalMs = ms, splits = "", manual = true,
                            )
                        )
                        // Splits no longer match a hand-corrected total, so they are dropped.
                        is TimeDialog.Fix -> dao.updateAttempt(
                            d.attempt.copy(totalMs = ms, splits = if (ms == d.attempt.totalMs) d.attempt.splits else "", manual = true)
                        )
                    }
                    dialog = null
                }
            },
            onDelete = {
                scope.launch {
                    (d as? TimeDialog.Fix)?.let { dao.deleteAttempt(it.attempt) }
                    dialog = null
                }
            },
        )
    }
}

@Composable
private fun AttemptRow(a: AttemptEntity, best: AttemptEntity?, isRecordThen: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(a.startedAt))
            val tags = buildList {
                if (a.id == best?.id) add("рекорд")
                else if (isRecordThen) add("был рекордом")
                if (a.manual) add("вручную")
            }
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (a.id == best?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatTime(a.totalMs), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (best != null && a.id != best.id) {
                Text(formatDelta(a.totalMs - best.totalMs), fontFamily = FontFamily.Monospace, color = Behind, style = MaterialTheme.typography.labelMedium)
            } else if (a.id == best?.id) {
                Text("лучшее", color = Ahead, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TimeEditDialog(
    title: String,
    initial: Long?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    var min by remember { mutableStateOf(initial?.let { (it / 60_000).toString() } ?: "") }
    var sec by remember { mutableStateOf(initial?.let { ((it / 1000) % 60).toString() } ?: "") }
    val parsed = parseTime("${min.ifBlank { "0" }}:${sec.ifBlank { "0" }.padStart(2, '0')}")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = min, onValueChange = { min = it.filter(Char::isDigit).take(3) },
                    label = { Text("мин") }, singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(":", fontSize = 24.sp)
                OutlinedTextField(
                    value = sec, onValueChange = { sec = it.filter(Char::isDigit).take(2) },
                    label = { Text("сек") }, singleLine = true, modifier = Modifier.weight(1f),
                    isError = (sec.toIntOrNull() ?: 0) >= 60,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Сохранить") } },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = onDelete) { Text("Удалить попытку", color = Behind) }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
