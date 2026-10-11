package com.z1makoff.sport.ui.run

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.z1makoff.sport.core.GhostStatus
import com.z1makoff.sport.core.Step
import com.z1makoff.sport.core.ghostStatus
import com.z1makoff.sport.core.formatDelta
import com.z1makoff.sport.core.formatTime
import com.z1makoff.sport.core.formatTimeTenths
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.ui.theme.Ahead
import com.z1makoff.sport.ui.theme.Behind

@Composable
fun RunScreen(dao: SportDao, workoutId: Long, onClose: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val vm: RunViewModel = viewModel(factory = viewModelFactory { initializer { RunViewModel(app, dao, workoutId) } })
    var confirmAbort by remember { mutableStateOf(false) }
    val active = vm.phase == Phase.Countdown || vm.phase == Phase.Running || vm.phase == Phase.Paused

    // The screen stays on during the workout; the power button still turns it off
    // and the timer keeps going.
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = active) { confirmAbort = true }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.start() }
    val startWithPermission = {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.start()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        when (vm.phase) {
            Phase.Loading -> Unit
            Phase.Ready -> ReadyView(vm, onStart = startWithPermission, onClose = onClose)
            Phase.Countdown -> CountdownView(vm.countdown, vm.goalMs)
            Phase.Running, Phase.Paused -> ActiveView(vm, onClose = { confirmAbort = true })
            Phase.Finished -> ResultView(vm, onClose = onClose)
        }
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("Прервать тренировку?") },
            text = { Text("Эта попытка не сохранится.") },
            confirmButton = { TextButton(onClick = { confirmAbort = false; vm.abort(); onClose() }) { Text("Прервать", color = Behind) } },
            dismissButton = { TextButton(onClick = { confirmAbort = false }) { Text("Продолжить") } },
        )
    }
}

@Composable
private fun TopRow(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Закрыть") }
    }
}

