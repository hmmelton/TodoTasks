package com.harrison.todotasks

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.harrison.todotasks.ui.navigation.TodoTasksNavDisplay
import com.harrison.todotasks.ui.theme.TodoTasksTheme
import dagger.hilt.android.AndroidEntryPoint

/** The app's only activity, which hosts every screen. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        setContent {
            TodoTasksTheme {
                TodoTasksNavDisplay()
            }
        }
    }
}
