package dev.setsuna.impakviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.setsuna.impakviewer.model.FileInfo
import dev.setsuna.impakviewer.model.FrameInfo
import dev.setsuna.impakviewer.ui.theme.*

@Composable
fun InfoPanel(
    fileInfo: FileInfo?,
    frameInfo: FrameInfo?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val panelBg = if (isDark) DarkBg else LightBg
    val borderCol = if (isDark) DarkBorder else LightBorder
    val headerCol = if (isDark) TextDim else TextDimLight
    val primary = if (isDark) TextPrimary else TextPrimaryLight
    val muted = if (isDark) TextMuted else TextMutedLight
    val dimColor = if (isDark) TextDim else TextDimLight
    val accentCol = if (isDark) Accent else AccentLight
    val warnCol = if (isDark) Warning else WarningLight
    val successCol = if (isDark) Success else SuccessLight
    val patchBg = if (isDark) DarkPatchBg else LightPatchBg

    Column(
        modifier = modifier
            .background(panelBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // File section
        SectionHeader("FILE", headerCol)
        Spacer(Modifier.height(4.dp))

        if (fileInfo != null) {
            InfoRow("File", fileInfo.fileName, primary, muted)
            InfoRow("Mode", fileInfo.modeName, primary, muted)
            InfoRow("Codec", fileInfo.codec, primary, muted)
            InfoRow("Canvas", "${fileInfo.width} × ${fileInfo.height}", primary, muted)
            InfoRow("Frames", fileInfo.totalFrames.toString(), primary, muted)
            if (fileInfo.baselineCount > 0) InfoRow(
                "Baselines",
                fileInfo.baselineCount.toString(),
                warnCol,
                muted
            )
            InfoRow("Size", formatBytes(fileInfo.fileSizeBytes), primary, muted)
        } else {
            Text("No file loaded", fontSize = 11.sp, color = dimColor)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = borderCol, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        // Frame section
        SectionHeader("FRAME", headerCol)
        Spacer(Modifier.height(4.dp))

        if (frameInfo != null) {
            InfoRow("Name", frameInfo.name, primary, muted)
            InfoRow(
                "Type",
                if (frameInfo.isKeyframe) "keyframe" else "delta",
                if (frameInfo.isKeyframe) accentCol else successCol,
                muted
            )
            InfoRow("Ref", frameInfo.refId, primary, muted)
            InfoRow("Patches", frameInfo.patchCount.toString(), primary, muted)

            if (frameInfo.patches.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "PATCH REGIONS",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = headerCol,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(patchBg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    frameInfo.patches.forEach { p ->
                        Text(
                            text = " %4d,%4d  %4d×%-4d".format(p.x, p.y, p.w, p.h),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = muted,
                        )
                    }
                }
            }
        } else {
            Text("No frame loaded", fontSize = 11.sp, color = dimColor)
        }
    }
}

@Composable
private fun SectionHeader(label: String, color: Color) {
    Text(
        text = label,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 11.sp, color = labelColor)
        Text(value, fontSize = 11.sp, color = valueColor)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes > 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%.1f KB".format(bytes / 1_000.0)
}
