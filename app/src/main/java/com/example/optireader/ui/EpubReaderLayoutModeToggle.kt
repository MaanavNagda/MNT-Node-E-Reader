package com.example.optireader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.optireader.R

/**
 * Two-part control: **Scroll view** (left) and **Page view** (right). The highlighted side is the active mode.
 *
 * @param pageView `true` = page view, `false` = scroll view.
 */
@Composable
fun EpubReaderLayoutModeToggle(
    pageView: Boolean,
    onPageViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape),
    ) {
        val scrollSelected = !pageView
        val pageSelected = pageView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    if (scrollSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                )
                .clickable { onPageViewChange(false) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.reader_mode_scroll),
                style = MaterialTheme.typography.labelLarge,
                color = if (scrollSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    if (pageSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                )
                .clickable { onPageViewChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.reader_mode_page),
                style = MaterialTheme.typography.labelLarge,
                color = if (pageSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}
