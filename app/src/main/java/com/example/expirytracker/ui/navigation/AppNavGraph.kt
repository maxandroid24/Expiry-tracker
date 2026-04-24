package com.example.expirytracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.expirytracker.ui.screens.AddEditProductScreen
import com.example.expirytracker.ui.screens.CameraScreen
import com.example.expirytracker.ui.screens.ProductListScreen
import com.example.expirytracker.viewmodel.ProductViewModel

object Routes {
    const val LIST = "list"
    const val ADD = "edit?id={id}"
    fun edit(id: Long?): String = if (id == null) "edit?id=-1" else "edit?id=$id"
    const val CAMERA = "camera"
}

@Composable
fun AppNavGraph() {
    val nav = rememberNavController()
    val vm: ProductViewModel = viewModel(factory = ProductViewModel.Factory)

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            ProductListScreen(
                vm = vm,
                onAdd = { nav.navigate(Routes.edit(null)) },
                onEdit = { id -> nav.navigate(Routes.edit(id)) }
            )
        }
        composable(
            route = Routes.ADD,
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            AddEditProductScreen(
                vm = vm,
                productId = id.takeIf { it > 0 },
                onBack = { nav.popBackStack() },
                onScan = { nav.navigate(Routes.CAMERA) }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                vm = vm,
                onCancel = { nav.popBackStack() },
                onCaptured = {
                    // After capture/extraction the user returns to the form, which reads lastExtraction.
                    nav.popBackStack()
                }
            )
        }
    }
}
