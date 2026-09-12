package com.example.selfiememory.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Gallery : Screen("gallery")
    data object Settings : Screen("settings")
    data object Viewer : Screen("viewer/{selfieId}") {
        fun createRoute(selfieId: Int) = "viewer/$selfieId"
    }
    data object VideoPreview : Screen("videoPreview/{videoUri}") {
        fun createRoute(videoUri: Uri) = "videoPreview/${Uri.encode(videoUri.toString())}"
    }
}
