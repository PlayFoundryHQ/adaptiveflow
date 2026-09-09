package io.github.playfoundryhq.adaptiveflow.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import io.github.playfoundryhq.adaptiveflow.R
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProviderId
import io.github.playfoundryhq.adaptiveflow.data.model.ChatLog
import io.github.playfoundryhq.adaptiveflow.data.model.Deck
import io.github.playfoundryhq.adaptiveflow.data.model.Flashcard
import io.github.playfoundryhq.adaptiveflow.ui.components.DiagnosticLogsDialog
import io.github.playfoundryhq.adaptiveflow.ui.theme.AppTheme
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun PathTab(
    viewModel: StudyViewModel,
    onNavigateToImport: () -> Unit
) {
    val decks by viewModel.allDecks.collectAsStateWithLifecycle()
    val nativeLanguage by viewModel.nativeLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    var selectedNodeDeck by remember { mutableStateOf<io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards?>(null) }
    val c = AppTheme.colors

    // Compute Crowns
    val crownsCount = remember(decks) {
        decks.count { dwc ->
            dwc.flashcards.isNotEmpty() && dwc.flashcards.all { it.repetitions > 0 }
        }
    }

    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val totalXp = progress.xp
    val streakDays = progress.streak

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
                colors = CardDefaults.cardColors(containerColor = c.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, c.hairline, RoundedCornerShape(18.dp))
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
                                color = c.textPrimary
                            )
                        }
                        Text("Streak", fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Bold)
                    }

                    // Vertical Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(c.hairline))

                    // XP
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$totalXp XP",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = c.textPrimary
                            )
                        }
                        Text("Total Points", fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Bold)
                    }

                    // Vertical Divider
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(c.hairline))

                    // Crowns
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👑", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (crownsCount > 0) "$crownsCount" else "0",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = c.textPrimary
                            )
                        }
                        Text("Crowns Earned", fontSize = 11.sp, color = c.textSecondary, fontWeight = FontWeight.Bold)
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
                        colors = CardDefaults.cardColors(containerColor = c.heroSurface)
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
                                        .background(c.warning)
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
                                color = c.heroText,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Manage your global languages directly below. Your adaptive flashcards and tutors will instantly tune to this goal.",
                                color = c.heroTextMuted,
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
                        colors = CardDefaults.cardColors(containerColor = c.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Goal Configuration",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )

                            // Native Language Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Your Native Language:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c.textSecondary
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
                                                .background(if (isSelected) c.accentMuted else c.surfaceMuted)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) c.accent else c.hairline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.updateLearningGoal(lang, targetLanguage) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) c.accent else c.textSecondary
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
                                    color = c.textSecondary
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
                                                .background(if (isSelected) c.warningMuted else c.surfaceMuted)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) c.warning else c.hairline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { viewModel.updateLearningGoal(nativeLanguage, lang) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) c.warning else c.textSecondary
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
                        colors = CardDefaults.cardColors(containerColor = c.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "$targetLanguage Goal Progress",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
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
                                            .background(c.warningMuted),
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
                                            color = c.textPrimary
                                        )
                                        Text(
                                            text = "${matchingDecks.size} active target decks",
                                            fontSize = 11.sp,
                                            color = c.textSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = "$masteryPercent%",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = c.warning
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = c.warning,
                                trackColor = c.warningMuted
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
                        colors = CardDefaults.cardColors(containerColor = c.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Weekly Quests & Challenges",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )

                            // Quest 1
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (streakDays > 0) "✅" else "⏳", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Maintain active daily study streak (🔥 $streakDays Days)",
                                    fontSize = 12.sp,
                                    color = c.textSecondary
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
                                        color = c.textSecondary
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
                                        color = c.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { progressChallengeCrowns },
                                        modifier = Modifier
                                            .width(150.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = c.warning,
                                        trackColor = c.warningMuted
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
                        color = c.textPrimary,
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
                            colors = CardDefaults.cardColors(containerColor = c.surfaceMuted)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
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
                                    color = c.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "We couldn't find any study materials configured for $targetLanguage in your library yet.",
                                    fontSize = 12.sp,
                                    color = c.textSecondary,
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
                                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("AI Quest Generator: Seed Starter Deck", color = c.onAccent, fontWeight = FontWeight.Bold)
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
                            colors = CardDefaults.cardColors(containerColor = c.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, c.hairline, RoundedCornerShape(16.dp))
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
                                            color = c.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$learned of $total words learned",
                                            fontSize = 11.sp,
                                            color = c.textSecondary
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
                                        trackColor = c.surfaceMuted
                                    )

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Details",
                                        tint = c.textFaint,
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
                containerColor = c.surface,
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
                            color = c.textPrimary,
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
                        HorizontalDivider(color = c.surfaceMuted, thickness = 1.dp)

                        // Clean visual tab selector mimicking modern web designs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.surfaceMuted, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (activeViewTab == "stats") c.surface else Color.Transparent)
                                    .clickable { activeViewTab = "stats" }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Overview Stats",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeViewTab == "stats") c.textPrimary else c.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (activeViewTab == "grid") c.surface else Color.Transparent)
                                    .clickable { activeViewTab = "grid" }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Vocabulary Grid",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeViewTab == "grid") c.textPrimary else c.textSecondary
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
                                    Text(text = "$total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                                    Text(text = "Total Words", fontSize = 11.sp, color = c.textSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "$learned", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.success)
                                    Text(text = "Learned", fontSize = 11.sp, color = c.textSecondary)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(text = "$reviewsDue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (reviewsDue > 0) c.warning else c.textFaint)
                                    Text(text = "Due Now", fontSize = 11.sp, color = c.textSecondary)
                                }
                            }

                            // Leitner Memory Stages visualization
                            Text(
                                text = "Leitner Memory Boxes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LeitnerBoxProgressRow(boxName = "Box 1: New / Unstudied", count = box1Count, total = total, color = c.textFaint)
                                LeitnerBoxProgressRow(boxName = "Box 2: Fresh Review", count = box2Count, total = total, color = Color(0xFF60A5FA))
                                LeitnerBoxProgressRow(boxName = "Box 3: Familiar", count = box3Count, total = total, color = Color(0xFF818CF8))
                                LeitnerBoxProgressRow(boxName = "Box 4: Highly Retained", count = box4Count, total = total, color = c.warning)
                                LeitnerBoxProgressRow(boxName = "Box 5: Mastered (Permanent)", count = box5Count, total = total, color = c.success)
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
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_quest_study_button")
                    ) {
                        Text(
                            text = if (reviewsDue > 0) "Study & Review ($reviewsDue Due)" else "Study Deck / Start Quiz",
                            color = c.onAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { selectedNodeDeck = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close Map details", color = c.textSecondary, fontWeight = FontWeight.Bold)
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
    val c = AppTheme.colors
    val progress = if (total > 0) count.toFloat() / total else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(boxName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textSecondary)
            Text("$count words", fontSize = 10.sp, fontWeight = FontWeight.Black, color = c.textPrimary)
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = color,
            trackColor = c.surfaceMuted
        )
    }
}


@Composable
fun ResponsiveVocabularyGrid(
    cards: List<Flashcard>,
    modifier: Modifier = Modifier
) {
    val c = AppTheme.colors
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
                    containerColor = if (isFlipped) c.surfaceMuted else c.accentMuted
                ),
                border = BorderStroke(1.dp, c.hairline),
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
                                    .background(c.accentMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getIconForCard(card),
                                    contentDescription = "Concept Icon",
                                    tint = c.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = card.front,
                                color = c.textPrimary,
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
                                tint = c.success.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = card.back,
                                color = c.success,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "TAP TO FLIP",
                                color = c.textFaint,
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

