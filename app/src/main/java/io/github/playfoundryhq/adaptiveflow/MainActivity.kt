package io.github.playfoundryhq.adaptiveflow

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.playfoundryhq.adaptiveflow.data.settings.LocaleManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.playfoundryhq.adaptiveflow.ui.DecksTab
import io.github.playfoundryhq.adaptiveflow.ui.ImportTab
import io.github.playfoundryhq.adaptiveflow.ui.PathTab
import io.github.playfoundryhq.adaptiveflow.ui.StudySessionScreen
import io.github.playfoundryhq.adaptiveflow.ui.TutorialTab
import io.github.playfoundryhq.adaptiveflow.ui.theme.AdaptiveFlowTheme
import io.github.playfoundryhq.adaptiveflow.ui.theme.AppTheme
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels { StudyViewModel.Factory }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdaptiveFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(AppTheme.colors.screenGradient))
                    ) {
                        MainScreen(viewModel)
                    }
                }
            }
        }
    }
}


/** The four bottom-nav destinations. `study` is a separate route (no tab). */
enum class TopRoute(
    val route: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @androidx.annotation.StringRes val label: Int,
    @androidx.annotation.StringRes val contentDescription: Int,
    val testTag: String,
) {
    Decks("decks", Icons.Default.Layers, R.string.nav_decks, R.string.nav_decks_desc, "nav_decks_tab"),
    Quest("quest", Icons.Default.Explore, R.string.nav_quest, R.string.nav_quest_desc, "nav_path_tab"),
    Import("import", Icons.Default.AddCircle, R.string.nav_import, R.string.nav_import_desc, "nav_import_tab"),
    Guide("guide", Icons.Default.Lightbulb, R.string.nav_guide, R.string.nav_guide_desc, "nav_tutorial_tab"),
}

private const val STUDY_ROUTE = "study"

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun MainScreen(viewModel: StudyViewModel) {
    val navController = rememberNavController()
    val currentDeck by viewModel.currentDeck.collectAsStateWithLifecycle()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // The deck selection in the ViewModel is the source of truth for whether a
    // study session is open; keep the nav graph in sync with it.
    LaunchedEffect(currentDeck) {
        val onStudy = navController.currentDestination?.route == STUDY_ROUTE
        if (currentDeck != null && !onStudy) {
            navController.navigate(STUDY_ROUTE) { launchSingleTop = true }
        } else if (currentDeck == null && onStudy) {
            navController.popBackStack()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { io.github.playfoundryhq.adaptiveflow.ui.AppSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute != STUDY_ROUTE) {
                ZeroLanguageNavigationBar(
                    currentRoute = currentRoute,
                    onSelect = { navController.switchTab(it.route) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopRoute.Decks.route,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable(TopRoute.Decks.route) {
                DecksTab(viewModel = viewModel, onNavigateToImport = { navController.switchTab(TopRoute.Import.route) })
            }
            composable(TopRoute.Quest.route) {
                PathTab(viewModel = viewModel, onNavigateToImport = { navController.switchTab(TopRoute.Import.route) })
            }
            composable(TopRoute.Import.route) { ImportTab(viewModel = viewModel) }
            composable(TopRoute.Guide.route) { TutorialTab() }
            composable(STUDY_ROUTE) { StudySessionScreen(viewModel = viewModel) }
        }
    }
}

@Composable
fun ZeroLanguageNavigationBar(
    currentRoute: String?,
    onSelect: (TopRoute) -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.hairline, shape)
            .testTag("navigation_bar"),
        containerColor = c.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        TopRoute.entries.forEach { item ->
            val selected = currentRoute == item.route
            val tint = if (selected) c.accent else c.textSecondary
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(item) },
                modifier = Modifier.testTag(item.testTag),
                icon = { Icon(item.icon, contentDescription = stringResource(item.contentDescription), tint = tint) },
                label = {
                    Text(
                        stringResource(item.label),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 11.sp,
                        color = tint
                    )
                },
                colors = NavigationBarItemDefaults.colors(indicatorColor = c.accentMuted)
            )
        }
    }
}


