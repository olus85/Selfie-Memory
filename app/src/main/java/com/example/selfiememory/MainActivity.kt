package com.example.selfiememory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.selfiememory.ui.gallery.GalleryScreen
import com.example.selfiememory.ui.navigation.Screen
import com.example.selfiememory.ui.settings.SettingsScreen
import com.example.selfiememory.ui.theme.SelfieMemoryTheme
import com.example.selfiememory.ui.viewer.ViewerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SelfieMemoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Gallery.route
                    ) {
                        composable(Screen.Gallery.route) {
                            GalleryScreen(
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onNavigateToViewer = { selfieId ->
                                    navController.navigate(Screen.Viewer.createRoute(selfieId))
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable(Screen.Viewer.route) { backStackEntry ->
                            val selfieId = backStackEntry.arguments?.getString("selfieId")?.toIntOrNull() ?: return@composable
                            ViewerScreen(
                                selfieId = selfieId,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}