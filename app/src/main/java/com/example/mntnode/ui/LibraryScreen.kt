package com.example.mntnode.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.mntnode.R
import com.example.mntnode.data.BookEntity
import com.example.mntnode.data.ShelfItem
import com.example.mntnode.ui.theme.woodGrainShelfRow
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private data class ShelfDragState(
    val gridKey: String,
    val listIndex: Int,
    /** Set for standalone [ShelfItem.BookTile] only; used to merge into another tile. */
    val bookIdForMerge: Long?,
    val startInCard: Offset,
)

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    val item = removeAt(fromIndex)
    var dest = if (toIndex > fromIndex) toIndex - 1 else toIndex
    dest = dest.coerceIn(0, size)
    add(dest, item)
}

private fun nearestDropIndex(
    fingerWin: Offset,
    orderedKeys: List<String>,
    bounds: Map<String, Rect>,
    draggedKey: String,
    fromIndex: Int,
): Int {
    val keyed = orderedKeys.mapIndexed { idx, key -> key to idx }.filter { it.first != draggedKey }
    if (keyed.isEmpty()) return fromIndex
    var bestKey = orderedKeys.first { it != draggedKey }
    var bestD = Float.MAX_VALUE
    for ((key, _) in keyed) {
        val r = bounds[key] ?: continue
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        val d = hypot((fingerWin.x - cx).toDouble(), (fingerWin.y - cy).toDouble()).toFloat()
        if (d < bestD) {
            bestD = d
            bestKey = key
        }
    }
    val idx = orderedKeys.indexOf(bestKey)
    val r = bounds[bestKey] ?: return fromIndex
    val cy = (r.top + r.bottom) / 2f
    val cx = (r.left + r.right) / 2f
    val before = fingerWin.y < cy || (kotlin.math.abs(fingerWin.y - cy) < 24f && fingerWin.x < cx)
    return if (before) idx else idx + 1
}

/** Horizontal nudge so non-dragged tiles make room for the insertion preview. */
private fun previewShiftDp(
    flatIndex: Int,
    fromIndex: Int,
    previewDropIndex: Int?,
    slotWidth: Dp,
): Dp {
    if (previewDropIndex == null || fromIndex < 0) return 0.dp
    val i = flatIndex
    val from = fromIndex
    val to = previewDropIndex
    return when {
        from < to -> if (i in (from + 1) until to) -slotWidth else 0.dp
        from > to -> if (i in to until from) slotWidth else 0.dp
        else -> 0.dp
    }
}

private data class ShelfScrollbarMetrics(
    val totalRows: Int,
    val visibleRows: Int,
    val thumbHeightFrac: Float,
    val thumbOffsetFrac: Float,
    val maxFirstRow: Int,
)

private data class ShelfScrollbarDragAnchor(
    val firstRowIndex: Int,
    val maxFirstRow: Int,
    val trackHeightPx: Float,
    val thumbHeightPx: Float,
)

private class ShelfThumbDragGestureState {
    var anchor: ShelfScrollbarDragAnchor? = null
    var accumulatedY: Float = 0f
}

/** Wide enough for a comfortable drag handle; thumb sits inside with horizontal inset. */
private val ShelfScrollbarTrackWidth = 32.dp

/** Minimum thumb height so the handle stays easy to grab (proportional height can be tiny on long shelves). */
private val ShelfScrollbarMinThumbHeight = 56.dp

private const val ShelfScrollbarHideAfterIdleMs = 850L

private fun shelfScrollbarMetrics(state: LazyListState): ShelfScrollbarMetrics? {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return null
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val visibleRows = visible.size.coerceAtLeast(1)
    val thumbHeightFrac = (visibleRows.toFloat() / total.toFloat()).coerceIn(0.08f, 1f)
    val maxFirstRow = (total - visibleRows).coerceAtLeast(0)
    val thumbOffsetFrac =
        if (maxFirstRow == 0) {
            0f
        } else {
            (state.firstVisibleItemIndex.toFloat() / maxFirstRow.toFloat()).coerceIn(0f, 1f)
        }
    return ShelfScrollbarMetrics(total, visibleRows, thumbHeightFrac, thumbOffsetFrac, maxFirstRow)
}

