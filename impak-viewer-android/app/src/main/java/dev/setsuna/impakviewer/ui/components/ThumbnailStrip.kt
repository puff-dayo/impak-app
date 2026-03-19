package dev.setsuna.impakviewer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.setsuna.impakviewer.model.FrameThumb
import dev.setsuna.impakviewer.ui.theme.Accent
import dev.setsuna.impakviewer.ui.theme.AccentLight
import dev.setsuna.impakviewer.ui.theme.DarkBg
import dev.setsuna.impakviewer.ui.theme.DarkSidebar
import dev.setsuna.impakviewer.ui.theme.DarkThumbHov
import dev.setsuna.impakviewer.ui.theme.LightBg
import dev.setsuna.impakviewer.ui.theme.LightSidebar
import dev.setsuna.impakviewer.ui.theme.LightThumbHov
import dev.setsuna.impakviewer.ui.theme.TextDim
import dev.setsuna.impakviewer.ui.theme.TextDimLight

@Composable
fun ThumbnailStrip(
    thumbs: List<FrameThumb>,
    selectedIdx: Int,
    isDark: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIdx) {
        if (thumbs.isNotEmpty() && selectedIdx < thumbs.size) {
            listState.animateScrollToItem(selectedIdx)
        }
    }

    val sidebarBg = if (isDark) DarkSidebar else LightSidebar
    val selectedBg = if (isDark) DarkBg else LightBg
    val accentColor = if (isDark) Accent else AccentLight
    val hoverBg = if (isDark) DarkThumbHov else LightThumbHov

    LazyColumn(
        state = listState,
        modifier = modifier.background(sidebarBg),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(thumbs, key = { it.index }) { thumb ->
            val isSelected = thumb.index == selectedIdx
            ThumbnailItem(
                thumb = thumb,
                isSelected = isSelected,
                isDark = isDark,
                selectedBg = selectedBg,
                hoverBg = hoverBg,
                accentColor = accentColor,
                onClick = { onSelect(thumb.index) },
            )
        }
    }
}

@Composable
private fun ThumbnailItem(
    thumb: FrameThumb,
    isSelected: Boolean,
    isDark: Boolean,
    selectedBg: androidx.compose.ui.graphics.Color,
    hoverBg: androidx.compose.ui.graphics.Color,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val accentDim = if (isDark) TextDim else TextDimLight
    val textDim = if (isDark) TextDim else TextDimLight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(selectedBg).border(
                        width = 3.dp,
                        color = accentColor,
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp),
                    )
                else Modifier.background(hoverBg.copy(alpha = 0f))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = thumb.thumb.asImageBitmap(),
                contentDescription = thumb.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(thumb.thumb.width.toFloat() / thumb.thumb.height.toFloat())
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "#${thumb.index}",
                    fontSize = 9.sp,
                    color = textDim,
                )
                Text(
                    text = if (thumb.isKeyframe) "KEY" else "Δ",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (thumb.isKeyframe) accentColor else textDim,
                )
            }
        }
    }
}
