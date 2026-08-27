package com.example.optireader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.optireader.R
import com.example.optireader.data.BookEntity
import com.example.optireader.data.ShelfFolderEntity
import com.example.optireader.data.ShelfItem
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContentsScreen(
    folderId: Long,
    folderFlow: Flow<ShelfFolderEntity?>,
    booksFlow: Flow<List<BookEntity>>,
    showReadingProgress: Boolean,
    isDayTheme: Boolean,
    useAmoledDark: Boolean,
    onBookClick: (Long) -> Unit,
    onBack: () -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onShelfOrderInFolderCommitted: (List<String>) -> Unit,
    onBookLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val folder by folderFlow.collectAsState(initial = null)
    val books by booksFlow.collectAsState(initial = emptyList())
    var hadFolder by remember(folderId) { mutableStateOf(false) }
    LaunchedEffect(folder) {
        if (folder != null) hadFolder = true
        else if (hadFolder) onBack()
    }
    val defaultName = stringResource(R.string.folder_name_default)
    var showRename by remember { mutableStateOf(false) }
    var renameDraft by remember(folderId) { mutableStateOf("") }

    val displayName = folder?.name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.folder_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text(stringResource(R.string.folder_rename_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFolder(folderId, renameDraft)
                        showRename = false
                    },
                ) {
                    Text(stringResource(R.string.folder_rename_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.folder_rename_cancel))
                }
            },
        )
    }

    val shelfItems = books.map { ShelfItem.BookTile(it) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier.clickable {
                            renameDraft = folder?.name?.trim() ?: ""
                            showRename = true
                        },
                    ) {
                        Text(
                            text = displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(R.string.folder_inside_book_count, books.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.folder_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LibraryScreen(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            shelfItems = shelfItems,
            showReadingProgress = showReadingProgress,
            isDayTheme = isDayTheme,
            useAmoledDark = useAmoledDark,
            onBookClick = onBookClick,
            onOpenFolder = { },
            onShelfOrderCommitted = onShelfOrderInFolderCommitted,
            onMergeBookOntoTarget = { _, _ -> },
            dragAndMergeEnabled = true,
            allowMergeOntoOtherTiles = false,
            onBookLongPress = onBookLongPress,
            showRatingStamp = true,
            emptyMessageResId = R.string.folder_empty,
        )
    }
}
