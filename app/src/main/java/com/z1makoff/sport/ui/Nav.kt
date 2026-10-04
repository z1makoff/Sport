package com.z1makoff.sport.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.z1makoff.sport.data.SportDao
import com.z1makoff.sport.ui.detail.DetailScreen
import com.z1makoff.sport.ui.edit.EditWorkoutScreen
import com.z1makoff.sport.ui.home.HomeScreen
import com.z1makoff.sport.ui.run.RunScreen

@Composable
fun SportNav(dao: SportDao) {
    val nav = rememberNavController()
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                dao = dao,
                onOpen = { nav.navigate("detail/$it") },
                onCreate = { nav.navigate("edit/0") },
            )
        }
        composable("edit/{id}", idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            EditWorkoutScreen(
                dao = dao,
                workoutId = id,
                onDone = { savedId ->
                    nav.popBackStack()
                    if (id == 0L && savedId != null) nav.navigate("detail/$savedId")
                },
                onDeleted = { nav.popBackStack("home", inclusive = false) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("detail/{id}", idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            DetailScreen(
                dao = dao,
                workoutId = id,
                onStart = { nav.navigate("run/$id") },
                onEdit = { nav.navigate("edit/$id") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("run/{id}", idArg) { entry ->
            val id = entry.arguments!!.getLong("id")
            RunScreen(dao = dao, workoutId = id, onClose = { nav.popBackStack() })
        }
    }
}
