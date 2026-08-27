package com.example.optireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import com.example.optireader.ImportUiState
import com.example.optireader.OptireaderViewModel
import com.example.optireader.R
import com.example.optireader.scan.DiscoveredSource

@Composable
fun ImportScreen(
    state: ImportUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onImport: () -> Unit,
    onScanDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = OptireaderViewModel.filterItems(state.items, state.query)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
        OutlinedButton(
            onClick = onScanDownloads,
            enabled = !state.isScanning && !state.isImporting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.import_scan_downloads))
        }
        state.scanSourceDescription?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text(stringResource(R.string.import_search_hint)) },
            singleLine = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onSelectAll,
                enabled = visible.isNotEmpty() && !state.isScanning,
            ) {
                Text(stringResource(R.string.import_select_all))
            }
            OutlinedButton(
                onClick = onSelectNone,
                enabled = state.selectedKeys.isNotEmpty() && !state.isScanning,
            ) {
                Text(stringResource(R.string.import_select_none))
            }
            Button(
                onClick = onImport,
                enabled = state.selectedKeys.isNotEmpty() && !state.isImporting && !state.isScanning,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.import_import_selected))
            }
        }
        if (state.isScanning || state.isImporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when {
            state.isScanning -> {
                Text(
                    text = stringResource(R.string.import_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.items.isEmpty() -> {
                Text(
                    text = stringResource(R.string.import_scan_help_api33),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            visible.isEmpty() -> {
                Text(
                    text = stringResource(R.string.import_no_matches),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(visible, key = { it.key }) { item ->
                DiscoveredRow(
                    item = item,
                    checked = item.key in state.selectedKeys,
                    onToggle = { onToggle(item.key) },
                )
            }
        }
        }
    }
}

@Composable
private fun DiscoveredRow(
    item: DiscoveredSource,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
