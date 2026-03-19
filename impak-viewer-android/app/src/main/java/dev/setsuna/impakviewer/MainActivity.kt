package dev.setsuna.impakviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.setsuna.impakviewer.model.ViewerViewModel
import dev.setsuna.impakviewer.ui.screens.MainScreen
import dev.setsuna.impakviewer.ui.theme.ImpakViewerTheme


class MainActivity : ComponentActivity() {
    private val viewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            ImpakViewerTheme(darkTheme = viewModel.isDark) {
                MainScreen(viewModel)
            }
        }

        // from a file manager
        intent?.data?.let { uri ->
            viewModel.openFile(uri, this)
        }
    }
}
