package com.example.selfiememory.ui.navigation

sealed class Screen(val route: String) {
    data object Gallery : Screen("gallery")
    data object Settings : Screen("settings")
    data object Viewer : Screen("viewer/{selfieId}") {
        fun createRoute(selfieId: Int) = "viewer/$selfieId"
    }
}