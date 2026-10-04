package com.z1makoff.sport.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.z1makoff.sport.data.ExerciseEntity
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.data.WorkoutEntity
import kotlinx.coroutines.launch

class ExerciseDraft(name: String = "", reps: String = "") {
    var name by mutableStateOf(name)
    var reps by mutableStateOf(reps)
}

class EditWorkoutViewModel(private val dao: SportDao, private val workoutId: Long) : ViewModel() {
    var loaded by mutableStateOf(workoutId == 0L)
        private set
    var name by mutableStateOf("")
    var rounds by mutableStateOf("1")
    var restSec by mutableStateOf("0")
    val exercises = mutableStateListOf(ExerciseDraft())
    private var createdAt = System.currentTimeMillis()

    init {
        if (workoutId != 0L) viewModelScope.launch {
            dao.workoutOnce(workoutId)?.let { w ->
                name = w.name
                rounds = w.rounds.toString()
                restSec = w.restSec.toString()
                createdAt = w.createdAt
            }
            val ex = dao.exercisesOnce(workoutId)
            exercises.clear()
            exercises.addAll(ex.map { ExerciseDraft(it.name, it.reps) }.ifEmpty { listOf(ExerciseDraft()) })
            loaded = true
        }
    }

    val roundsValue get() = rounds.toIntOrNull()?.takeIf { it in 1..99 }
    val restValue get() = restSec.toIntOrNull()?.takeIf { it in 0..3600 }
    val canSave get() = name.isNotBlank() && roundsValue != null && restValue != null &&
        exercises.any { it.name.isNotBlank() }

    suspend fun save(): Long {
        val w = WorkoutEntity(
            id = workoutId, name = name.trim(), rounds = roundsValue!!, restSec = restValue!!, createdAt = createdAt,
        )
        val ex = exercises.filter { it.name.isNotBlank() }.map {
            ExerciseEntity(workoutId = workoutId, position = 0, name = it.name.trim(), reps = it.reps.trim())
        }
        return dao.saveWorkout(w, ex)
    }

    suspend fun delete() = dao.deleteWorkout(workoutId)

    fun move(from: Int, to: Int) {
        if (to !in exercises.indices) return
        val e = exercises.removeAt(from)
        exercises.add(to, e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutScreen(
    dao: SportDao,
    workoutId: Long,
    onDone: (Long?) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val vm: EditWorkoutViewModel = viewModel(factory = viewModelFactory { initializer { EditWorkoutViewModel(dao, workoutId) } })
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workoutId == 0L) "Новая тренировка" else "Изменить тренировку") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = {
                    if (workoutId != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Удалить") }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { scope.launch { onDone(vm.save()) } },
                enabled = vm.loaded && vm.canSave,
                modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp),
            ) { Text("Сохранить") }
        },
    ) { pad ->
        if (!vm.loaded) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.name, onValueChange = { vm.name = it },
                    label = { Text("Название") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vm.rounds, onValueChange = { vm.rounds = it.filter(Char::isDigit).take(2) },
                        label = { Text("Раунды") }, singleLine = true, isError = vm.roundsValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = vm.restSec, onValueChange = { vm.restSec = it.filter(Char::isDigit).take(4) },
                        label = { Text("Отдых между раундами, сек") }, singleLine = true, isError = vm.restValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
            item {
                Text("Упражнения одного раунда", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(vm.exercises) { i, e ->
                ExerciseRow(
                    index = i, draft = e, count = vm.exercises.size,
                    onUp = { vm.move(i, i - 1) }, onDown = { vm.move(i, i + 1) },
                    onRemove = { if (vm.exercises.size > 1) vm.exercises.removeAt(i) else { e.name = ""; e.reps = "" } },
                )
            }
            item {
                OutlinedButton(onClick = { vm.exercises.add(ExerciseDraft()) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить упражнение")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить тренировку?") },
            text = { Text("Вместе с ней удалятся все попытки и рекорд.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { vm.delete(); confirmDelete = false; onDeleted() } }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ExerciseRow(
    index: Int, draft: ExerciseDraft, count: Int,
    onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft.name, onValueChange = { draft.name = it },
                    label = { Text("Упражнение") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.reps, onValueChange = { draft.reps = it },
                    label = { Text("Сколько (например 20 или 1 мин)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onUp, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Выше") }
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Убрать") }
                IconButton(onClick = onDown, enabled = index < count - 1) { Icon(Icons.Default.KeyboardArrowDown, "Ниже") }
            }
        }
    }
}
