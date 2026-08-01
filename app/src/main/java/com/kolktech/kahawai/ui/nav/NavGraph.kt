package com.kolktech.kahawai.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kolktech.kahawai.KahawaiApp
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.detail.DetailScreen
import com.kolktech.kahawai.ui.login.LoginScreen
import com.kolktech.kahawai.ui.main.MainScreen
import com.kolktech.kahawai.ui.player.PlayerScreen
import com.kolktech.kahawai.ui.setup.ServerSetupScreen

private object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val DETAIL = "detail/{itemId}"
    const val PLAYER = "player/{itemId}?startMs={startMs}"
    fun detail(itemId: String) = "detail/$itemId"
    fun player(itemId: String, startMs: Long) = "player/$itemId?startMs=$startMs"
}

@Composable
fun KahawaiNavGraph(app: KahawaiApp, modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    val catalogRepository = remember { CatalogRepository() }
    val start = when {
        app.serverConfigStore.baseUrl == null -> Routes.SETUP
        !app.tokenStore.hasTokens -> Routes.LOGIN
        else -> Routes.HOME
    }

    NavHost(navController = navController, startDestination = start, modifier = modifier) {
        composable(Routes.SETUP) {
            ServerSetupScreen(
                serverConfigStore = app.serverConfigStore,
                onReady = {
                    val dest = if (app.tokenStore.hasTokens) Routes.HOME else Routes.LOGIN
                    navController.navigate(dest) { popUpTo(Routes.SETUP) { inclusive = true } }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                tokenStore = app.tokenStore,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            MainScreen(
                repo = catalogRepository,
                onOpenItem = { itemId -> navController.navigate(Routes.detail(itemId)) },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
            DetailScreen(
                itemId = itemId,
                repo = catalogRepository,
                onOpenItem = { childId -> navController.navigate(Routes.detail(childId)) },
                onPlay = { playId, startMs -> navController.navigate(Routes.player(playId, startMs)) },
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("startMs") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
            val startMs = backStackEntry.arguments?.getLong("startMs") ?: 0L
            PlayerScreen(
                itemId = itemId,
                startMs = startMs,
                onClose = { navController.popBackStack() },
            )
        }
    }
}
