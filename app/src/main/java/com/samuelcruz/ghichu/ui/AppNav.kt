package com.samuelcruz.ghichu.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samuelcruz.ghichu.core.S
import com.samuelcruz.ghichu.data.NoteKind
import kotlinx.coroutines.launch

/** Something the app was opened for: a notification, a launcher shortcut, a widget, the share menu. */
sealed interface Launch {
    data class Open(val id: String) : Launch
    data class New(val kind: Int, val start: String? = null) : Launch
    data class Shared(val title: String, val text: String, val images: List<Uri>) : Launch
    data object Search : Launch
    /** Today's note (launcher shortcut). */
    data object Today : Launch
}

/** Routes (like Rails routes): the note list, the editor that grows out of a card, and the other screens. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(launch: Launch?, onLaunchHandled: () -> Unit) {
    val app = app()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()

    // Status bar icons follow the theme.
    val view = LocalView.current
    val dark = LocalIsDark.current
    LaunchedEffect(dark, entry?.destination?.route) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    LaunchedEffect(Unit) {
        app.events.collect { e ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar(e.message, e.actionLabel, withDismissAction = false, duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) e.action?.let { app.scope.launch { it() } }
            }
        }
    }

    fun openEditor(id: String, start: String? = null) {
        nav.navigate("edit/$id" + (start?.let { "?start=$it" } ?: "")) { launchSingleTop = true }
    }

    fun openToday() = scope.launch {
        openEditor(app.repo.dailyNote(app.prefs.dailyTemplate.ifEmpty { null }).id)
    }

    LaunchedEffect(launch) {
        val l = launch ?: return@LaunchedEffect
        onLaunchHandled()
        when (l) {
            is Launch.Open -> if (app.db.notes().get(l.id)?.deleted == false) openEditor(l.id)
            is Launch.New -> openEditor(app.repo.create(l.kind).id, l.start)
            is Launch.Shared -> {
                val n = app.repo.create(NoteKind.TEXT, title = l.title, body = l.text)
                l.images.forEach { runCatching { app.repo.addImage(n.id, it) } }
                openEditor(n.id)
            }
            Launch.Search -> nav.navigate("search")
            Launch.Today -> openToday()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this) {
                NavHost(
                    nav, startDestination = "notes",
                    enterTransition = { fadeIn(tween(280)) },
                    exitTransition = { fadeOut(tween(280)) },
                    popEnterTransition = { fadeIn(tween(280)) },
                    popExitTransition = { fadeOut(tween(280)) },
                ) {
                    composable("notes") {
                        ProvideNavScope(this) {
                            NotesScreen(
                                onOpen = { openEditor(it) },
                                onNew = { id, start -> openEditor(id, start) },
                                onSearch = { nav.navigate("search") },
                                onLabels = { nav.navigate("labels") },
                                onSettings = { nav.navigate("settings") },
                                onReorder = { nav.navigate("reorder") },
                                onTemplates = { nav.navigate("templates") },
                                onToday = { openToday() },
                                onCalendar = { nav.navigate("calendar") },
                            )
                        }
                    }
                    composable(
                        "edit/{id}?start={start}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("start") { type = NavType.StringType; nullable = true; defaultValue = null },
                        ),
                    ) { e ->
                        val id = e.arguments?.getString("id").orEmpty()
                        ProvideNavScope(this) {
                            EditorScreen(
                                id, e.arguments?.getString("start"),
                                onBack = { nav.popBackStack() },
                                onHistory = { nav.navigate("history/$id") },
                                onOpenNote = { other -> nav.popBackStack(); openEditor(other) },
                                onFollowLink = { other -> openEditor(other) },
                                onDraw = { att -> nav.navigate("draw/$id" + (att?.let { "?att=$it" } ?: "")) },
                                onShareImage = { nav.navigate("shareimage/$id") },
                            )
                        }
                    }
                    composable("search") {
                        ProvideNavScope(this) { SearchScreen(onBack = { nav.popBackStack() }, onOpen = { openEditor(it) }) }
                    }
                    composable("labels") { LabelsScreen(onBack = { nav.popBackStack() }) }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() }, onTemplates = { nav.navigate("templates") })
                    }
                    composable("templates") {
                        TemplatesScreen(onBack = { nav.popBackStack() }, onOpen = { openEditor(it) })
                    }
                    composable(
                        "draw/{id}?att={att}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("att") { type = NavType.StringType; nullable = true; defaultValue = null },
                        ),
                    ) { e ->
                        DrawScreen(e.arguments?.getString("id").orEmpty(), e.arguments?.getString("att"), onDone = { nav.popBackStack() })
                    }
                    composable("shareimage/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                        ShareImageScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable("calendar") { CalendarScreen(onBack = { nav.popBackStack() }, onOpen = { openEditor(it) }) }
                    composable("reorder") { ReorderScreen(onBack = { nav.popBackStack() }) }
                    composable("history/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                        HistoryScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}

/** Covers the whole app until the fingerprint / face / PIN check passes. Asks right away when shown. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { onUnlock() }
    BackHandler { activity?.moveTaskToBack(true) }
    Box(
        Modifier.fillMaxSize().background(BrandBrush).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(44.dp)) }
            Spacer(Modifier.height(20.dp))
            Text(S.appLocked, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB45309)),
                modifier = Modifier.height(52.dp),
            ) { Text(S.unlock, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
