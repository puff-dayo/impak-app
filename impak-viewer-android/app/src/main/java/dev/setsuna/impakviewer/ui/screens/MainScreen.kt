package dev.setsuna.impakviewer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.setsuna.impakviewer.model.ViewerState
import dev.setsuna.impakviewer.model.ViewerViewModel
import dev.setsuna.impakviewer.ui.components.InfoPanel
import dev.setsuna.impakviewer.ui.components.ThumbnailStrip
import dev.setsuna.impakviewer.ui.components.ZoomableImage
import dev.setsuna.impakviewer.ui.theme.Accent
import dev.setsuna.impakviewer.ui.theme.AccentLight
import dev.setsuna.impakviewer.ui.theme.Danger
import dev.setsuna.impakviewer.ui.theme.DangerLight
import dev.setsuna.impakviewer.ui.theme.DarkBg
import dev.setsuna.impakviewer.ui.theme.DarkBorder
import dev.setsuna.impakviewer.ui.theme.DarkCanvas
import dev.setsuna.impakviewer.ui.theme.DarkSidebar
import dev.setsuna.impakviewer.ui.theme.LightBg
import dev.setsuna.impakviewer.ui.theme.LightBorder
import dev.setsuna.impakviewer.ui.theme.LightCanvas
import dev.setsuna.impakviewer.ui.theme.LightSidebar
import dev.setsuna.impakviewer.ui.theme.TextDim
import dev.setsuna.impakviewer.ui.theme.TextDimLight
import dev.setsuna.impakviewer.ui.theme.TextPrimary
import dev.setsuna.impakviewer.ui.theme.TextPrimaryLight
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: ViewerViewModel) {
    val context = LocalContext.current
    val isDark = viewModel.isDark
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

    val bg = if (isDark) DarkBg else LightBg
    val sidebar = if (isDark) DarkSidebar else LightSidebar
    val border = if (isDark) DarkBorder else LightBorder
    val accent = if (isDark) Accent else AccentLight
    val primary = if (isDark) TextPrimary else TextPrimaryLight
    val dim = if (isDark) TextDim else TextDimLight

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.openFile(it, context) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? -> uri?.let { viewModel.exportCurrentFrame(context, it) } }

    var showThumbs by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    var memText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            memText = viewModel.getMemoryStatus()
            delay(3000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding(),
    ) {
        // Canvas
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Thumbnail drawer toggle
                IconButton(onClick = { showThumbs = !showThumbs }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ViewSidebar,
                        "Thumbnails",
                        tint = if (showThumbs) accent else dim,
                    )
                }

                IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, "Open", tint = primary)
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.prevFrame() },
                    enabled = viewModel.currentIndex > 0,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous", tint = primary)
                }

                Text(
                    text = if (viewModel.frameCount > 0) "${viewModel.currentIndex + 1} / ${viewModel.frameCount}"
                    else "—",
                    fontSize = 11.sp,
                    color = dim,
                )

                IconButton(
                    onClick = { viewModel.nextFrame() },
                    enabled = viewModel.currentIndex < viewModel.frameCount - 1,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next", tint = primary)
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    onClick = {
                        val name = viewModel.frameInfo?.name ?: "frame"
                        exportLauncher.launch("$name.png")
                    },
                    enabled = viewModel.viewerState is ViewerState.Ready,
                ) {
                    Icon(Icons.Default.SaveAlt, "Export", tint = primary)
                }

                IconButton(onClick = { viewModel.isDark = !viewModel.isDark }) {
                    Icon(
                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        "Toggle theme",
                        tint = primary,
                    )
                }

                // Info drawer toggle
                IconButton(onClick = { showInfo = !showInfo }) {
                    Icon(
                        Icons.Default.Info,
                        "Info",
                        tint = if (showInfo) accent else dim,
                    )
                }
            }

            HorizontalDivider(color = border, thickness = 1.dp)

            // Main canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (isDark) DarkCanvas else LightCanvas),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = viewModel.viewerState) {
                    is ViewerState.Welcome -> WelcomeHint(isDark)
                    is ViewerState.Loading -> CircularProgressIndicator(color = accent)
                    is ViewerState.Error -> ErrorMessage(state.message, isDark)
                    is ViewerState.Ready -> {
                        ZoomableImage(
                            bitmap = state.bitmap,
                            onSwipeLeft = { viewModel.nextFrame() },
                            onSwipeRight = { viewModel.prevFrame() },
                            onTapLeft = { viewModel.prevFrame() },
                            onTapRight = { viewModel.nextFrame() },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.10f)
                                    .clickable { viewModel.prevFrame() })
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.10f)
                                    .clickable { viewModel.nextFrame() })
                        }
                    }
                }
            }

            HorizontalDivider(color = border, thickness = 1.dp)

            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = viewModel.statusText,
                    fontSize = 10.sp,
                    color = if (viewModel.statusError) {
                        if (isDark) Danger else DangerLight
                    } else dim,
                )
                Text(
                    text = memText,
                    fontSize = 10.sp,
                    color = dim,
                )
            }
        }

        // Thumbnail drawer
        AnimatedVisibility(
            visible = showThumbs,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 56.dp)
                .zIndex(10f),
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            ThumbnailStrip(
                thumbs = thumbnails,
                selectedIdx = viewModel.currentIndex,
                isDark = isDark,
                onSelect = {
                    viewModel.goTo(it)
                    showThumbs = false
                },
                modifier = Modifier
                    .width(172.dp)
                    .fillMaxHeight()
                    .shadow(8.dp)
                    .background(
                        sidebar, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    ),
            )
        }

        // Info drawer
        AnimatedVisibility(
            visible = showInfo,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp)
                .zIndex(10f),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            InfoPanel(
                fileInfo = viewModel.fileInfo,
                frameInfo = viewModel.frameInfo,
                isDark = isDark,
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .shadow(8.dp)
                    .background(sidebar, RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
            )
        }
    }
}

@Composable
private fun WelcomeHint(isDark: Boolean) {
    val dim = if (isDark) TextDim else TextDimLight
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            tint = dim,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Open an .impak file to begin", fontSize = 16.sp, color = dim)
    }
}

@Composable
private fun ErrorMessage(msg: String, isDark: Boolean) {
    val danger = if (isDark) Danger else DangerLight
    Text(
        text = msg,
        color = danger,
        fontSize = 13.sp,
        modifier = Modifier.padding(24.dp),
    )
}