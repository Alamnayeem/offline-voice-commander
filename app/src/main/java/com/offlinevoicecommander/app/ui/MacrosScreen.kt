package com.offlinevoicecommander.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinevoicecommander.app.macro.Macro
import com.offlinevoicecommander.app.macro.MacroStep
import com.offlinevoicecommander.app.macro.StepType
import com.offlinevoicecommander.app.state.AppState
import com.offlinevoicecommander.app.viewmodel.MainViewModel
import java.util.UUID

@Composable
fun MacrosScreen(vm: MainViewModel) {
    val macros by vm.macros.collectAsState()
    val lastResult by AppState.lastResult.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Macro?>(null) }
    var deleting by remember { mutableStateOf<Macro?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Macros", style = MaterialTheme.typography.headlineSmall)
        Text(
            "A macro is a list of steps. Say its trigger phrase (while listening) to run it. " +
                "Macros are saved only on this phone.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { creating = true }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("New macro", modifier = Modifier.padding(start = 8.dp))
        }
        if (lastResult.isNotBlank()) Text(lastResult, style = MaterialTheme.typography.bodySmall)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(macros, key = { it.id }) { macro ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(macro.name, style = MaterialTheme.typography.titleMedium)
                        Text("Say: \"${macro.trigger}\"", style = MaterialTheme.typography.bodyMedium)
                        macro.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. ${step.describe()}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { vm.runMacro(macro) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Run macro")
                            }
                            IconButton(onClick = { editing = macro }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit macro")
                            }
                            IconButton(onClick = { deleting = macro }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete macro")
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        MacroEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = {
                vm.saveMacro(it)
                creating = false
            }
        )
    }
    editing?.let { current ->
        MacroEditorDialog(
            initial = current,
            onDismiss = { editing = null },
            onSave = {
                vm.saveMacro(it)
                editing = null
            }
        )
    }
    deleting?.let { current ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete macro?") },
            text = { Text("\"${current.name}\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMacro(current.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MacroEditorDialog(initial: Macro?, onDismiss: () -> Unit, onSave: (Macro) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var trigger by remember { mutableStateOf(initial?.trigger ?: "") }
    val steps = remember { mutableStateListOf<MacroStep>().apply { initial?.let { addAll(it.steps) } } }
    var newType by remember { mutableStateOf(StepType.OPEN_APP) }
    var newArg by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New macro" else "Edit macro") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Macro name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Voice trigger, e.g. start facebook mode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Steps", style = MaterialTheme.typography.titleSmall)
                if (steps.isEmpty()) Text("No steps yet.", style = MaterialTheme.typography.bodySmall)
                steps.forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}. ${step.describe()}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            if (i > 0) {
                                val tmp = steps[i]
                                steps[i] = steps[i - 1]
                                steps[i - 1] = tmp
                            }
                        }) { Text("Up") }
                        TextButton(onClick = {
                            if (i < steps.size - 1) {
                                val tmp = steps[i]
                                steps[i] = steps[i + 1]
                                steps[i + 1] = tmp
                            }
                        }) { Text("Down") }
                        IconButton(onClick = { steps.removeAt(i) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove step")
                        }
                    }
                }
                Text("Add a step", style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) { Text(newType.label) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        StepType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    newType = type
                                    newArg = ""
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
                if (newType.needsArg) {
                    OutlinedTextField(
                        value = newArg,
                        onValueChange = { newArg = it },
                        label = { Text(newType.argHint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = {
                    val arg = newArg.trim()
                    when {
                        newType.needsArg && arg.isEmpty() -> error = "Please fill in the step value."
                        newType == StepType.WAIT && arg.toDoubleOrNull() == null ->
                            error = "Wait needs a number of seconds."
                        else -> {
                            steps.add(MacroStep(newType, if (newType.needsArg) arg else ""))
                            newArg = ""
                            error = null
                        }
                    }
                }) { Text("Add step") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Please enter a macro name."
                    trigger.isBlank() -> error = "Please enter a voice trigger."
                    steps.isEmpty() -> error = "Add at least one step."
                    else -> onSave(
                        Macro(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            trigger = trigger.trim(),
                            steps = steps.toList()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
