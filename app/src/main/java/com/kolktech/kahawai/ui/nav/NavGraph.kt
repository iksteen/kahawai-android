package com.kolktech.kahawai.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.platform.LocalContext
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
import com.kolktech.kahawai.data.repository.AuthRepository
import com.kolktech.kahawai.data.repository.CatalogRepository
import com.kolktech.kahawai.ui.admin.AdminScreen
import com.kolktech.kahawai.ui.detail.DetailScreen
import com.kolktech.kahawai.ui.home.HomeScreen
import com.kolktech.kahawai.ui.library.LibraryScreen
import com.kolktech.kahawai.ui.login.LoginScreen
import com.kolktech.kahawai.ui.player.PlayerScreen
import com.kolktech.kahawai.ui.search.SearchScreen
import com.kolktech.kahawai.ui.settings.AboutScreen
import com.kolktech.kahawai.ui.settings.CacheSettingsScreen
import com.kolktech.kahawai.ui.settings.InterfaceSettingsScreen
import com.kolktech.kahawai.ui.settings.LanguageSettingsScreen
import com.kolktech.kahawai.ui.settings.NetworkSettingsScreen
import com.kolktech.kahawai.ui.settings.PlayerSettingsScreen
import com.kolktech.kahawai.ui.settings.SeekingSettingsScreen
import com.kolktech.kahawai.ui.settings.ServerSettingsScreen
import com.kolktech.kahawai.ui.settings.SettingsScreen
import com.kolktech.kahawai.ui.setup.ServerSetupScreen

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private object Routes {
    const val SETUP = "setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val SETTINGS_LANGUAGE = "settings_language"
    const val SETTINGS_INTERFACE = "settings_interface"
    const val SETTINGS_PLAYER = "settings_player"
    const val SETTINGS_PLAYER_SEEKING = "settings_player_seeking"
    const val SETTINGS_NETWORK = "settings_network"
    const val SETTINGS_CACHE = "settings_cache"
    const val SETTINGS_ABOUT = "settings_about"
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
    val authRepository = remember { AuthRepository() }
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
    // User-initiated logout (menu item): revoke the refresh family
    // server-side before dropping local tokens, so the hub's login row
    // doesn't outlive this session. Best-effort — an unreachable hub or
    // an already-invalid token shouldn't block clearing local state.
    val onLogout: () -> Unit = {
        coroutineScope.launch {
            app.tokenStore.refreshToken?.let { refreshToken ->
                runCatching { authRepository.logout(refreshToken) }
            }
            app.tokenStore.clear()
        }
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }

    // Every screen keeps out of the display cutout (and any visible bars)
    // EXCEPT the player: video should use the whole panel, cutout
    // included, so its route skips safeDrawingPadding entirely.
    //
    // Compose's safeDrawingPadding() below only controls content padding
    // *inside* the window — it does nothing about whether the window
    // itself is allowed to draw into the cutout area at all. That's a
    // separate, window-level permission (layoutInDisplayCutoutMode),
    // which Android only grants when explicitly set to SHORT_EDGES/ALWAYS;
    // left at the default, the system reserves a margin around the
    // cutout regardless of what Compose does. So reserveNotchSpace has to
    // toggle both: the window's cutout mode here (skipped on the player's
    // own route, which always forces SHORT_EDGES for the duration of
    // playback via its own DisposableEffect, then restores whatever this
    // effect had set) and the Compose-side padding below.
    //
    // Held as Compose state here (rather than read straight from the
    // store each time) so InterfaceSettingsScreen flipping it recomposes
    // this graph immediately — both effects below rerun on the same
    // frame, on whatever screen is currently showing, instead of only
    // taking effect after the next route change.
    var reserveNotchSpace by remember { mutableStateOf(app.appSettingsStore.reserveNotchSpace) }
    val onReserveNotchSpaceChange: (Boolean) -> Unit = {
        reserveNotchSpace = it
        app.appSettingsStore.reserveNotchSpace = it
    }
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val context = LocalContext.current
    LaunchedEffect(currentRoute, reserveNotchSpace) {
        if (currentRoute != Routes.PLAYER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.findActivity()?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = if (reserveNotchSpace) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
        }
    }
    // safeDrawingPadding() both pads AND consumes the safeDrawing insets,
    // so screens further down the tree (a Scaffold's TopAppBar, which
    // reserves this same inset by default) see nothing left to pad for
    // and don't add their own margin on top. Turning the setting off has
    // to consume those insets too — with a bare `modifier` here, every
    // Scaffold-based screen would just fall back to applying its own
    // default inset padding, and the toggle would look like it does
    // nothing on any screen with a TopAppBar (only screens with no
    // Scaffold at all, e.g. Detail, would visibly react).
    val insetsModifier = when {
        currentRoute == Routes.PLAYER -> modifier
        reserveNotchSpace -> modifier.safeDrawingPadding()
        else -> modifier.consumeWindowInsets(WindowInsets.safeDrawing)
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
                onOpenItem = { itemId -> navController.navigate(Routes.detail(itemId)) },
                onOpenLibrary = { id, name -> navController.navigate(Routes.library(id, name)) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = onLogout,
                onSessionExpired = onSessionExpired,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                isAdmin = app.tokenStore.isAdmin,
                onBack = { navController.popBackStack() },
                onOpenLanguage = { navController.navigate(Routes.SETTINGS_LANGUAGE) },
                onOpenInterface = { navController.navigate(Routes.SETTINGS_INTERFACE) },
                onOpenPlayer = { navController.navigate(Routes.SETTINGS_PLAYER) },
                onOpenNetwork = { navController.navigate(Routes.SETTINGS_NETWORK) },
                onOpenServerSettings = { navController.navigate(Routes.SERVER_SETTINGS) },
                onOpenAdmin = { navController.navigate(Routes.ADMIN) },
                onOpenCache = { navController.navigate(Routes.SETTINGS_CACHE) },
                onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
            )
        }
        composable(Routes.SETTINGS_LANGUAGE) {
            LanguageSettingsScreen(
                appSettingsStore = app.appSettingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_INTERFACE) {
            InterfaceSettingsScreen(
                reserveNotchSpace = reserveNotchSpace,
                onReserveNotchSpaceChange = onReserveNotchSpaceChange,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_PLAYER) {
            PlayerSettingsScreen(
                appSettingsStore = app.appSettingsStore,
                onBack = { navController.popBackStack() },
                onOpenSeeking = { navController.navigate(Routes.SETTINGS_PLAYER_SEEKING) },
            )
        }
        composable(Routes.SETTINGS_PLAYER_SEEKING) {
            SeekingSettingsScreen(
                appSettingsStore = app.appSettingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_NETWORK) {
            NetworkSettingsScreen(
                serverConfigStore = app.serverConfigStore,
                tokenStore = app.tokenStore,
                onBack = { navController.popBackStack() },
                onChangeServer = {
                    navController.navigate(Routes.SETUP) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable(Routes.SETTINGS_CACHE) {
            CacheSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
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
                appSettingsStore = app.appSettingsStore,
                initialAudioTrack = audioTrack,
                initialSubtitleTrackId = subtitleTrack.takeIf { it >= 0 },
                onClose = { navController.popBackStack() },
                // Replaces both the current (just-finished) player entry
                // AND its episode's detail entry with the NEXT episode's
                // own detail entry, then stacks its player on top of that
                // — so "back" from episode N+1 lands on episode N+1's
                // detail screen, and back again lands on the season/series
                // screen the user started watching from, instead of
                // walking back through every previously-watched episode's
                // leftover detail entry.
                onNextEpisode = { nextItemId, nextSubtitleTrackId ->
                    navController.navigate(Routes.detail(nextItemId)) {
                        popUpTo(Routes.DETAIL) { inclusive = true }
                    }
                    navController.navigate(Routes.player(nextItemId, 0L, 0, nextSubtitleTrackId))
                },
                // Same back-stack rewrite as onNextEpisode, for the "<"
                // button jumping to the previous episode instead.
                onPreviousEpisode = { previousItemId, previousSubtitleTrackId ->
                    navController.navigate(Routes.detail(previousItemId)) {
                        popUpTo(Routes.DETAIL) { inclusive = true }
                    }
                    navController.navigate(Routes.player(previousItemId, 0L, 0, previousSubtitleTrackId))
                },
            )
        }
    }
}