@Composable
private fun ShelfLazyColumnScrollbar(
    listState: LazyListState,
    visible: Boolean,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val onInteractionLatest = rememberUpdatedState(onInteraction)
    val density = LocalDensity.current

    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val thumbColor = MaterialTheme.colorScheme.primary
    val onThumb = MaterialTheme.colorScheme.onPrimary

    val thumbDragState = remember { ShelfThumbDragGestureState() }

    BoxWithConstraints(modifier = modifier.width(ShelfScrollbarTrackWidth)) {
        val trackHpx = with(density) { maxHeight.toPx() }
        val metrics by remember {
            derivedStateOf { shelfScrollbarMetrics(listState) }
        }
        val m = metrics ?: return@BoxWithConstraints

        val proportionalThumb = maxHeight * m.thumbHeightFrac
        val thumbHeight =
            maxOf(proportionalThumb, ShelfScrollbarMinThumbHeight).coerceAtMost(maxHeight)
        val thumbOffset = (maxHeight - thumbHeight) * m.thumbOffsetFrac
        val thumbHpx = with(density) { thumbHeight.toPx() }

        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(trackColor)
                    .pointerInput(m.totalRows, m.maxFirstRow) {
                        detectTapGestures(
                            onPress = {
                                onInteractionLatest.value()
                                tryAwaitRelease()
                            },
                            onTap = { tapOffset ->
                                if (m.maxFirstRow <= 0) return@detectTapGestures
                                val frac = (tapOffset.y / size.height).coerceIn(0f, 1f)
                                val target =
                                    (frac * m.maxFirstRow).roundToInt().coerceIn(0, m.maxFirstRow)
                                scope.launch { listState.scrollToItem(target) }
                            },
                        )
                    },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 5.dp)
                    .offset(y = thumbOffset)
                    .height(thumbHeight)
                    .pointerInput(trackHpx, thumbHpx) {
                        detectDragGestures(
                            onDragStart = {
                                onInteractionLatest.value()
                                val info = listState.layoutInfo
                                val total = info.totalItemsCount
                                val vis = info.visibleItemsInfo.size.coerceAtLeast(1)
                                val maxFirst = (total - vis).coerceAtLeast(0)
                                thumbDragState.accumulatedY = 0f
                                thumbDragState.anchor = ShelfScrollbarDragAnchor(
                                    firstRowIndex = listState.firstVisibleItemIndex,
                                    maxFirstRow = maxFirst,
                                    trackHeightPx = trackHpx,
                                    thumbHeightPx = thumbHpx,
                                )
                            },
                            onDrag = { _, dragAmount ->
                                val anchor = thumbDragState.anchor ?: return@detectDragGestures
                                if (anchor.maxFirstRow <= 0) return@detectDragGestures
                                thumbDragState.accumulatedY += dragAmount.y
                                val usable =
                                    (anchor.trackHeightPx - anchor.thumbHeightPx).coerceAtLeast(1f)
                                val deltaRows = anchor.maxFirstRow * (thumbDragState.accumulatedY / usable)
                                val newFirst =
                                    (anchor.firstRowIndex + deltaRows).roundToInt()
                                        .coerceIn(0, anchor.maxFirstRow)
                                scope.launch { listState.scrollToItem(newFirst) }
                            },
                            onDragEnd = {
                                thumbDragState.anchor = null
                                thumbDragState.accumulatedY = 0f
                            },
                            onDragCancel = {
                                thumbDragState.anchor = null
                                thumbDragState.accumulatedY = 0f
                            },
                        )
                    },
                shape = RoundedCornerShape(14.dp),
                color = thumbColor,
                contentColor = onThumb,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = onThumb.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    shelfItems: List<ShelfItem>,
    showReadingProgress: Boolean,
    isDayTheme: Boolean,
    useAmoledDark: Boolean,
    onBookClick: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onShelfOrderCommitted: (List<String>) -> Unit,
    onMergeBookOntoTarget: (draggedBookId: Long, targetGridKey: String) -> Unit,
    dragAndMergeEnabled: Boolean = true,
    /** When false, dropping a book on another tile only reorders (no new folder / add-to-folder). */
    allowMergeOntoOtherTiles: Boolean = true,
    onBookLongPress: ((Long) -> Unit)? = null,
    /** When true (main library), show rating as a colour stamp when [BookEntity.rating10] is set. */
    showRatingStamp: Boolean = false,
    emptyMessageResId: Int = R.string.library_empty,
    modifier: Modifier = Modifier,
) {
    if (shelfItems.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(emptyMessageResId),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val ordered = remember { mutableStateListOf<ShelfItem>() }
    LaunchedEffect(shelfItems) {
        val incoming = shelfItems
        val incomingKeys = incoming.map { it.gridKey }
        val currentKeys = ordered.map { it.gridKey }
        when {
            ordered.isEmpty() -> ordered.addAll(incoming)
            incomingKeys.toSet() != currentKeys.toSet() -> {
                ordered.clear()
                ordered.addAll(incoming)
            }
            !dragAndMergeEnabled && incomingKeys != currentKeys -> {
                // Non-draggable surfaces (like TBR) should follow caller-provided sort order exactly.
                ordered.clear()
                ordered.addAll(incoming)
            }
            else -> {
                val byKey = incoming.associateBy { it.gridKey }
                for (i in ordered.indices) {
                    byKey[ordered[i].gridKey]?.let { fresh -> ordered[i] = fresh }
                }
            }
        }
    }

    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    var dragState by remember { mutableStateOf<ShelfDragState?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPreviewDropIndex by remember { mutableStateOf<Int?>(null) }
    /** (dragged gridKey, fromIndex) — set during deferred book drag before [dragState] commits, and while dragging. */
    var dragPreviewContext by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val haptics = LocalHapticFeedback.current

    val onDragPreviewContextChange: (Pair<String, Int>?) -> Unit = { ctx ->
        dragPreviewContext = ctx
        dragPreviewDropIndex = ctx?.second
    }

    val onDragFingerMove: (Offset) -> Unit = body@{ fingerWin ->
        val ctx = dragPreviewContext ?: return@body
        val (draggedKey, fromIndex) = ctx
        val keys = ordered.map { it.gridKey }
        dragPreviewDropIndex = nearestDropIndex(fingerWin, keys, itemBounds, draggedKey, fromIndex)
    }

    val rows = ordered.chunked(3)
    val listState = rememberLazyListState()
    val canScrollShelf by remember {
        derivedStateOf {
            listState.canScrollForward || listState.canScrollBackward
        }
    }
    var shelfScrollbarRevealed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shelfScrollbarHideJob = remember { object { var job: Job? = null } }
    fun cancelShelfScrollbarHide() {
        shelfScrollbarHideJob.job?.cancel()
        shelfScrollbarHideJob.job = null
    }
    fun revealShelfScrollbar() {
        cancelShelfScrollbarHide()
        shelfScrollbarRevealed = true
    }
    fun scheduleShelfScrollbarHide() {
        cancelShelfScrollbarHide()
        shelfScrollbarHideJob.job = scope.launch {
            delay(ShelfScrollbarHideAfterIdleMs)
            shelfScrollbarRevealed = false
            shelfScrollbarHideJob.job = null
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress) {
                    revealShelfScrollbar()
                } else {
                    scheduleShelfScrollbarHide()
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            shelfScrollbarHideJob.job?.cancel()
        }
    }
    val showShelfScrollbar = canScrollShelf && shelfScrollbarRevealed
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 24.dp,
                end = if (showShelfScrollbar) 30.dp else 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                count = rows.size,
                key = { rowIndex -> rows[rowIndex].joinToString("|") { it.gridKey } },
            ) { rowIndex ->
            val row = rows[rowIndex]
            val rowSeed = rowIndex * 7919 + rows.size
            Column(
                Modifier
                    .fillMaxWidth()
                    .woodGrainShelfRow(
                        isDay = isDayTheme,
                        isAmoled = useAmoledDark,
                        rowSeed = rowSeed,
                    )
                    .animateItem(),
            ) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    val slotWidth = (maxWidth - 12.dp * 2) / 3f
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEachIndexed { colIndex, item ->
                            val flatIndex = rowIndex * 3 + colIndex
                            val rawShift = previewShiftDp(
                                flatIndex = flatIndex,
                                fromIndex = dragPreviewContext?.second ?: -1,
                                previewDropIndex = dragPreviewDropIndex,
                                slotWidth = slotWidth,
                            )
                            val animatedShift by animateDpAsState(
                                targetValue = rawShift,
                                label = "shelfPreviewShift",
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .offset(x = animatedShift),
                            ) {
                                ShelfLibraryCell(
                                    item = item,
                                    ordered = ordered,
                                    itemBounds = itemBounds,
                                    dragState = dragState,
                                    dragOffset = dragOffset,
                                    onDragOffsetChange = { dragOffset = it },
                                    onDragStateChange = { dragState = it },
                                    onDragPreviewContextChange = onDragPreviewContextChange,
                                    onDragFingerMove = onDragFingerMove,
                                    showReadingProgress = showReadingProgress,
                                    onBookClick = onBookClick,
                                    onOpenFolder = onOpenFolder,
                                    onShelfOrderCommitted = onShelfOrderCommitted,
                                    onMergeBookOntoTarget = onMergeBookOntoTarget,
                                    haptics = haptics,
                                    dragAndMergeEnabled = dragAndMergeEnabled,
                                    allowMergeOntoOtherTiles = allowMergeOntoOtherTiles,
                                    onBookLongPress = onBookLongPress,
                                    showRatingStamp = showRatingStamp,
                                )
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Spacer(Modifier.height(22.dp))
                Spacer(Modifier.height(18.dp))
            }
        }
        }
        ShelfLazyColumnScrollbar(
            listState = listState,
            visible = showShelfScrollbar,
            onInteraction = { revealShelfScrollbar() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfLibraryCell(
    item: ShelfItem,
    ordered: MutableList<ShelfItem>,
    itemBounds: SnapshotStateMap<String, Rect>,
    dragState: ShelfDragState?,
    dragOffset: Offset,
    onDragOffsetChange: (Offset) -> Unit,
    onDragStateChange: (ShelfDragState?) -> Unit,
    onDragPreviewContextChange: (Pair<String, Int>?) -> Unit,
    onDragFingerMove: (Offset) -> Unit,
    showReadingProgress: Boolean,
    onBookClick: (Long) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onShelfOrderCommitted: (List<String>) -> Unit,
    onMergeBookOntoTarget: (draggedBookId: Long, targetGridKey: String) -> Unit,
    haptics: HapticFeedback,
    dragAndMergeEnabled: Boolean,
    allowMergeOntoOtherTiles: Boolean,
    onBookLongPress: ((Long) -> Unit)?,
    showRatingStamp: Boolean,
) {
    var layoutCoords by remember(item.gridKey) { mutableStateOf<LayoutCoordinates?>(null) }

    val dragOffsetLatest = rememberUpdatedState(dragOffset)
    val dragStateLatest = rememberUpdatedState(dragState)
    val onDragFingerMoveLatest = rememberUpdatedState(onDragFingerMove)
    val onBookLongPressLatest = rememberUpdatedState(onBookLongPress)

    val dispatchDragStateChange: (ShelfDragState?) -> Unit = { s ->
        if (s != null) {
            onDragPreviewContextChange(s.gridKey to s.listIndex)
        } else {
            onDragPreviewContextChange(null)
        }
        onDragStateChange(s)
    }

    /** Long-press menu vs drag share the same detector; defer drag state until finger moves (no per-frame state). */
    val pendingAfterLongPress = remember(item.gridKey) {
        object {
            var drag: ShelfDragState? = null
            var offset: Offset = Offset.Zero
        }
    }

    DisposableEffect(item.gridKey) {
        onDispose { itemBounds.remove(item.gridKey) }
    }

    val isDraggingThis = dragState?.gridKey == item.gridKey
    val elevation by animateDpAsState(
        targetValue = if (isDraggingThis) 8.dp else 0.dp,
        label = "shelfDrag",
    )

    val layoutModifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { coords ->
            layoutCoords = coords
            if (coords.isAttached) {
                itemBounds[item.gridKey] = coords.boundsInWindow()
            }
        }
        .graphicsLayer {
            translationX = if (isDraggingThis) dragOffset.x else 0f
            translationY = if (isDraggingThis) dragOffset.y else 0f
            alpha = if (isDraggingThis) 0.42f else 1f
        }
        .zIndex(if (isDraggingThis) 2f else 0f)

    val tapOnLift: (() -> Unit)? = when (item) {
        is ShelfItem.FolderTile -> {
            { onOpenFolder(item.folderId) }
        }
        else -> null
    }

    // pointerInput is inner (after clickable). Optional short-tap on folders opens them; books use
    // deferred drag when a long-press menu is enabled.
    val dragGesturesModifier = Modifier.pointerInput(item.gridKey) {
        detectDragGesturesAfterLongPressWithOptionalTap(
            onTapWhenLiftBeforeLongPress = tapOnLift,
            onDragMove = dragMoveBody@{ change, amount ->
                change.consume()
                val pending = pendingAfterLongPress.drag
                val committed = dragStateLatest.value
                if (pending != null && committed == null && pending.gridKey == item.gridKey) {
                    val newPending = pendingAfterLongPress.offset + amount
                    pendingAfterLongPress.offset = newPending
                    val moved = hypot(newPending.x.toDouble(), newPending.y.toDouble()).toFloat()
                    val minCommitDistance = viewConfiguration.touchSlop * 2.5f
                    val lc = layoutCoords
                    if (lc != null && lc.isAttached) {
                        val fingerWin = lc.localToWindow(pending.startInCard + newPending)
                        onDragFingerMoveLatest.value(fingerWin)
                    }
                    if (moved >= minCommitDistance) {
                        dispatchDragStateChange(pending)
                        onDragOffsetChange(newPending)
                        pendingAfterLongPress.drag = null
                        pendingAfterLongPress.offset = Offset.Zero
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    return@dragMoveBody
                }
                val newOffset = dragOffsetLatest.value + amount
                onDragOffsetChange(newOffset)
                val state = dragStateLatest.value
                if (state != null && state.gridKey == item.gridKey) {
                    val lc = layoutCoords
                    if (lc != null && lc.isAttached) {
                        val fingerWin = lc.localToWindow(state.startInCard + newOffset)
                        onDragFingerMoveLatest.value(fingerWin)
                    }
                }
            },
            onDragStart = { startLocal ->
                val index = ordered.indexOfFirst { it.gridKey == item.gridKey }
                if (index < 0) return@detectDragGesturesAfterLongPressWithOptionalTap
                onDragOffsetChange(Offset.Zero)
                val mergeId = (item as? ShelfItem.BookTile)?.book?.id
                val state = ShelfDragState(
                    gridKey = item.gridKey,
                    listIndex = index,
                    bookIdForMerge = mergeId,
                    startInCard = startLocal,
                )
                val deferForMenu = onBookLongPressLatest.value != null && item is ShelfItem.BookTile
                if (deferForMenu) {
                    pendingAfterLongPress.drag = state
                    pendingAfterLongPress.offset = Offset.Zero
                    onDragPreviewContextChange(item.gridKey to index)
                } else {
                    dispatchDragStateChange(state)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }
            },
            onDragEnd = {
                if (pendingAfterLongPress.drag != null && dragStateLatest.value == null) {
                    val bookTile = item as? ShelfItem.BookTile
                    if (bookTile != null) {
                        onBookLongPressLatest.value?.invoke(bookTile.book.id)
                    }
                    pendingAfterLongPress.drag = null
                    pendingAfterLongPress.offset = Offset.Zero
                    onDragPreviewContextChange(null)
                    return@detectDragGesturesAfterLongPressWithOptionalTap
                }
                val state = dragStateLatest.value
                val total = dragOffsetLatest.value
                dispatchDragStateChange(null)
                onDragOffsetChange(Offset.Zero)
                pendingAfterLongPress.drag = null
                pendingAfterLongPress.offset = Offset.Zero
                if (state == null) return@detectDragGesturesAfterLongPressWithOptionalTap
                val dragDistance = hypot(total.x.toDouble(), total.y.toDouble()).toFloat()
                val minCommitDistance = viewConfiguration.touchSlop * 2.5f
                if (dragDistance < minCommitDistance) {
                    return@detectDragGesturesAfterLongPressWithOptionalTap
                }
                val lc = layoutCoords
                if (lc == null || !lc.isAttached) return@detectDragGesturesAfterLongPressWithOptionalTap
                val fingerWin = lc.localToWindow(state.startInCard + total)

                val mergeTarget = itemBounds.entries
                    .filter { (key, rect) -> key != state.gridKey && rect.contains(fingerWin) }
                    .minByOrNull { (_, rect) -> rect.width * rect.height }
                    ?.key

                if (
                    state.bookIdForMerge != null &&
                    mergeTarget != null &&
                    dragAndMergeEnabled &&
                    allowMergeOntoOtherTiles
                ) {
                    onMergeBookOntoTarget(state.bookIdForMerge, mergeTarget)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    return@detectDragGesturesAfterLongPressWithOptionalTap
                }

                val keys = ordered.map { it.gridKey }
                val from = state.listIndex
                val to = nearestDropIndex(fingerWin, keys, itemBounds, state.gridKey, from)
                if (from != to) {
                    ordered.move(from, to)
                    onShelfOrderCommitted(ordered.map { it.gridKey })
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            },
            onDragCancel = {
                pendingAfterLongPress.drag = null
                pendingAfterLongPress.offset = Offset.Zero
                dispatchDragStateChange(null)
                onDragOffsetChange(Offset.Zero)
            },
        )
    }

    when (item) {
        is ShelfItem.BookTile -> {
            val bookGestures = when {
                onBookLongPress != null && !dragAndMergeEnabled ->
                    Modifier.combinedClickable(
                        onClick = { onBookClick(item.book.id) },
                        onLongClick = { onBookLongPress.invoke(item.book.id) },
                    )
                !dragAndMergeEnabled ->
                    Modifier.clickable { onBookClick(item.book.id) }
                onBookLongPress != null ->
                    Modifier.combinedClickable(
                        onClick = { onBookClick(item.book.id) },
                        onLongClick = { onBookLongPress.invoke(item.book.id) },
                    ).then(dragGesturesModifier)
                else ->
                    Modifier.clickable { onBookClick(item.book.id) }.then(dragGesturesModifier)
            }
            Box(layoutModifier) {
                BookCoverCard(
                    book = item.book,
                    showReadingProgress = showReadingProgress,
                    showRatingStamp = showRatingStamp,
                    elevation = elevation,
                    modifier = Modifier.fillMaxWidth(),
                    fullTileGestures = bookGestures,
                )
            }
        }
        is ShelfItem.FolderTile -> {
            // Shelf tile: no custom folder name shown—only cover grid (+N). Name exists only inside folder.
            val folderCd = stringResource(R.string.library_folder_tile_cd, item.books.size)
            val folderGesture = if (dragAndMergeEnabled) {
                dragGesturesModifier
            } else {
                Modifier.clickable { onOpenFolder(item.folderId) }
            }
            Box(layoutModifier) {
                FolderShelfTile(
                    books = item.books,
                    showReadingProgress = showReadingProgress,
                    elevation = elevation,
                    folderContentDescription = folderCd,
                    modifier = Modifier.fillMaxWidth(),
                    fullTileGestures = folderGesture,
                )
            }
        }
    }
}

@Composable
private fun BookCoverCard(
    book: BookEntity,
    showReadingProgress: Boolean,
    showRatingStamp: Boolean,
    elevation: Dp,
    modifier: Modifier = Modifier,
    fullTileGestures: Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(fullTileGestures),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column {
            val cover = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
            val hasCover = cover != null
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (hasCover) {
                        AsyncImage(
                            model = cover,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    if (showReadingProgress && book.readProgress01 > 0f) {
                        CoverReadingProgressBar(
                            progress01 = book.readProgress01,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    if (showRatingStamp && book.rating10 != null) {
                        RatingCornerStamp(
                            rating10 = book.rating10,
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                    DiagonalFormatCornerTag(
                        label = book.format.uppercase(),
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderShelfTile(
    books: List<BookEntity>,
    showReadingProgress: Boolean,
    elevation: Dp,
    folderContentDescription: String,
    modifier: Modifier = Modifier,
    fullTileGestures: Modifier,
) {
    Card(
        modifier = modifier
            .semantics { contentDescription = folderContentDescription }
            .fillMaxWidth()
            .then(fullTileGestures),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FolderMiniCell(books.getOrNull(0), Modifier.weight(1f), showReadingProgress)
                        FolderMiniCell(books.getOrNull(1), Modifier.weight(1f), showReadingProgress)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FolderMiniCell(books.getOrNull(2), Modifier.weight(1f), showReadingProgress)
                        FolderMiniCell(books.getOrNull(3), Modifier.weight(1f), showReadingProgress)
                    }
                }
            }
            if (books.size > 4) {
                Text(
                    text = stringResource(R.string.library_folder_more, books.size - 4),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderMiniCell(
    book: BookEntity?,
    modifier: Modifier,
    showReadingProgress: Boolean,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            val cover = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(2.dp),
                )
            }
            if (showReadingProgress && book.readProgress01 > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.readProgress01.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/**
 * Like long-press-then-drag, but if the finger lifts before long-press fires, [onTapWhenLiftBeforeLongPress]
 * runs (used so folder tiles can open on tap while [pointerInput] is innermost).
 */
private suspend fun PointerInputScope.detectDragGesturesAfterLongPressWithOptionalTap(
    onTapWhenLiftBeforeLongPress: (() -> Unit)? = null,
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDragMove: (PointerInputChange, Offset) -> Unit,
) {
    awaitEachGesture {
        try {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPressChange = awaitLongPressOrCancellation(down.id)
            if (longPressChange == null) {
                onTapWhenLiftBeforeLongPress?.invoke()
                return@awaitEachGesture
            }
            onDragStart.invoke(longPressChange.position)
            if (
                drag(longPressChange.id) { change ->
                    onDragMove(change, change.position - change.previousPosition)
                    change.consume()
                }
            ) {
                currentEvent.changes.fastForEach { ch ->
                    if (!ch.pressed && ch.previousPressed) ch.consume()
                }
                onDragEnd()
            } else {
                onDragCancel()
            }
        } catch (c: CancellationException) {
            onDragCancel()
            throw c
        }
    }
}

private fun lerpStampColor(a: Color, b: Color, t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * x,
        green = a.green + (b.green - a.green) * x,
        blue = a.blue + (b.blue - a.blue) * x,
        alpha = a.alpha + (b.alpha - a.alpha) * x,
    )
}

/**
 * Blood red at ~0.1, then orange/yellow, then green; after ~9 the green gets darker toward 10.
 */
private fun ratingStampBackgroundColor(rating10: Float): Color {
    val r = rating10.coerceIn(0.1f, 10f)
    val stops = listOf(
        0.1f to Color(0.45f, 0f, 0.06f),
        3.5f to Color(1f, 0.48f, 0f),
        5.5f to Color(1f, 0.9f, 0.22f),
        7f to Color(0.5f, 0.85f, 0.42f),
        8.5f to Color(0.08f, 0.48f, 0.22f),
        10f to Color(0.03f, 0.30f, 0.14f),
    )
    var i = 0
    while (i < stops.size - 1 && r > stops[i + 1].first) i++
    val (a, ca) = stops[i]
    val (b, cb) = stops[i + 1]
    val t = if (b == a) 0f else (r - a) / (b - a)
    return lerpStampColor(ca, cb, t)
}

private fun contrastOnRatingStamp(bg: Color): Color {
    val l = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (l > 0.62f) Color(0.1f, 0.1f, 0.12f) else Color.White
}

@Composable
private fun RatingCornerStamp(
    rating10: Float,
    modifier: Modifier = Modifier,
) {
    val bg = ratingStampBackgroundColor(rating10)
    val fg = contrastOnRatingStamp(bg)
    val label = remember(rating10) {
        DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).format(rating10.toDouble())
    }
    Box(
        modifier = modifier
            .padding(top = 6.dp, start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
internal fun DiagonalFormatCornerTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = modifier
            .padding(top = 6.dp, end = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CoverReadingProgressBar(
    progress01: Float,
    modifier: Modifier = Modifier,
) {
    val p = progress01.coerceIn(0f, 1f)
    val percent = (p * 100f).roundToInt().coerceIn(0, 100)
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        val fullW = maxWidth
        val pillW = 40.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.58f),
                    ),
                )
                .padding(top = 12.dp, bottom = 6.dp, start = 6.dp, end = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.3f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(p)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                val xOffset = (fullW * p - pillW / 2).coerceIn(0.dp, (fullW - pillW).coerceAtLeast(0.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = xOffset, y = (-11).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xE6000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
