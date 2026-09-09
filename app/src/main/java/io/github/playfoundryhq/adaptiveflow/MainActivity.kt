package io.github.playfoundryhq.adaptiveflow

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.playfoundryhq.adaptiveflow.ui.DecksTab
import io.github.playfoundryhq.adaptiveflow.ui.ImportTab
import io.github.playfoundryhq.adaptiveflow.ui.PathTab
import io.github.playfoundryhq.adaptiveflow.ui.StudySessionScreen
import io.github.playfoundryhq.adaptiveflow.ui.TutorialTab
import io.github.playfoundryhq.adaptiveflow.ui.theme.AdaptiveFlowTheme
import io.github.playfoundryhq.adaptiveflow.ui.theme.AppTheme
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels()

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


enum class NavigationTab {
    Decks,
    Path,
    Import,
    Tutorial
}


@Composable
fun MainScreen(viewModel: StudyViewModel) {
    var activeTab by rememberSaveable { mutableStateOf(NavigationTab.Decks) }
    val currentDeck by viewModel.currentDeck.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (currentDeck == null) {
                ZeroLanguageNavigationBar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentDeck != null) {
                StudySessionScreen(viewModel = viewModel)
            } else {
                when (activeTab) {
                    NavigationTab.Decks -> DecksTab(
                        viewModel = viewModel,
                        onNavigateToImport = { activeTab = NavigationTab.Import }
                    )
                    NavigationTab.Path -> PathTab(
                        viewModel = viewModel,
                        onNavigateToImport = { activeTab = NavigationTab.Import }
                    )
                    NavigationTab.Import -> ImportTab(viewModel = viewModel)
                    NavigationTab.Tutorial -> TutorialTab()
                }
            }
        }
    }
}


private data class NavItem(
    val tab: NavigationTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val contentDescription: String,
    val testTag: String,
)

private val navItems = listOf(
    NavItem(NavigationTab.Decks, Icons.Default.Layers, "Decks", "Decks Dashboard", "nav_decks_tab"),
    NavItem(NavigationTab.Path, Icons.Default.Explore, "Quest", "Gamified Path", "nav_path_tab"),
    NavItem(NavigationTab.Import, Icons.Default.AddCircle, "Import", "One-Tap Import / Create", "nav_import_tab"),
    NavItem(NavigationTab.Tutorial, Icons.Default.Lightbulb, "Guide", "Visual Onboarding Tutorial", "nav_tutorial_tab"),
)

@Composable
fun ZeroLanguageNavigationBar(
    activeTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
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
        navItems.forEach { item ->
            val selected = activeTab == item.tab
            val tint = if (selected) c.accent else c.textSecondary
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(item.tab) },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                        tint = tint
                    )
                },
                label = {
                    Text(
                        item.label,
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


