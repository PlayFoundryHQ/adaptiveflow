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
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdaptiveFlowTheme(darkTheme = false, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFEEF2FF), // Indigo Mist
                                        Color(0xFFF5F3FF), // Lavender Shimmer
                                        Color(0xFFF8FAFC)  // Soft Pearl Base
                                    )
                                )
                            )
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


@Composable
fun ZeroLanguageNavigationBar(
    activeTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .testTag("navigation_bar"),
        containerColor = Color.White.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = activeTab == NavigationTab.Decks,
            onClick = { onTabSelected(NavigationTab.Decks) },
            modifier = Modifier.testTag("nav_decks_tab"),
            icon = {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Decks Dashboard",
                    tint = if (activeTab == NavigationTab.Decks) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            label = {
                Text(
                    "Decks",
                    fontWeight = if (activeTab == NavigationTab.Decks) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (activeTab == NavigationTab.Decks) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color(0xFFEFF6FF)
            )
        )

        NavigationBarItem(
            selected = activeTab == NavigationTab.Path,
            onClick = { onTabSelected(NavigationTab.Path) },
            modifier = Modifier.testTag("nav_path_tab"),
            icon = {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Gamified Path",
                    tint = if (activeTab == NavigationTab.Path) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            label = {
                Text(
                    "Quest",
                    fontWeight = if (activeTab == NavigationTab.Path) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (activeTab == NavigationTab.Path) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color(0xFFEFF6FF)
            )
        )

        NavigationBarItem(
            selected = activeTab == NavigationTab.Import,
            onClick = { onTabSelected(NavigationTab.Import) },
            modifier = Modifier.testTag("nav_import_tab"),
            icon = {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "One-Tap Import / Create",
                    tint = if (activeTab == NavigationTab.Import) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            label = {
                Text(
                    "Import",
                    fontWeight = if (activeTab == NavigationTab.Import) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (activeTab == NavigationTab.Import) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color(0xFFEFF6FF)
            )
        )

        NavigationBarItem(
            selected = activeTab == NavigationTab.Tutorial,
            onClick = { onTabSelected(NavigationTab.Tutorial) },
            modifier = Modifier.testTag("nav_tutorial_tab"),
            icon = {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Visual Onboarding Tutorial",
                    tint = if (activeTab == NavigationTab.Tutorial) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            label = {
                Text(
                    "Guide",
                    fontWeight = if (activeTab == NavigationTab.Tutorial) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    color = if (activeTab == NavigationTab.Tutorial) Color(0xFF0054D1) else Color(0xFF64748B)
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color(0xFFEFF6FF)
            )
        )
    }
}


