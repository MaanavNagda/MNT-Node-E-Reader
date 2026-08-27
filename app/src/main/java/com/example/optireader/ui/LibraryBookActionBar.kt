package com.example.optireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.optireader.R

@Composable
fun LibraryBookActionBar(
    onOpenBookInfo: () -> Unit,
    onOpenStats: () -> Unit,
    onMoveToTbr: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveFromFolder: (() -> Unit)? = null,
    showStatsButton: Boolean = true,
    showMoveToTbrButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenBookInfo) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Book info",
                )
            }
            if (showStatsButton) {
                IconButton(onClick = onOpenStats) {
                    Icon(
                        Icons.Outlined.QueryStats,
                        contentDescription = stringResource(R.string.library_action_stats_cd),
                    )
                }
            }
            if (showMoveToTbrButton) {
                IconButton(onClick = onMoveToTbr) {
                    Icon(
                        Icons.Outlined.MoveToInbox,
                        contentDescription = stringResource(R.string.library_action_tbr_cd),
                    )
                }
            }
            if (onRemoveFromFolder != null) {
                IconButton(onClick = onRemoveFromFolder) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = stringResource(R.string.folder_remove_book_cd),
                    )
                }
            }
            IconButton(onClick = onRequestDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.library_action_delete_cd),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.library_action_close_cd),
                )
            }
        }
    }
}
