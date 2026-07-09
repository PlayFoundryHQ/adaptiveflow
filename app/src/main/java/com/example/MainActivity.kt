package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatLog
import com.example.data.model.Deck
import com.example.data.model.Flashcard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StudyViewModel
import com.example.ui.components.DiagnosticLogsDialog
import com.example.ui.viewmodel.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private val viewModel: StudyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
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
    var activeTab by remember { mutableStateOf(NavigationTab.Decks) }
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

// Helper to look up our generated illustration dynamically to prevent resource errors
@Composable
fun rememberOnboardingHeroId(): Int {
    val context = LocalContext.current
    return remember(context) {
        val resId = context.resources.getIdentifier(
            "onboarding_hero_1783182758531",
            "drawable",
            context.packageName
        )
        if (resId != 0) resId else android.R.drawable.ic_menu_gallery
    }
}

@Composable
fun TutorialTab() {
    val heroResId = rememberOnboardingHeroId()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("tutorial_screen"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Header
            Text(
                text = "AdaptiveFlow",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Universal Semantic Learning",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }

        item {
            // Generated Visual Artwork Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = heroResId),
                        contentDescription = "AdaptiveFlow Onboarding Hero Scene",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0x77000000), Color(0xBB000000))
                                )
                            )
                    )
                    Text(
                        text = "ZERO TEXT CONFIGURATION",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        item {
            // Pillars
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TutorialPillarItem(
                    icon = Icons.Default.AllInclusive,
                    title = "Universal Language Flow",
                    desc = "Drag, paste, or select words in any custom format. AI parses the deck structure, detects flashcard pairs, and designs the session instantly."
                )

                TutorialPillarItem(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Predictive Context",
                    desc = "No tedious language configuration screens. The engine automatically predicts both source and target language in real-time."
                )

                TutorialPillarItem(
                    icon = Icons.Default.SupportAgent,
                    title = "Empathetic AI Tutor",
                    desc = "Struggling on a card? The tutor adjusts its explanation style dynamically—giving comforting advice and simpler analogies in your native language."
                )
            }
        }
    }
}

@Composable
fun TutorialPillarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0054D1),
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFF1E293B),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// Data structures for styling our decks beautifully with custom themes based on languages
data class DeckStyle(
    val startColor: Color,
    val endColor: Color,
    val textColor: Color,
    val iconBgColor: Color,
    val emoji: String,
    val bannerBg: Brush
)

fun getStyleForLanguage(lang: String?, deckName: String): DeckStyle {
    val clean = (lang ?: deckName).lowercase().trim()
    return when {
        clean.contains("persian") || clean.contains("farsi") || clean.contains("🌸") || clean.contains("سلام") -> DeckStyle(
            startColor = Color(0xFFFFF1F2),
            endColor = Color(0xFFFCE7F3),
            textColor = Color(0xFFE11D48),
            iconBgColor = Color(0xFFFFE4E6),
            emoji = "🌸",
            bannerBg = Brush.horizontalGradient(listOf(Color(0xFFFDA4AF), Color(0xFFF472B6)))
        )
        clean.contains("french") || clean.contains("culinary") || clean.contains("🇫🇷") -> DeckStyle(
            startColor = Color(0xFFEFF6FF),
            endColor = Color(0xFFDBEAFE),
            textColor = Color(0xFF2563EB),
            iconBgColor = Color(0xFFDBEAFE),
            emoji = "🇫🇷",
            bannerBg = Brush.horizontalGradient(listOf(Color(0xFF60A5FA), Color(0xFF3B82F6)))
        )
        clean.contains("japanese") || clean.contains("essential") || clean.contains("🇯🇵") || clean.contains("arigatou") -> DeckStyle(
            startColor = Color(0xFFFFF5F5),
            endColor = Color(0xFFFEE2E2),
            textColor = Color(0xFFDC2626),
            iconBgColor = Color(0xFFFEE2E2),
            emoji = "🇯🇵",
            bannerBg = Brush.horizontalGradient(listOf(Color(0xFFF87171), Color(0xFFEF4444)))
        )
        clean.contains("spanish") || clean.contains("espanol") || clean.contains("🇪🇸") -> DeckStyle(
            startColor = Color(0xFFFFFBEB),
            endColor = Color(0xFFFEF3C7),
            textColor = Color(0xFFD97706),
            iconBgColor = Color(0xFFFEF3C7),
            emoji = "🇪🇸",
            bannerBg = Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
        )
        else -> DeckStyle(
            startColor = Color(0xFFEEF2FF),
            endColor = Color(0xFFE0E7FF),
            textColor = Color(0xFF4F46E5),
            iconBgColor = Color(0xFFE0E7FF),
            emoji = "🧠",
            bannerBg = Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF6366F1)))
        )
    }
}

