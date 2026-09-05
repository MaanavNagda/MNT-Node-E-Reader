package com.example.mntnode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.example.mntnode.FirstImportWizardItem
import com.example.mntnode.FirstImportWizardReadBefore
import com.example.mntnode.R
import com.example.mntnode.scan.DiscoveredSource

@Composable
fun FirstImportWizardDialog(
    sources: List<DiscoveredSource>,
    onDismiss: () -> Unit,
    onConfirm: (List<FirstImportWizardItem>) -> Unit,
) {
    val readBeforeByKey = remember(sources) {
        mutableStateMapOf<String, FirstImportWizardReadBefore>()
    }
    var ratingDialogKey by remember { mutableStateOf<String?>(null) }
    var ratingText by remember { mutableStateOf("") }
    var timesText by remember { mutableStateOf("1") }

    fun openRatingDialog(key: String) {
        val existing = readBeforeByKey[key]
        ratingText = existing?.rating10?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        timesText = (existing?.timesRead ?: 1).toString()
        ratingDialogKey = key
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.first_import_wizard_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.first_import_wizard_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(sources, key = { it.key }) { src ->
                        val checked =
                            readBeforeByKey[src.key] != null || ratingDialogKey == src.key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = checked,
                                    onValueChange = { next ->
                                        if (next) {
                                            openRatingDialog(src.key)
                                        } else {
                                            readBeforeByKey.remove(src.key)
                                            if (ratingDialogKey == src.key) {
                                                ratingDialogKey = null
                                            }
                                        }
                                    },
                                    role = Role.Checkbox,
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = src.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (checked) {
                                    val rb = readBeforeByKey[src.key]
                                    if (rb != null) {
                                        val ratingLabel = rb.rating10?.let { r ->
                                            String.format(Locale.US, "%.1f", r)
                                        } ?: stringResource(R.string.rating_na)
                                        Text(
                                            text = stringResource(
                                                R.string.first_import_read_before_summary,
                                                ratingLabel,
                                                rb.timesRead,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val list = sources.map { src ->
                        FirstImportWizardItem(
                            source = src,
                            readBefore = readBeforeByKey[src.key],
                        )
                    }
                    onConfirm(list)
                },
                enabled = ratingDialogKey == null,
            ) {
                Text(stringResource(R.string.first_import_confirm_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.first_import_wizard_cancel))
            }
        },
    )

    ratingDialogKey?.let { key ->
        val src = sources.firstOrNull { it.key == key }
        if (src != null) {
            AlertDialog(
                onDismissRequest = {
                    readBeforeByKey.remove(key)
                    ratingDialogKey = null
                },
                title = { Text(stringResource(R.string.first_import_rating_dialog_title)) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            stringResource(R.string.first_import_read_before_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ratingText,
                            onValueChange = { ratingText = it },
                            label = { Text(stringResource(R.string.first_import_rating_hint)) },
                            placeholder = { Text(stringResource(R.string.rating_na)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = timesText,
                            onValueChange = { v -> timesText = v.filter { ch -> ch.isDigit() } },
                            label = { Text(stringResource(R.string.first_import_times_read_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = ratingText.trim()
                            val isNA = trimmed.isEmpty() ||
                                trimmed.equals("NA", ignoreCase = true) ||
                                trimmed.equals("N/A", ignoreCase = true)
                            val rRating: Float? = if (isNA) {
                                null
                            } else {
                                trimmed.toFloatOrNull()?.coerceIn(0f, 10f)
                            }
                            val t = timesText.toIntOrNull()?.coerceAtLeast(1)
                            if (t == null) return@TextButton
                            if (!isNA && rRating == null) return@TextButton
                            readBeforeByKey[key] = FirstImportWizardReadBefore(
                                rating10 = rRating,
                                timesRead = t,
                            )
                            ratingDialogKey = null
                        },
                    ) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            readBeforeByKey.remove(key)
                            ratingDialogKey = null
                        },
                    ) {
                        Text(stringResource(R.string.first_import_rating_dialog_skip))
                    }
                },
            )
        }
    }
}
