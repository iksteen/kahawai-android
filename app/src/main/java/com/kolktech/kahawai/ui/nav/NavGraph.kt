package com.kolktech.kahawai.ui.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavHostController
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kolktech.kahawai.KahawaiApp
import kotlinx.coroutines.launch
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.admin.AdminScreen
import com.kolktech.kahawai.ui.detail.DetailScreen
import com.kolktech.kahawai.ui.home.HomeScreen
import com.kolktech.kahawai.ui.library.LibraryScreen
import com.kolktech.kahawai.ui.login.LoginScreen
import com.kolktech.kahawai.ui.player.PlayerScreen
import com.kolktech.kahawai.ui.search.SearchScreen
import com.kolktech.kahawai.ui.settings.AppSettingsScreen
import com.kolktech.kahawai.ui.settings.ServerSettingsScreen
import com.kolktech.kahawai.ui.setup.ServerSetupScreen

private object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SEARCH = "search"
    const val APP_SETTINGS = "app_settings"
    const val SERVER_SETTINGS = "server_settings"
    const val ADMIN = "admin"
    const val LIBRARY = "library/{libraryId}?name={name}"
    const val DETAIL = "detail/{itemId}"
    const val PLAYER = "player/{itemId}?startMs={startMs}&audioTrack={audioTrack}&subtitleTrack={subtitleTrack}"
    fun library(libraryId: String, name: String) = "library/$libraryId?name=${Uri.encode(name)}"
    fun detail(itemId: String) = "detail/$itemId"
    fun player(itemId: String, startMs: Long, audioTrack: Int, subtitleTrackId: Long?) =
        "player/$itemId?startMs=$startMs&audioTrack=$audioTrack&subtitleTrack=${subtitleTrackId ?: -1}"
}

@Composable
fun KahawaiNavGraph(app: KahawaiApp, modifier: Modifier = Modifier) {
    // Token hydration (disk read + Keystore decrypt) runs off the main
    // thread now (see TokenStore.hydrated) instead of blocking process
    // startup, so the very first composition here can land before it's
    // done. Waiting on it — rather than reading hasTokens early and
    // risking a false "logged out" — costs at most a brief spinner
    // instead of a login-screen flash for a session that's actually
    // still valid.
    var tokensReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        app.tokenStore.hydrated.await()
        tokensReady = true
    }
    if (!tokensReady) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController: NavHostController = rememberNavController()
    val catalogRepository = remember { CatalogRepository() }
    val coroutineScope = rememberCoroutineScope()
    val start = when {
        app.serverConfigStore.baseUrl == null -> Routes.SETUP
        !app.tokenStore.hasTokens -> Routes.LOGIN
        else -> Routes.HOME
    }
    // A 401 that survived TokenAuthenticator's own refresh attempt means
    // the refresh token is gone too — no request will succeed until the
    // user signs in again. Tokens are usually already cleared by then
    // (TokenAuthenticator does it on a failed refresh), but clear here
    // too so a bare invalid-access-token-with-no-refresh-token case
    // (never hits that refresh path at all) is covered as well.
    val onSessionExpired: () -> Unit = {
        coroutineScope.launch { app.tokenStore.clear() }
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }

    // Every screen keeps out of the display cutout (and any visible bars)
    // EXCEPT the player: video should use the whole panel, cutout
    // included, so its route skips safeDrawingPadding entirely. The
    // window-level opt-in that actually lets content extend under the
    // cutout (layoutInDisplayCutoutMode) is the player's own to manage —
    // see PlayerScreen's window DisposableEffect. The margin is also
    // user-toggleable (AppSettingsScreen) for boxes that report a phantom
    // cutout with nothing to actually avoid.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val insetsModifier = if (currentRoute == Routes.PLAYER || !app.appSettingsStore.reserveNotchSpace) {
        modifier
    } else {
        modifier.safeDrawingPadding()
    }
    // No animated transitions anywhere in the graph — every route cuts
    // instantly instead of the library's default 700ms cross-fade.
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = insetsModifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.SETUP) {
            ServerSetupScreen(
                serverConfigStore = app.serverConfigStore,
                tokenStore = app.tokenStore,
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
            HomeScreen(
                repo = catalogRepository,
                isAdmin = app.tokenStore.isAdmin,
                onOpenItem = { itemId -> navController.navigate(Routes.detail(itemId)) },
                onOpenLibrary = { id, name -> navController.navigate(Routes.library(id, name)) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenAppSettings = { navController.navigate(Routes.APP_SETTINGS) },
                onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                // Same effect as an expired session: clear tokens, pop back
                // to a fresh Login.
                onLogout = onSessionExpired,
                onSessionExpired = onSessionExpired,
            )
        }
        composable(Routes.APP_SETTINGS) {
            AppSettingsScreen(
                serverConfigStore = app.serverConfigStore,
                tokenStore = app.tokenStore,
                appSettingsStore = app.appSettingsStore,
                onBack = { navController.popBackStack() },
                onChangeServer = {
                    navController.navigate(Routes.SETUP) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.SERVER_SETTINGS) {
            ServerSettingsScreen(
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
            )
        }
        composable(Routes.ADMIN) {
            AdminScreen(
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                repo = catalogRepository,
                onOpenItem = { itemId -> navController.navigate(Routes.detail(itemId)) },
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
            )
        }
        composable(
            Routes.LIBRARY,
            arguments = listOf(
                navArgument("libraryId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val libraryId = backStackEntry.arguments?.getString("libraryId") ?: return@composable
            val name = backStackEntry.arguments?.getString("name").orEmpty()
            LibraryScreen(
                libraryId = libraryId,
                libraryName = name,
                repo = catalogRepository,
                onOpenItem = { itemId -> navController.navigate(Routes.detail(itemId)) },
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
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
                onPlay = { playId, startMs, audioTrack, subtitleTrackId ->
                    navController.navigate(Routes.player(playId, startMs, audioTrack, subtitleTrackId))
                },
                onBack = { navController.popBackStack() },
                onSessionExpired = onSessionExpired,
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("startMs") { type = NavType.LongType; defaultValue = 0L },
                navArgument("audioTrack") { type = NavType.IntType; defaultValue = 0 },
                navArgument("subtitleTrack") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
            val startMs = backStackEntry.arguments?.getLong("startMs") ?: 0L
            val audioTrack = backStackEntry.arguments?.getInt("audioTrack") ?: 0
            val subtitleTrack = backStackEntry.arguments?.getLong("subtitleTrack") ?: -1L
            PlayerScreen(
                itemId = itemId,
                startMs = startMs,
                initialAudioTrack = audioTrack,
                initialSubtitleTrackId = subtitleTrack.takeIf { it >= 0 },
                onClose = { navController.popBackStack() },
                // Replaces the current (just-finished) player entry with
                // the NEXT episode's own detail entry, then stacks its
                // player on top of that — so "back" from episode N+1 lands
                // on episode N+1's detail screen, not back on wherever
                // playback of the season was originally started from (the
                // finished episode's entry, which this pops away).
                onNextEpisode = { nextItemId, nextSubtitleTrackId ->
                    navController.navigate(Routes.detail(nextItemId)) {
                        popUpTo(Routes.PLAYER) { inclusive = true }
                    }
                    navController.navigate(Routes.player(nextItemId, 0L, 0, nextSubtitleTrackId))
                },
            )
        }
    }
}