@Composable
fun DecksTab(
    viewModel: StudyViewModel,
    onNavigateToImport: () -> Unit
) {
    val decks by viewModel.allDecks.collectAsStateWithLifecycle()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isGoalSettingsOpen by remember { mutableStateOf(false) }

    if (isSettingsOpen) {
        ApiKeySettingsDialog(viewModel = viewModel, onDismiss = { isSettingsOpen = false })
    }

    if (isGoalSettingsOpen) {
        LearningGoalSettingsDialog(viewModel = viewModel, onDismiss = { isGoalSettingsOpen = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("decks_screen")
    ) {
        // API Key settings floating indicator
        IconButton(
            onClick = { isSettingsOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9))
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = "API Key Configuration",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }

        if (decks.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = "Your Library is Empty",
                    color = Color(0xFF1E293B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Your library is currently empty. Import some new study material from YouTube, PDF, or raw text to continue learning!",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onNavigateToImport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Import / Create Deck", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Dashboard Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Decks",
                            color = Color(0xFF1E293B),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Universal Study Dashboard",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7))
                                .clickable { isGoalSettingsOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Active Study Goal Settings",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9))
                                .clickable { isSettingsOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "API Key Configuration",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF))
                                .clickable { onNavigateToImport() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create New Deck",
                                tint = Color(0xFF0054D1),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Beautiful Premium Welcome Banner Card with Active Learning Goal
                val nativeLanguage by viewModel.nativeLanguage.collectAsStateWithLifecycle()
                val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable { isGoalSettingsOpen = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(modifier = Modifier.align(Alignment.CenterStart)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF0284C7))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ACTIVE STUDY GOAL",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = "• Click to edit",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Learn $targetLanguage from $nativeLanguage",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "AdaptiveFlow auto-tunes local fallback assistance and cloud generators specifically to $targetLanguage. Tap any deck below to study!",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier
                                .size(80.dp)
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp)
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(decks) { deckWithCards ->
                        DeckCardItem(
                            deckWithCards = deckWithCards,
                            onClick = { viewModel.selectDeck(deckWithCards.deck) }
                        )
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { onNavigateToImport() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 8.dp)
                .testTag("create_deck_fab"),
            containerColor = Color(0xFF0054D1),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = "New Deck"
            )
        }
    }
}

@Composable
fun DeckCardItem(
    deckWithCards: com.example.data.model.DeckWithCards,
    onClick: () -> Unit
) {
    val deck = deckWithCards.deck
    val cards = deckWithCards.flashcards
    val style = getStyleForLanguage(deck.sourceLanguage, deck.name)

    val totalCards = cards.size
    val learnedCards = cards.count { it.repetitions > 0 }
    val reviewsDue = cards.count { it.nextReview <= System.currentTimeMillis() }
    val learnedProgress = if (totalCards > 0) learnedCards.toFloat() / totalCards else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("deck_item_${deck.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circle Language Icon Badge
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(style.iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.emoji,
                        fontSize = 26.sp
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = deck.name,
                        color = Color(0xFF1E293B),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Source Language Label
                        if (!deck.sourceLanguage.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(style.iconBgColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = deck.sourceLanguage,
                                    color = style.textColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Arrow
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )

                        // Target Language Label
                        if (!deck.targetLanguage.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = deck.targetLanguage,
                                    color = Color(0xFF475569),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Arrow Right icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Divider
            HorizontalDivider(
                color = Color(0xFFF1F5F9),
                thickness = 1.dp
            )

            // Dynamic Progress Indicator
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mastery Progress",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$learnedCards / $totalCards Words (${(learnedProgress * 100).toInt()}%)",
                        color = Color(0xFF0F172A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { learnedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (learnedProgress >= 0.8f) Color(0xFF10B981) else style.textColor,
                    trackColor = Color(0xFFF1F5F9)
                )
            }

            // High-Value Memory stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total cards stat
                StatChip(
                    icon = Icons.Default.MenuBook,
                    label = "$totalCards Words",
                    contentColor = Color(0xFF475569),
                    backgroundColor = Color(0xFFF8FAFC),
                    modifier = Modifier.weight(1f)
                )

                // Mastered / Learned stat
                StatChip(
                    icon = Icons.Default.CheckCircle,
                    label = "$learnedCards Learned",
                    contentColor = Color(0xFF16A34A),
                    backgroundColor = Color(0xFFF0FDF4),
                    modifier = Modifier.weight(1f)
                )

                // Due cards stat
                StatChip(
                    icon = Icons.Default.Schedule,
                    label = if (reviewsDue > 0) "$reviewsDue Due" else "0 Due",
                    contentColor = if (reviewsDue > 0) Color(0xFFD97706) else Color(0xFF94A3B8),
                    backgroundColor = if (reviewsDue > 0) Color(0xFFFEF3C7) else Color(0xFFF8FAFC),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ImportTab(viewModel: StudyViewModel) {
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDiagnosticsOpen by remember { mutableStateOf(false) }

    if (isSettingsOpen) {
        ApiKeySettingsDialog(viewModel = viewModel, onDismiss = { isSettingsOpen = false })
    }

    if (isDiagnosticsOpen) {
        DiagnosticLogsDialog(viewModel = viewModel, onDismiss = { isDiagnosticsOpen = false })
    }

    var rawText by remember { mutableStateOf("") }
    var topicHint by remember { mutableStateOf("") }
    var selectedImportMode by remember { mutableStateOf("Text") } // "Text", "YouTube", "PDF"
    var isExternalHelperExpanded by remember { mutableStateOf(false) }
    
    var attachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var attachedFileName by remember { mutableStateOf("") }
    var attachedFileSize by remember { mutableStateOf(0L) }
    var attachedFileMimeType by remember { mutableStateOf("") }

    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val decks by viewModel.allDecks.collectAsStateWithLifecycle()
    var isMergeEnabled by remember { mutableStateOf(false) }
    var selectedMergeDeckId by remember { mutableStateOf<Int?>(null) }
    var isDeckDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDensity by remember { mutableStateOf("Balanced") } // "Focused", "Balanced", "Exhaustive"

    LaunchedEffect(decks) {
        if (selectedMergeDeckId == null && decks.isNotEmpty()) {
            val masterPool = decks.find { it.deck.name.contains("Master Vocabulary Pool") }
            if (masterPool != null) {
                selectedMergeDeckId = masterPool.deck.id
            } else {
                selectedMergeDeckId = decks.firstOrNull()?.deck?.id
            }
        }
    }

    // Observe Import States to show beautiful messages
    LaunchedEffect(importState) {
        when (importState) {
            is StudyViewModel.ImportState.Success -> {
                // Field clearing happens immediately, but the importState itself is left as Success
                // so the persistent banner below stays up until the user dismisses it - a plain
                // Toast.LENGTH_SHORT was easy to miss at the exact moment the form silently reset.
                rawText = ""
                topicHint = ""
                attachedFileUri = null
                attachedFileName = ""
                attachedFileSize = 0L
                attachedFileMimeType = ""
            }
            else -> {}
        }
    }

    // Helper to format file size
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${(kb * 10).toInt() / 10.0} KB"
        val mb = kb / 1024.0
        return "${(mb * 10).toInt() / 10.0} MB"
    }

    // Local Document Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                var name = "Selected File"
                var size = 0L

                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (sizeIndex != -1) {
                            size = it.getLong(sizeIndex)
                        }
                        if (nameIndex != -1) {
                            name = it.getString(nameIndex) ?: "Selected File"
                        }
                    }
                }

                attachedFileName = name
                attachedFileSize = size
                attachedFileMimeType = mimeType

                DiagnosticLogger.i("MainActivity", "User selected file: $name ($mimeType), pre-caching immediately...")

                // Copy picked Uri content immediately to prevent transient Android permission revoking
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val extension = if (mimeType.contains("pdf")) ".pdf" else if (mimeType.contains("csv")) ".csv" else ".txt"
                        val tempFile = File(context.cacheDir, "picked_" + System.currentTimeMillis() + extension)
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            attachedFileUri = Uri.fromFile(tempFile)
                            DiagnosticLogger.i("MainActivity", "Successfully cached picked file to local sandbox: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "Attached file $name successfully!", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            throw Exception("Cached file is empty or missing")
                        }
                    } catch (e: Exception) {
                        DiagnosticLogger.e("MainActivity", "Failed to cache selected file", e)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(context, "Failed to cache file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.e("MainActivity", "Error querying file info", e)
                Toast.makeText(context, "Error querying file info: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("import_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "One-Tap Import",
                        color = Color(0xFF1E293B),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Seamlessly build decks using raw texts, YouTube links, or PDF files.",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { isDiagnosticsOpen = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEF2F2))
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Diagnostic Logs",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { isSettingsOpen = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "API Key Configuration",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Segmented Control Tabs for Ingestion Modes
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    "Text" to "📝 Paste",
                    "YouTube" to "📺 YouTube",
                    "PDF" to "📄 PDF / Files"
                )
                modes.forEach { (modeKey, label) ->
                    val isSelected = selectedImportMode == modeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable {
                                selectedImportMode = modeKey
                                rawText = "" // clear text on mode swap to keep it clean
                                topicHint = ""
                                // Also clear any attached file - otherwise a file attached under one
                                // mode silently rides along into a later submission under another mode.
                                attachedFileUri = null
                                attachedFileName = ""
                                attachedFileSize = 0L
                                attachedFileMimeType = ""
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF0054D1) else Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Ingestion Guide Card based on Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF).copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedImportMode) {
                                "YouTube" -> Icons.Default.SmartDisplay
                                "PDF" -> Icons.Default.InsertDriveFile
                                else -> Icons.Default.Subject
                            },
                            contentDescription = null,
                            tint = Color(0xFF0054D1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (selectedImportMode) {
                                "YouTube" -> "How YouTube URL Import Works"
                                "PDF" -> "PDF & File Processing"
                                else -> "Structured Text Parsing"
                            },
                            color = Color(0xFF1E293B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (selectedImportMode) {
                                "YouTube" -> "Paste a video link (lessons, talk, song). Gemini analyzes the topic or transcript semantically to build a specialized study session automatically."
                                "PDF" -> "To study books or PDFs, copy-paste their text chapters or public links. You can also pick a local plain text file directly!"
                                else -> "Paste CSV pairs (Front, Back), vocabulary bullet lists, or raw sentences. The AI extracts translation pairs and generates pronunciation guides."
                            },
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // External AI Heavy-Lifter Prompt Guide Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExternalHelperExpanded) Color(0xFFFAF5FF) else Color(0xFFF8FAFC)
                ),
                border = BorderStroke(
                    1.dp, 
                    if (isExternalHelperExpanded) Color(0xFFE9D5FF) else Color(0xFFE2E8F0)
                )
            ) {
                Column(
                    modifier = Modifier
                        .clickable { isExternalHelperExpanded = !isExternalHelperExpanded }
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isExternalHelperExpanded) Color(0xFFF3E8FF) else Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExternalHelperExpanded) Icons.Default.AutoAwesome else Icons.Default.Help,
                                contentDescription = null,
                                tint = if (isExternalHelperExpanded) Color(0xFF9333EA) else Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "💡 Study Large PDFs, Books or Long Videos?",
                                color = if (isExternalHelperExpanded) Color(0xFF7E22CE) else Color(0xFF1E293B),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isExternalHelperExpanded) "Tap to collapse external guide" else "Tap for a copyable expert prompt to let ChatGPT/Claude/Gemini do the heavy lifting!",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = if (isExternalHelperExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand or collapse",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isExternalHelperExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFE9D5FF).copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Why inline parsers generate fewer cards for large files:",
                            color = Color(0xFF581C87),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("•", color = Color(0xFF9333EA), fontWeight = FontWeight.Bold)
                                Text(
                                    "Rate Limits & Token Caps: Multi-page text split into many sequential API requests often triggers protection limits or hits context limits on mobile connections.",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("•", color = Color(0xFF9333EA), fontWeight = FontWeight.Bold)
                                Text(
                                    "Layout Extraction: Scanned, encrypted, columns-based, or diagram-rich PDFs contain text streams that local parsers cannot always extract cleanly.",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("•", color = Color(0xFF9333EA), fontWeight = FontWeight.Bold)
                                Text(
                                    "Video Transcripts: YouTube restriction policies (like geo-fencing, age gates, or disabled captions) block client-side access to full transcripts.",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "THE ULTIMATE COPIABLE AGENT PROMPT",
                            color = Color(0xFF7E22CE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        val clipboardManager = LocalClipboardManager.current
                        val promptTemplate = """
                            You are a world-class Flashcard Deck Generator.
                            I will provide you with a long text, PDF segment, document, or video transcript.
                            Your job is to thoroughly analyze it and extract high-yield vocabulary words, specialized concepts, critical definitions, and core insights to build a comprehensive study deck.
                            
                            You MUST return ONLY a raw JSON string conforming exactly to the schema below. 
                            DO NOT wrap the response in markdown blocks like ```json or ```. 
                            Ensure the response is valid, parsable JSON.
                            
                            Output JSON Schema:
                            {
                              "deckName": "📄 Topic: [Insert Descriptive Topic Name]",
                              "sourceLanguage": "[Insert source material language or context, e.g., Swedish]",
                              "targetLanguage": "[Insert the translation/target language or context, e.g., English]",
                              "cards": [
                                {
                                  "front": "[Vocabulary word, phrase, or key concept]",
                                  "back": "[Clear translation, meaning, or concept breakdown]",
                                  "notes": "[Optional: Pronunciation guide, brief example sentence, or memory tip]"
                                }
                              ]
                            }
                            
                            Aesthetic guidelines:
                            - Extract at least 25 to 50 comprehensive, distinct flashcards.
                            - Make sure the 'front' contains the exact term or concept, and the 'back' has a detailed definition.
                            
                            Here is the source material to parse:
                            [PASTE YOUR PDF TEXT OR VIDEO TRANSCRIPT HERE]
                        """.trimIndent()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF5F3FF))
                                .border(1.dp, Color(0xFFDDD6FE), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = promptTemplate,
                                    color = Color(0xFF4C1D95),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(promptTemplate))
                                        Toast.makeText(context, "Copied Prompt to Clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E22CE)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Prompt",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("COPY AGENT PROMPT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "💡 How to import the result:",
                            color = Color(0xFF581C87),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Copy the prompt above and paste it into ChatGPT, Claude, or Gemini Advanced.\n2. Paste your long PDF text or video transcript at the bottom of the prompt.\n3. Run it, copy the resulting JSON, and switch to the '📝 Paste' tab above.\n4. Paste the JSON directly and tap 'PARSE DECK WITH AI' to import instantly!",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Attached File Card Component
        if (attachedFileUri != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (attachedFileMimeType.contains("pdf")) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (attachedFileMimeType.contains("pdf")) Color(0xFFEF4444) else Color(0xFF22C55E),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachedFileName,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ready to parse • ${formatFileSize(attachedFileSize)}",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                attachedFileUri = null
                                attachedFileName = ""
                                attachedFileSize = 0L
                                attachedFileMimeType = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove file",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Ingestion Input Box
        item {
            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .testTag("import_paste_input"),
                placeholder = {
                    Text(
                        text = if (attachedFileUri != null) {
                            "Add optional instructions or custom context for parsing the attached file (e.g. 'Focus on medical phrases only', 'Only extract verbs')..."
                        } else {
                            when (selectedImportMode) {
                                "YouTube" -> "Paste any YouTube video link here...\ne.g., https://www.youtube.com/watch?v=Y8YAs_76Iio"
                                "PDF" -> "Paste copied PDF text, book chapters, public PDF link, or tap 'LOAD FILE' below..."
                                else -> "Paste vocabulary list here:\n- Bonjour: Hello\n- Arigatou: Thank you\nOr paste any natural prose paragraphs!"
                            }
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0054D1),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() }
                )
            )
        }

        // Optional Focus Language / Topic Focus Hint Box
        item {
            OutlinedTextField(
                value = topicHint,
                onValueChange = { topicHint = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("import_topic_hint_input"),
                placeholder = {
                    Text(
                        text = "e.g. 'Swedish', 'French idioms', 'Medical vocabulary'",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                },
                label = {
                    Text(
                        text = "Focus Language or Topic (Highly Recommended)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0054D1),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { keyboardController?.hide() }
                )
            )
        }

        // Auxiliary Tools (Quick Upload or Quick-Fill Templates)
        if (selectedImportMode == "Text" || selectedImportMode == "PDF") {
            item {
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0054D1)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload local file",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "LOAD LOCAL FILE (.PDF / .TXT / .CSV)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        } else if (selectedImportMode == "YouTube") {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tap to test with a preloaded educational video link:",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rawText = "https://www.youtube.com/watch?v=Y8YAs_76Iio"
                                topicHint = "French (song lyrics and idioms)"
                            },
                            enabled = importState !is StudyViewModel.ImportState.Loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🇫🇷 French Song Lesson", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                rawText = "https://www.youtube.com/watch?v=pPy7643bZGo"
                                topicHint = "Japanese (Tokyo travel phrases)"
                            },
                            enabled = importState !is StudyViewModel.ImportState.Loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("🇯🇵 Tokyo Travel Phrases", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Unified Knowledge Pool (DVES) Enrichment Settings Panel
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMergeEnabled) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
                ),
                border = BorderStroke(
                    width = 1.2.dp,
                    color = if (isMergeEnabled) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isMergeEnabled) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = if (isMergeEnabled) Color(0xFF16A34A) else Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Smart Merge & Enrich",
                                    color = if (isMergeEnabled) Color(0xFF15803D) else Color(0xFF1E293B),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Append terms to a unified master pool",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = isMergeEnabled,
                            onCheckedChange = { isMergeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF16A34A)
                            )
                        )
                    }

                    if (isMergeEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFBBF7D0).copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "SELECT TARGET STUDY POOL / DECK:",
                            color = Color(0xFF15803D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val targetDeck = decks.find { it.deck.id == selectedMergeDeckId }?.deck
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(12.dp))
                                .clickable { isDeckDropdownExpanded = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = targetDeck?.name ?: "Select study pool...",
                                        color = Color(0xFF1E293B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    tint = Color(0xFF16A34A)
                                )
                            }

                            DropdownMenu(
                                expanded = isDeckDropdownExpanded,
                                onDismissRequest = { isDeckDropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(Color.White)
                            ) {
                                decks.forEach { deckWithCards ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = if (deckWithCards.deck.name.contains("Master Vocabulary Pool")) Color(0xFF8B5CF6) else Color(0xFF16A34A),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = deckWithCards.deck.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = "${deckWithCards.flashcards.size} cards",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedMergeDeckId = deckWithCards.deck.id
                                            isDeckDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Smart merge will automatically check for existing terms in the target pool. New context sentences are appended to existing cards instead of creating duplicates!",
                                    color = Color(0xFF15803D),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Extraction Density Settings Panel
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DensityMedium,
                                contentDescription = null,
                                tint = Color(0xFF0054D1),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Card Extraction Density",
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tune flashcard generation volume per chunk",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val densityOptions = listOf(
                            "Focused" to "🎯 Focused",
                            "Balanced" to "⚖️ Balanced",
                            "Exhaustive" to "🧬 Exhaustive"
                        )
                        densityOptions.forEach { (densityKey, label) ->
                            val isSelected = selectedDensity == densityKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color.White else Color.Transparent)
                                    .clickable { selectedDensity = densityKey }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFF0054D1) else Color(0xFF64748B),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (densityKey) {
                                            "Focused" -> "8-10 cards"
                                            "Balanced" -> "All unique vocab"
                                            "Exhaustive" -> "Uncapped (full document)"
                                            else -> ""
                                        },
                                        color = if (isSelected) Color(0xFF0054D1).copy(alpha = 0.7f) else Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            // Submit Ingest Button
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.importDeckFromRawText(
                        rawText = rawText,
                        topicHint = topicHint,
                        fileUri = attachedFileUri,
                        mergeDeckId = if (isMergeEnabled) selectedMergeDeckId else null,
                        density = selectedDensity
                    )
                },
                enabled = (rawText.isNotBlank() || attachedFileUri != null) && importState !is StudyViewModel.ImportState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("import_parse_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0054D1),
                    disabledContainerColor = Color(0xFFCBD5E1)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (importState is StudyViewModel.ImportState.Loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = (importState as StudyViewModel.ImportState.Loading).message,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                        Text(
                            text = if (selectedImportMode == "YouTube") "AUTOMAGIC GENERATE" else "PARSE DECK WITH AI",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        val currentImportState = importState
        if (currentImportState is StudyViewModel.ImportState.Error) {
            item {
                // Persistent, dismissible error banner. Replaces a transient Toast (LENGTH_LONG) which
                // was easy to miss since it auto-dismisses in ~3.5s with no lasting visual change.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFDECEC))
                        .border(1.dp, Color(0xFFF5A3A3), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFC62828),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = currentImportState.message,
                        color = Color(0xFF8B1D1D),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.resetImportState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFC62828),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (currentImportState is StudyViewModel.ImportState.Success) {
            item {
                // Persistent, dismissible success banner - mirrors the error banner above. Replaces a
                // plain Toast.LENGTH_SHORT that fired at the exact moment the form silently cleared,
                // which was easy to miss.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE7F8EF))
                        .border(1.dp, Color(0xFFA3E5C2), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF1B8A4C),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Deck saved: ${currentImportState.deckName}",
                        color = Color(0xFF14532D),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.resetImportState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF1B8A4C),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            // Instant Quick-Start Sample Seeds
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Instant Quick-Start Sample Decks",
                    color = Color(0xFF0054D1),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                QuickSeedDeckButton(
                    title = "🇫🇷 French Culinary Terms",
                    desc = "Terms like Le pain, Le beurre, Le fromage",
                    enabled = importState !is StudyViewModel.ImportState.Loading,
                    onClick = {
                        viewModel.importDeckFromRawText(
                            "Le pain: The bread\nLe beurre: The butter\nLe fromage: The cheese\nLe vin: The wine\nLe café: The coffee"
                        )
                    }
                )

                QuickSeedDeckButton(
                    title = "🇯🇵 Japanese Essential Travel",
                    desc = "Basic traveling phrases like Sumimasen, Arigatou",
                    enabled = importState !is StudyViewModel.ImportState.Loading,
                    onClick = {
                        viewModel.importDeckFromRawText(
                            "Arigatou: Thank you (informal)\nSumimasen: Excuse me / Sorry\nKonnichiwa: Hello / Good afternoon\nSayounara: Goodbye\nKore wa ikura desu ka: How much is this?"
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun QuickSeedDeckButton(
    title: String,
    desc: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val emoji = when {
        title.contains("French") -> "🇫🇷"
        title.contains("Japanese") -> "🇯🇵"
        else -> "⚡"
    }

    val style = when {
        title.contains("French") -> getStyleForLanguage("French", "")
        title.contains("Japanese") -> getStyleForLanguage("Japanese", "")
        else -> getStyleForLanguage("", "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(style.iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.replace("🇫🇷 ", "").replace("🇯🇵 ", ""),
                color = Color(0xFF1E293B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Quick Seed",
                tint = Color(0xFF0054D1),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StudySessionScreen(viewModel: StudyViewModel) {
    val context = LocalContext.current
    val deck by viewModel.currentDeck.collectAsStateWithLifecycle()
    val cards by viewModel.currentFlashcards.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentCardIndex.collectAsStateWithLifecycle()
    val isFlipped by viewModel.isCardFlipped.collectAsStateWithLifecycle()
    val flippedCardIds by viewModel.flippedCardIds.collectAsStateWithLifecycle()

    // Streaks for AI Tutor difficulty adaptation indicator
    val correctTotal by viewModel.sessionCorrectCount.collectAsStateWithLifecycle()
    val incorrectTotal by viewModel.sessionIncorrectCount.collectAsStateWithLifecycle()
    val consecutiveStruggles by viewModel.consecutiveIncorrectStreak.collectAsStateWithLifecycle()

    var showTutorSheet by remember { mutableStateOf(false) }
    var showContextDrawer by remember { mutableStateOf(false) }
    var isSessionStarted by remember(deck?.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Without this, system back/gesture falls through to the Activity and exits the app entirely
    // instead of returning to the deck library. Close any open overlay first, then fall back to
    // leaving the session, so back behaves the same way tapping the in-app back arrow does.
    BackHandler {
        when {
            showTutorSheet -> showTutorSheet = false
            showContextDrawer -> showContextDrawer = false
            else -> viewModel.clearActiveDeck()
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
            },
            title = {
                Text(
                    text = "Delete \"${deck?.name ?: "this deck"}\"?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    text = "This permanently deletes every card and all study progress in this deck. This cannot be undone.",
                    color = Color(0xFF64748B)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteCurrentDeck()
                }) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    val isAutoPlayEnabled by viewModel.isAutoPlayTtsEnabled.collectAsStateWithLifecycle()
    val isTtsReady by viewModel.isTtsReady.collectAsStateWithLifecycle()

    // Native TTS speech auto play on card index transition
    LaunchedEffect(currentIndex, isSessionStarted) {
        if (isSessionStarted && cards.isNotEmpty() && isAutoPlayEnabled && isTtsReady) {
            val activeCard = cards.getOrNull(currentIndex)
            if (activeCard != null) {
                viewModel.speak(activeCard.front)
            }
        }
    }

    if (cards.isNotEmpty() && flippedCardIds.size >= cards.size) {
        AlertDialog(
            onDismissRequest = { /* Keep focus on summary options */ },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD1FAE5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎉", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Session Complete!",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Outstanding work! You have flipped every card in the **${deck?.name}** deck to help visualize and lock in your vocabulary learning.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${cards.size}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0054D1)
                            )
                            Text(
                                text = "Total Cards",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$correctTotal",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Correct",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$incorrectTotal",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEF4444)
                            )
                            Text(
                                text = "Incorrect",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restartSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restart_session_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESTART SESSION", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.clearActiveDeck()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exit_session_button")
                ) {
                    Text("EXIT TO LIBRARY", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("study_screen")
    ) {
        if (!isSessionStarted) {
            // Upper Navigation (for Back) on Prep Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.clearActiveDeck() },
                        modifier = Modifier.testTag("study_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }

                    Text(
                        text = deck?.name ?: "",
                        color = Color(0xFF1E293B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (deck?.name?.contains("Master Vocabulary Pool") != true) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.testTag("delete_deck_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Delete Deck",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "This is your protected Master Vocabulary Pool and can't be deleted.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "This deck is protected and can't be deleted",
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                // Beautiful Prep Screen centered details and big Play Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color(0xFF0054D1),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Dynamic Study Session",
                        color = Color(0xFF1E293B),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Adaptive Flow blends native pronunciations, smart flashcards, and multiple-choice quiz questions based on your real-time performance.",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Pulsing/stunning Circular Play Button
                    Button(
                        onClick = {
                            isSessionStarted = true
                            viewModel.togglePlayMode(true)
                        },
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .testTag("play_session_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0054D1)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "PLAY",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Active Study Session
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Upper Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.clearActiveDeck() },
                        modifier = Modifier.testTag("study_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }

                    Text(
                        text = deck?.name ?: "",
                        color = Color(0xFF1E293B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showContextDrawer = true },
                        modifier = Modifier.testTag("context_drawer_trigger")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Session Options",
                            tint = Color(0xFF0054D1)
                        )
                    }

                    if (deck?.name?.contains("Master Vocabulary Pool") != true) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.testTag("delete_deck_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Delete Deck",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "This is your protected Master Vocabulary Pool and can't be deleted.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "This deck is protected and can't be deleted",
                                tint = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                if (cards.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF0054D1))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading Cards...", color = Color(0xFF1A1C1E))
                        }
                    }
                } else {
                    val activeCard = cards.getOrNull(currentIndex) ?: cards[0]

                    // Flipped Cards Progress Bar at the Top of Session
                    val flippedCount = flippedCardIds.size
                    val flippedProgress = if (cards.isNotEmpty()) flippedCount.toFloat() / cards.size.toFloat() else 0f
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔄", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Flip Session Progress",
                                    color = Color(0xFF1E293B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "$flippedCount of ${cards.size} flipped",
                                color = Color(0xFF10B981),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { flippedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF10B981), // Beautiful green
                            trackColor = Color(0xFFD1FAE5)
                        )
                    }

                    // Progress Indicators & Real-Time Statistics
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (currentIndex + 1).toFloat() / cards.size.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF0054D1),
                            trackColor = Color(0xFFDDE1FF)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentIndex + 1} / ${cards.size}",
                                color = Color(0xFF44474E),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Compact AI Adaptive Difficulty Indicator
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AI Adaptive Status:",
                                    color = Color(0xFF44474E),
                                    fontSize = 10.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (consecutiveStruggles >= 2) Color(0xFFFFA726) // Supportive Mode
                                            else Color(0xFF66BB6A) // Standard Mode
                                        )
                                )
                                Text(
                                    text = if (consecutiveStruggles >= 2) "Supportive" else "Standard",
                                    color = if (consecutiveStruggles >= 2) Color(0xFFFFA726) else Color(0xFF66BB6A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Check active card learning mode to mix quiz questions and flashcards
                    val activeCardMode = viewModel.getLearningModeForCard(activeCard.id)

                    if (activeCardMode == StudyViewModel.LearningMode.Quiz) {
                        // MULTIPLE CHOICE QUIZ ENGINE VIEW
                        val options = remember(activeCard.id) { viewModel.getMultipleChoiceOptions(activeCard) }
                        var selectedOption by remember(activeCard.id) { mutableStateOf<String?>(null) }
                        var isAnswerChecked by remember(activeCard.id) { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Question Box
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.42f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFDDE1FF)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, Color(0xFFC4C6D0), RoundedCornerShape(24.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = { viewModel.speak(activeCard.front) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .testTag("speak_front_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Read Aloud",
                                            tint = Color(0xFF001453),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = activeCard.front,
                                            color = Color(0xFF001453),
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "CHOOSE THE CORRECT MEANING",
                                            color = Color(0xFF0054D1),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }

                            // Choices Grid / List
                            Column(
                                modifier = Modifier
                                    .weight(0.58f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                options.forEach { option ->
                                    val isSelected = selectedOption == option
                                    val isCorrect = option == activeCard.back

                                    val backgroundColor = when {
                                        !isAnswerChecked -> Color.White
                                        isCorrect -> Color(0xFFE8F5E9)
                                        isSelected -> Color(0xFFFFEBEE)
                                        else -> Color.White
                                    }

                                    val textColor = when {
                                        !isAnswerChecked -> Color(0xFF1A1C1E)
                                        isCorrect -> Color(0xFF2E7D32)
                                        isSelected -> Color(0xFFC62828)
                                        else -> Color(0xFF44474E)
                                    }

                                    val borderColor = when {
                                        !isAnswerChecked -> if (isSelected) Color(0xFF0054D1) else Color(0xFFC4C6D0)
                                        isCorrect -> Color(0xFF81C784)
                                        isSelected -> Color(0xFFE57373)
                                        else -> Color(0xFFC4C6D0)
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isAnswerChecked) {
                                                selectedOption = option
                                                isAnswerChecked = true
                                                viewModel.speak(option)
                                            }
                                            .testTag("choice_item_${option.hashCode()}"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = backgroundColor
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = if (isSelected || (isAnswerChecked && isCorrect)) 2.dp else 1.dp,
                                                    color = borderColor,
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = option,
                                                color = textColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )

                                            if (isAnswerChecked) {
                                                if (isCorrect) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Correct",
                                                        tint = Color(0xFF2E7D32)
                                                    )
                                                } else if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cancel,
                                                        contentDescription = "Incorrect",
                                                        tint = Color(0xFFC62828)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom dynamic validation controls
                            if (isAnswerChecked) {
                                Button(
                                    onClick = {
                                        val correct = selectedOption == activeCard.back
                                        viewModel.recordCardAnswer(isCorrect = correct)
                                        selectedOption = null
                                        isAnswerChecked = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("quiz_continue_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0054D1)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("CONTINUE", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(52.dp))
                            }
                        }
                    } else {
                        // STANDARD FLASHCARD FLIP MODE VIEW
                        // Interactive Flipping Card Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InteractiveFlashcard(
                                card = activeCard,
                                isFlipped = isFlipped,
                                onFlip = { viewModel.flipCard() },
                                onSpeak = { viewModel.speak(it) },
                                onConfidenceSelected = { viewModel.recordCardConfidence(it) }
                            )
                        }

                        // Review / Interaction Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (isFlipped) {
                                Text(
                                    text = "Choose your recall confidence level on the card to update the study schedule.",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            } else {
                                // Tap to Reveal Hint
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF0054D1))
                                        .clickable { viewModel.flipCard() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "REVEAL TARGET WORD",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }

                    // AI Tutor Floating/Sliding Trigger
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFC4C6D0), RoundedCornerShape(12.dp))
                            .clickable { showTutorSheet = true }
                            .testTag("ai_tutor_trigger"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE8F5E9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "AI Help",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "HELP / ASK AI TUTOR",
                                        color = Color(0xFF1A1C1E),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Ask pronunciation, origin or usage hints",
                                        color = Color(0xFF44474E),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Open Chat",
                                tint = Color(0xFF0054D1),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Bottom Sheet 1: Advanced Session options Context Drawer ---
    AnimatedVisibility(
        visible = showContextDrawer,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { showContextDrawer = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFC4C6D0), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(enabled = false) {}
                    .padding(24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Controls",
                        color = Color(0xFF1A1C1E),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = { showContextDrawer = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF1A1C1E)
                        )
                    }
                }

                Divider(color = Color(0xFFC4C6D0))

                // Play Mode style
                val isPlayModeActive by viewModel.isPlayModeActive.collectAsStateWithLifecycle()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fluid Play Mode",
                            color = Color(0xFF1A1C1E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Auto-mix flashcards and smart quiz questions",
                            color = Color(0xFF44474E),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isPlayModeActive,
                        onCheckedChange = { viewModel.togglePlayMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0054D1)
                        )
                    )
                }

                // Auto-Play Pronunciation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Auto-Play Pronunciation",
                            color = Color(0xFF1A1C1E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Speak terms automatically when loaded",
                            color = Color(0xFF44474E),
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isAutoPlayEnabled,
                        onCheckedChange = { viewModel.toggleAutoPlayTts(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0054D1)
                        )
                    )
                }

                // TTS speed slider
                val speechRate by viewModel.ttsRate.collectAsStateWithLifecycle()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pronunciation Speed",
                            color = Color(0xFF1A1C1E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${speechRate}x",
                            color = Color(0xFF0054D1),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.setTtsRate(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0054D1),
                            activeTrackColor = Color(0xFF0054D1),
                            inactiveTrackColor = Color(0xFFDDE1FF)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // --- Bottom Sheet 2: AI Tutor empathy chat ---
    AnimatedVisibility(
        visible = showTutorSheet,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable { showTutorSheet = false }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFFF7F9FF))
                    .border(1.dp, Color(0xFFC4C6D0), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(enabled = false) {}
            ) {
                AiTutorBottomSheet(
                    viewModel = viewModel,
                    onClose = { showTutorSheet = false }
                )
            }
        }
    }
}

fun getIconForCard(card: Flashcard): ImageVector {
    val term = (card.front + " " + card.back).lowercase()
    return when {
        term.contains("apple") || term.contains("seeb") || term.contains("fruta") || term.contains("fruit") -> Icons.Default.Spa
        term.contains("heart") || term.contains("corazón") || term.contains("love") || term.contains("amor") -> Icons.Default.Favorite
        term.contains("book") || term.contains("ketāb") || term.contains("libro") || term.contains("read") || term.contains("study") -> Icons.Default.MenuBook
        term.contains("friend") || term.contains("doost") || term.contains("amigo") || term.contains("people") || term.contains("person") -> Icons.Default.People
        term.contains("sun") || term.contains("khorsheed") || term.contains("sol") || term.contains("light") -> Icons.Default.WbSunny
        term.contains("star") || term.contains("estrella") -> Icons.Default.Star
        term.contains("car") || term.contains("coche") || term.contains("auto") -> Icons.Default.DirectionsCar
        term.contains("music") || term.contains("música") || term.contains("song") || term.contains("sound") -> Icons.Default.MusicNote
        term.contains("water") || term.contains("agua") || term.contains("rain") -> Icons.Default.WaterDrop
        term.contains("home") || term.contains("house") || term.contains("casa") -> Icons.Default.Home
        term.contains("time") || term.contains("clock") || term.contains("hora") || term.contains("reloj") -> Icons.Default.AccessTime
        term.contains("money") || term.contains("dinero") || term.contains("cash") -> Icons.Default.AttachMoney
        term.contains("school") || term.contains("class") || term.contains("university") || term.contains("profesor") -> Icons.Default.School
        term.contains("health") || term.contains("respiración") || term.contains("medical") || term.contains("presión") || term.contains("arterial") -> Icons.Default.HealthAndSafety
        term.contains("fire") || term.contains("fuego") -> Icons.Default.LocalFireDepartment
        term.contains("cloud") || term.contains("nube") -> Icons.Default.Cloud
        term.contains("coffee") || term.contains("cafe") || term.contains("drink") -> Icons.Default.LocalCafe
        term.contains("search") || term.contains("buscar") || term.contains("find") -> Icons.Default.Search
        term.contains("setting") || term.contains("config") -> Icons.Default.Settings
        term.contains("info") || term.contains("about") -> Icons.Default.Info
        else -> Icons.Default.School
    }
}

@Composable
fun InteractiveFlashcard(
    card: Flashcard,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onSpeak: (String) -> Unit,
    onConfidenceSelected: (StudyViewModel.ConfidenceLevel) -> Unit
) {
    // 3D Card Flipping Rotation Animation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "CardFlipAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { onFlip() }
            .testTag("flashcard_box"),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (rotation <= 90f) {
                        Brush.verticalGradient(
                            listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE))
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                        )
                    }
                )
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (rotation <= 90f) {
                // Front / Source Language Word
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { onSpeak(card.front) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("speak_front_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = Color(0xFF0054D1),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Dynamically selected elegant icon
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0x150054D1))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getIconForCard(card),
                                contentDescription = "Concept Icon",
                                tint = Color(0xFF0054D1),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = card.front,
                            color = Color(0xFF1E293B),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0054D1))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "SOURCE TERM",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Hidden Target Word Indicator
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x100054D1))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Hidden Target Word",
                                tint = Color(0xFF0054D1).copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Target word hidden (Tap card)",
                                color = Color(0xFF0054D1).copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                // Back / Target Language Meaning (Rotated back)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                ) {
                    IconButton(
                        onClick = { onSpeak(card.back) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("speak_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = Color(0xFF0054D1),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Small icon indicator at the top of back face
                        Icon(
                            imageVector = getIconForCard(card),
                            contentDescription = "Concept Icon Back",
                            tint = Color(0xFF0054D1).copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = card.back,
                            color = Color(0xFF0054D1),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        if (!card.notes.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = card.notes,
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEFF6FF))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TARGET TRANSLATION",
                                color = Color(0xFF0054D1),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Integrated User Confidence Rating Segment
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "RATE YOUR RECALL CONFIDENCE:",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Low Confidence / Practice / Hard Button
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(14.dp))
                                    .clickable { onConfidenceSelected(StudyViewModel.ConfidenceLevel.LOW) }
                                    .testTag("incorrect_answer_button")
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentVeryDissatisfied,
                                    contentDescription = "Low Confidence",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "LOW",
                                    color = Color(0xFF991B1B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Medium Confidence / Good Button
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFEF3C7))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(14.dp))
                                    .clickable { onConfidenceSelected(StudyViewModel.ConfidenceLevel.MEDIUM) }
                                    .testTag("medium_confidence_button")
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentNeutral,
                                    contentDescription = "Medium Confidence",
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "MEDIUM",
                                    color = Color(0xFF92400E),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // High Confidence / Easy / Mastered Button
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFD1FAE5))
                                    .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(14.dp))
                                    .clickable { onConfidenceSelected(StudyViewModel.ConfidenceLevel.HIGH) }
                                    .testTag("correct_answer_button")
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SentimentVerySatisfied,
                                    contentDescription = "High Confidence",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "HIGH",
                                    color = Color(0xFF065F46),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiTutorBottomSheet(
    viewModel: StudyViewModel,
    onClose: () -> Unit
) {
    val chatLogs by viewModel.chatLogs.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val currentCards by viewModel.currentFlashcards.collectAsStateWithLifecycle()
    val index by viewModel.currentCardIndex.collectAsStateWithLifecycle()
    val activeCard = if (index < currentCards.size) currentCards[index] else null

    var textInput by remember { mutableStateOf("") }
    var showClearChatConfirmation by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (showClearChatConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmation = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
            },
            title = {
                Text(
                    text = "Clear tutor chat history?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    text = "This permanently deletes this conversation with the AI Tutor. This cannot be undone.",
                    color = Color(0xFF64748B)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearChatConfirmation = false
                    viewModel.clearChatHistory()
                }) {
                    Text("Clear", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmation = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_tutor_sheet")
    ) {
        // Tutor Panel Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF0054D1),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "AI Adaptive Tutor",
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time semantic guidance",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showClearChatConfirmation = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Chat",
                        tint = Color(0xFF64748B)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF1E293B)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

        // Chats History
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (chatLogs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = Color(0xFF0054D1),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "How can I support your study today?",
                            color = Color(0xFF1E293B),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Ask for pronunciation, custom example sentences, or cultural origin hints in any language!",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                items(chatLogs) { log ->
                    ChatBubbleItem(log)
                }
            }

            if (isAiLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0054D1),
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Tutor is thinking...",
                            color = Color(0xFF0054D1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Smart Helper Prompt Chips
        if (activeCard != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmartHelperChip(
                    label = "💡 Pronounce",
                    onClick = {
                        viewModel.sendTutorMessage("How do I correctly pronounce this word? Give me phonetic details.")
                    }
                )
                SmartHelperChip(
                    label = "📚 Examples",
                    onClick = {
                        viewModel.sendTutorMessage("Give me 2 simple examples showing how this word is used in conversational sentences.")
                    }
                )
                SmartHelperChip(
                    label = "🗺️ Origin",
                    onClick = {
                        viewModel.sendTutorMessage("What is the cultural background or origin of this term?")
                    }
                )
            }
        }

        // Send Custom Prompt Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_tutor_input_field"),
                placeholder = {
                    Text(
                        text = "Ask AI in any language...",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0054D1),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank() && !isAiLoading) {
                            viewModel.sendTutorMessage(textInput)
                            textInput = ""
                            keyboardController?.hide()
                        }
                    }
                )
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (textInput.isBlank() || isAiLoading) Color(0xFFE2E8F0)
                        else Color(0xFF0054D1)
                    )
                    .clickable(enabled = textInput.isNotBlank() && !isAiLoading) {
                        viewModel.sendTutorMessage(textInput)
                        textInput = ""
                        keyboardController?.hide()
                    }
                    .testTag("ai_tutor_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isBlank() || isAiLoading) Color(0xFF94A3B8) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(log: ChatLog) {
    val isUser = log.sender == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(align = if (isUser) Alignment.End else Alignment.Start)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) Color(0xFF0054D1) else Color(0xFFF1F5F9)
                )
                .border(
                    1.dp,
                    if (isUser) Color(0xFF0054D1) else Color(0xFFE2E8F0),
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                text = log.message,
                color = if (isUser) Color.White else Color(0xFF1E293B),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SmartHelperChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF0054D1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PathTab(
    viewModel: StudyViewModel,
    onNavigateToImport: () -> Unit
) {
    val decks by viewModel.allDecks.collectAsStateWithLifecycle()
    val nativeLanguage by viewModel.nativeLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    var selectedNodeDeck by remember { mutableStateOf<com.example.data.model.DeckWithCards?>(null) }

    // Compute Crowns
    val crownsCount = remember(decks) {
        decks.count { dwc ->
            dwc.flashcards.isNotEmpty() && dwc.flashcards.all { it.repetitions > 0 }
        }
    }

    val totalXp = viewModel.getXp()
    val streakDays = viewModel.getStreak()

    // Filter matching decks for the active learning goal (matching target language)
    val matchingDecks = remember(decks, targetLanguage) {
        decks.filter { dwc ->
            dwc.deck.sourceLanguage?.contains(targetLanguage, ignoreCase = true) == true ||
            dwc.deck.name.contains(targetLanguage, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("path_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Gamified Status Top Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Streak
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (streakDays > 0) "$streakDays Days" else "0 Days",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Text("Streak", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }

                    // Vertical Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFFE2E8F0)))

                    // XP
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$totalXp XP",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Text("Total Points", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }

                    // Vertical Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFFE2E8F0)))

                    // Crowns
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👑", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (crownsCount > 0) "$crownsCount" else "0",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = Color(0xFF1E293B)
                            )
                        }
                        Text("Crowns Earned", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Goal Management Header Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFD97706))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "UNIFIED QUEST GOAL",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Learn $targetLanguage from $nativeLanguage",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Manage your global languages directly below. Your adaptive flashcards and tutors will instantly tune to this goal.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Interactive Goal Selectors Panel
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Goal Configuration",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            // Native Language Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Your Native Language:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                val nativeOptions = listOf("English", "Spanish", "French", "German")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(end = 12.dp)
                                ) {
                                    items(nativeOptions) { lang ->
                                        val isSelected = lang == nativeLanguage
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFF0054D1) else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.updateLearningGoal(lang, targetLanguage) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFF0054D1) else Color(0xFF475569)
                                            )
                                        }
                                    }
                                }
                            }

                            // Target Language Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Language to Learn (Target):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                val targetOptions = listOf("Swedish", "Spanish", "French", "German", "Italian", "Japanese", "Persian")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(end = 12.dp)
                                ) {
                                    items(targetOptions) { lang ->
                                        val isSelected = lang == targetLanguage
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFFFEF3C7) else Color(0xFFF1F5F9))
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color(0xFFD97706) else Color(0xFFE2E8F0),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.updateLearningGoal(nativeLanguage, lang) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color(0xFFB45309) else Color(0xFF475569)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Goal-specific Progress Tracker card
                item {
                    val matchingCards = matchingDecks.flatMap { it.flashcards }
                    val totalCards = matchingCards.size
                    val learnedCards = matchingCards.count { it.repetitions > 0 }
                    val progress = if (totalCards > 0) learnedCards.toFloat() / totalCards else 0f
                    val masteryPercent = (progress * 100).toInt()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "$targetLanguage Goal Progress",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFEF3C7)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🏆", fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "$learnedCards of $totalCards mastered",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = "${matchingDecks.size} active target decks",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Text(
                                    text = "$masteryPercent%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = Color(0xFFD97706)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFFD97706),
                                trackColor = Color(0xFFFEF3C7)
                            )
                        }
                    }
                }

                // Daily challenges gamified card
                item {
                    val progressChallengeXp = (totalXp.toFloat() / 500f).coerceAtMost(1f)
                    val progressChallengeCrowns = (crownsCount.toFloat() / 3f).coerceAtMost(1f)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Weekly Quests & Challenges",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            // Quest 1
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (streakDays > 0) "✅" else "⏳", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Maintain active daily study streak (🔥 $streakDays Days)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }

                            // Quest 2
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (totalXp >= 500) "✅" else "⚡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Reach 500 total study XP (Points: $totalXp / 500)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progressChallengeXp },
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFF3B82F6),
                                        trackColor = Color(0xFFDBEAFE)
                                    )
                                }
                            }

                            // Quest 3
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (crownsCount >= 3) "✅" else "👑", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Unlock 3 mastered deck crowns (Crowns: $crownsCount / 3)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progressChallengeCrowns },
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = Color(0xFFF59E0B),
                                        trackColor = Color(0xFFFEF3C7)
                                    )
                                }
                            }
                        }
                    }
                }

                // Matching Decks header
                item {
                    Text(
                        text = "Active Quest Decks for $targetLanguage",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (matchingDecks.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("📭", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Decks Match Your Goal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "We couldn't find any study materials configured for $targetLanguage in your library yet.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        viewModel.importDeckFromRawText(
                                            rawText = "Please generate 15 essential vocabulary terms for learning $targetLanguage from $nativeLanguage",
                                            topicHint = "$targetLanguage starter vocabulary",
                                            density = "Balanced"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("AI Quest Generator: Seed Starter Deck", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(matchingDecks) { deckWithCards ->
                        val deck = deckWithCards.deck
                        val total = deckWithCards.flashcards.size
                        val learned = deckWithCards.flashcards.count { it.repetitions > 0 }
                        val masteryProgress = if (total > 0) learned.toFloat() / total else 0f
                        val style = getStyleForLanguage(deck.sourceLanguage, deck.name)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("path_node_${deck.id}")
                                .clickable { selectedNodeDeck = deckWithCards },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(style.iconBgColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(style.emoji, fontSize = 24.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = deck.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$learned of $total words learned",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Progress ring representation
                                    CircularProgressIndicator(
                                        progress = { masteryProgress },
                                        modifier = Modifier.size(32.dp),
                                        color = style.textColor,
                                        strokeWidth = 3.dp,
                                        trackColor = Color(0xFFF1F5F9)
                                    )

                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Details",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Details Dialog for selected Node (Backward compatible details view)
        selectedNodeDeck?.let { deckWithCards ->
            val deck = deckWithCards.deck
            val cards = deckWithCards.flashcards
            val style = getStyleForLanguage(deck.sourceLanguage, deck.name)

            val total = cards.size
            val learned = cards.count { it.repetitions > 0 }
            val reviewsDue = cards.count { it.nextReview <= System.currentTimeMillis() }

            // Leitner box calculation distributions
            val box1Count = cards.count { it.repetitions == 0 }
            val box2Count = cards.count { it.repetitions == 1 }
            val box3Count = cards.count { it.repetitions == 2 }
            val box4Count = cards.count { it.repetitions == 3 }
            val box5Count = cards.count { it.repetitions >= 4 }

            var activeViewTab by remember { mutableStateOf("stats") } // "stats" or "grid"

            AlertDialog(
                onDismissRequest = { selectedNodeDeck = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(style.iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(style.emoji, fontSize = 32.sp)
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = deck.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${deck.sourceLanguage ?: "Source"} ➔ ${deck.targetLanguage ?: "Target"}",
                            fontSize = 12.sp,
                            color = style.textColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        // Clean visual tab selector mimicking modern web designs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (activeViewTab == "stats") Color.White else Color.Transparent)
                                    .clickable { activeViewTab = "stats" }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Overview Stats",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeViewTab == "stats") Color(0xFF1E293B) else Color(0xFF64748B)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (activeViewTab == "grid") Color.White else Color.Transparent)
                                    .clickable { activeViewTab = "grid" }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Vocabulary Grid",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeViewTab == "grid") Color(0xFF1E293B) else Color(0xFF64748B)
                                )
                            }
                        }

                        if (activeViewTab == "stats") {
                            // Core stats summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "$total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Text(text = "Total Words", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "$learned", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                    Text(text = "Learned", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "$reviewsDue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (reviewsDue > 0) Color(0xFFD97706) else Color(0xFF94A3B8))
                                    Text(text = "Due Now", fontSize = 11.sp, color = Color(0xFF64748B))
                                }
                            }

                            // Leitner Memory Stages visualization
                            Text(
                                text = "Leitner Memory Boxes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LeitnerBoxProgressRow(boxName = "Box 1: New / Unstudied", count = box1Count, total = total, color = Color(0xFF94A3B8))
                                LeitnerBoxProgressRow(boxName = "Box 2: Fresh Review", count = box2Count, total = total, color = Color(0xFF60A5FA))
                                LeitnerBoxProgressRow(boxName = "Box 3: Familiar", count = box3Count, total = total, color = Color(0xFF818CF8))
                                LeitnerBoxProgressRow(boxName = "Box 4: Highly Retained", count = box4Count, total = total, color = Color(0xFFF59E0B))
                                LeitnerBoxProgressRow(boxName = "Box 5: Mastered (Permanent)", count = box5Count, total = total, color = Color(0xFF10B981))
                            }
                        } else {
                            // Responsive vocabulary cards grid with icons & live interactive 3D click flip
                            ResponsiveVocabularyGrid(
                                cards = cards,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.selectDeck(deck)
                            selectedNodeDeck = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_quest_study_button")
                    ) {
                        Text(
                            text = if (reviewsDue > 0) "Study & Review ($reviewsDue Due)" else "Study Deck / Start Quiz",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { selectedNodeDeck = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Map details", color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun LeitnerBoxProgressRow(
    boxName: String,
    count: Int,
    total: Int,
    color: Color
) {
    val progress = if (total > 0) count.toFloat() / total else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(boxName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            Text("$count words", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E293B))
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = color,
            trackColor = Color(0xFFF1F5F9)
        )
    }
}

@Composable
fun ResponsiveVocabularyGrid(
    cards: List<Flashcard>,
    modifier: Modifier = Modifier
) {
    // State collection managing individual card flips
    var flippedCardIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 130.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        items(cards) { card ->
            val isFlipped = flippedCardIds.contains(card.id)
            
            // 3D flip animation transition (like modern rotateY CSS transform)
            val rotation by animateFloatAsState(
                targetValue = if (isFlipped) 180f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "GridCardFlip"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable {
                        flippedCardIds = if (isFlipped) {
                            flippedCardIds - card.id
                        } else {
                            flippedCardIds + card.id
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isFlipped) Color(0xFFF8FAFC) else Color(0xFFEFF6FF)
                ),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation <= 90f) {
                        // Front face: Icon and source word
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x150054D1)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getIconForCard(card),
                                    contentDescription = "Concept Icon",
                                    tint = Color(0xFF0054D1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = card.front,
                                color = Color(0xFF1E293B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        // Back face: Rotated correction + translation
                        Column(
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getIconForCard(card),
                                contentDescription = "Concept Icon Back",
                                tint = Color(0xFF10B981).copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = card.back,
                                color = Color(0xFF10B981),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "TAP TO FLIP",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeySettingsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    var inputKey by remember { mutableStateOf(customApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveCustomApiKey(inputKey)
                    Toast.makeText(context, "API Key saved successfully!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF0054D1),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "API Key Configuration",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Android's strict sandbox security prevents apps from programmatically reading credentials from other apps (like Google or Gemini) directly. Paste your own Google AI Studio key below to run with unlimited personal quotas!",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )

                val effectiveKey = viewModel.getEffectiveApiKey()
                val hasKey = effectiveKey.isNotBlank()
                val isCustom = customApiKey.isNotBlank()

                // Current Key Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasKey) Color(0xFFF0FDF4) else Color(0xFFEFF6FF)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (hasKey) Color(0xFFBBF7D0) else Color(0xFFBFDBFE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasKey) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (hasKey) Color(0xFF16A34A) else Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isCustom) "Using Custom Gemini 3.5 Flash" else if (hasKey) "Using Project Gemini API Key" else "Using Gemini Nano Fallback",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (hasKey) Color(0xFF15803D) else Color(0xFF1E40AF)
                            )
                            Text(
                                text = if (hasKey) "Your Gemini API key is active for online cloud intelligence and PDF parsing." else "Running entirely on-device, offline and secure with no key needed!",
                                fontSize = 11.sp,
                                color = if (hasKey) Color(0xFF16A34A) else Color(0xFF2563EB)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide API key" else "Show API key",
                                tint = Color(0xFF64748B)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0054D1),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )

                if (customApiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            viewModel.clearCustomApiKey()
                            inputKey = ""
                            Toast.makeText(context, "Custom key cleared. Reset to default.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Clear custom key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Help link to get a free key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have a key?",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Get free key at Google AI Studio",
                        fontSize = 11.sp,
                        color = Color(0xFF0054D1),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://aistudio.google.com/")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun LearningGoalSettingsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
    val nativeLanguage by viewModel.nativeLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    
    var nativeInput by remember { mutableStateOf(nativeLanguage) }
    var targetInput by remember { mutableStateOf(targetLanguage) }
    
    val commonLanguages = listOf("English", "Swedish", "Spanish", "French", "German", "Italian", "Japanese", "Persian")
    
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (nativeInput.isBlank() || targetInput.isBlank()) {
                        Toast.makeText(context, "Languages cannot be empty!", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.updateLearningGoal(nativeInput, targetInput)
                        Toast.makeText(context, "Learning goal updated to $targetInput!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "My Study Goal Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Setting your explicit learning goal shapes all AI card generation, localized local chatbot assistance, and suggestions in AdaptiveFlow.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
                
                Text("Native Language (Source)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                OutlinedTextField(
                    value = nativeInput,
                    onValueChange = { nativeInput = it },
                    placeholder = { Text("e.g. English") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0054D1),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonLanguages.size) { index ->
                        val lang = commonLanguages[index]
                        val isSelected = nativeInput.equals(lang, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                .border(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { nativeInput = lang }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = lang, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF1E40AF) else Color(0xFF475569))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text("Language to Learn (Target)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    placeholder = { Text("e.g. Swedish") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0054D1),
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonLanguages.size) { index ->
                        val lang = commonLanguages[index]
                        val isSelected = targetInput.equals(lang, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF1F5F9))
                                .border(1.dp, if (isSelected) Color(0xFF3B82F6) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { targetInput = lang }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = lang, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color(0xFF1E40AF) else Color(0xFF475569))
                        }
                    }
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}