@Composable
private fun ReadyView(vm: RunViewModel, onStart: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopRow(vm.name, onClose)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val goal = vm.goalMs
            if (goal != null) {
                Text("Побей", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigTime(formatTime(goal), color = MaterialTheme.colorScheme.primary)
                Text("Рекорд ${formatTime(vm.recordTotalMs!!)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.recordWithoutSplits) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "У рекорда нет времени по упражнениям, поэтому по ходу тренировки сравним только с общим временем",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("Первая попытка", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Её время станет твоим рекордом", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "${vm.steps.size} отметок: нажимай «Готово» после каждого упражнения",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(120.dp),
            shape = RoundedCornerShape(20.dp),
        ) { Text("СТАРТ", fontSize = 40.sp, fontWeight = FontWeight.Black) }
    }
}

@Composable
private fun CountdownView(n: Int, goal: Long?) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("$n", fontSize = 200.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        if (goal != null) Text("цель ${formatTime(goal)}", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BigTime(text: String, color: Color = MaterialTheme.colorScheme.onBackground, size: Int = 72) {
    Text(text, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = size.sp, color = color, maxLines = 1)
}

@Composable
private fun GhostLine(vm: RunViewModel) {
    val ghost = vm.ghostMs
    val record = vm.recordTotalMs
    val (text, color) = when {
        ghost != null -> when (ghostStatus(ghost)) {
            GhostStatus.Ahead -> "${formatDelta(ghost)} впереди рекорда" to Ahead
            GhostStatus.Behind -> "${formatDelta(ghost)} позади рекорда" to Behind
            GhostStatus.Even -> "вровень с рекордом" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        record != null && vm.elapsedMs <= record -> "до рекорда ${formatTime(record - vm.elapsedMs)}" to MaterialTheme.colorScheme.onSurfaceVariant
        record != null -> "${formatDelta(vm.elapsedMs - record)} после рекорда" to Behind
        else -> "первая попытка" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ActiveView(vm: RunViewModel, onClose: () -> Unit) {
    val step = vm.currentStep
    val paused = vm.phase == Phase.Paused
    val rest = vm.restUntilMs
    Column(Modifier.fillMaxSize()) {
        TopRow(vm.name, onClose)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BigTime(formatTimeTenths(vm.elapsedMs), color = if (paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground)
            GhostLine(vm)
            vm.goalMs?.let { Text("цель ${formatTime(it)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(16.dp))
        if (step != null) StepCard(vm, step, resting = rest != null, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.weight(1f))
        // The big target: easy to hit from a distance or with sweaty hands.
        val bigColor = when {
            paused -> MaterialTheme.colorScheme.surfaceContainerHigh
            rest != null -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(bigColor)
                .clickable(enabled = !paused) { vm.tap() },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    paused -> Text("ПАУЗА", fontSize = 44.sp, fontWeight = FontWeight.Black)
                    rest != null -> {
                        Text("ОТДЫХ ${formatTime((rest - vm.elapsedMs).coerceAtLeast(0) + 999)}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        vm.restPushText?.let { Text(it, color = Color.Black, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                        Text("нажми, чтобы пропустить", color = Color.Black)
                    }
                    else -> {
                        Text("ГОТОВО", fontSize = 52.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                        Text(if (vm.nextStep == null) "последнее упражнение" else "отметить и дальше", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.undo() }, enabled = vm.splits.isNotEmpty(), modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Отменить отметку")
            }
            Button(
                onClick = { if (paused) vm.resume() else vm.pause() },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
            ) { Text(if (paused) "Продолжить" else "Пауза") }
        }
    }
}

@Composable
private fun StepCard(vm: RunViewModel, step: Step, resting: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp),
    ) {
        val roundText = if (vm.rounds > 1) "Раунд ${step.round + 1} из ${vm.rounds} · " else ""
        Text("$roundText${vm.splits.size + 1} / ${vm.steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (resting) "Дальше: ${step.name}" else step.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        if (step.reps.isNotBlank()) Text(step.reps, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        val next = vm.nextStep
        if (!resting && next != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Дальше: ${next.name}${if (next.reps.isNotBlank()) " · ${next.reps}" else ""}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultView(vm: RunViewModel, onClose: () -> Unit) {
    val r = vm.result
    Column(Modifier.fillMaxSize()) {
        TopRow(vm.name, onClose)
        if (r == null) return@Column
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                val prev = r.previousBestMs
                Text(
                    when {
                        !r.saved -> "Попытка не сохранена"
                        prev == null -> "Первый рекорд!"
                        r.isRecord -> "НОВЫЙ РЕКОРД!"
                        else -> "Финиш"
                    },
                    fontSize = 32.sp, fontWeight = FontWeight.Black,
                    color = if (r.isRecord && r.saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
                BigTime(formatTime(r.totalMs), size = 80)
                Text(formatTimeTenths(r.totalMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                if (prev != null) {
                    val d = r.totalMs - prev
                    Text(
                        if (d < 0) "${formatDelta(d)} к прошлому рекорду ${formatTime(prev)}" else "${formatDelta(d)} к рекорду ${formatTime(prev)}",
                        color = if (d < 0) Ahead else Behind, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            val rec = vm.recordSplitsMs.takeIf { it.size == vm.steps.size && r.previousBestMs != null }
            itemsIndexed(vm.steps) { i, s ->
                val end = vm.splits.getOrNull(i) ?: return@itemsIndexed
                val start = if (i == 0) 0L else vm.splits[i - 1]
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name)
                        if (vm.rounds > 1) Text("раунд ${s.round + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatTime(end - start), fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 16.dp))
                    if (rec != null) {
                        val d = end - rec[i]
                        Text(formatDelta(d), fontFamily = FontFamily.Monospace, color = if (d <= 0) Ahead else Behind)
                    } else {
                        Text(formatTime(end), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (r?.saved == true) {
                OutlinedButton(onClick = { vm.discardResult() }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Не сохранять") }
            }
            Button(onClick = onClose, modifier = Modifier.weight(1f).height(56.dp)) { Text("Готово") }
        }
    }
}
