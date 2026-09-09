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
    val c = AppTheme.colors
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
                .background(c.surfaceMuted)
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = "API Key Configuration",
                tint = c.textSecondary,
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
                        .background(c.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = "Your Library is Empty",
                    color = c.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Your library is currently empty. Import some new study material from YouTube, PDF, or raw text to continue learning!",
                    color = c.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onNavigateToImport,
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Import / Create Deck", color = c.onAccent, fontWeight = FontWeight.Bold)
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
                            color = c.textPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Universal Study Dashboard",
                            color = c.textSecondary,
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
                                .background(c.warningMuted)
                                .clickable { isGoalSettingsOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Active Study Goal Settings",
                                tint = c.warning,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c.surfaceMuted)
                                .clickable { isSettingsOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "API Key Configuration",
                                tint = c.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(c.accentMuted)
                                .clickable { onNavigateToImport() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create New Deck",
                                tint = c.accent,
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
                            .background(c.heroSurface)
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
                                    color = c.heroTextMuted.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Learn $targetLanguage from $nativeLanguage",
                                color = c.heroText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "New decks, quizzes, and tutor replies are tuned to $targetLanguage. Tap any deck below to study!",
                                color = c.heroTextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = c.heroText.copy(alpha = 0.15f),
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
            containerColor = c.accent,
            contentColor = c.onAccent,
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
    deckWithCards: io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards,
    onClick: () -> Unit
) {
    val deck = deckWithCards.deck
    val cards = deckWithCards.flashcards
    val style = getStyleForLanguage(deck.sourceLanguage, deck.name)
    val c = AppTheme.colors

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
            containerColor = c.surface
        ),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.hairline, RoundedCornerShape(22.dp))
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
                        color = c.textPrimary,
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
                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )

                        // Target Language Label
                        if (!deck.targetLanguage.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.surfaceMuted)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = deck.targetLanguage,
                                    color = c.textSecondary,
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
                        .background(c.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            // Divider
            HorizontalDivider(
                color = c.hairline,
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
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$learnedCards / $totalCards Words (${(learnedProgress * 100).toInt()}%)",
                        color = c.textPrimary,
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
                    color = if (learnedProgress >= 0.8f) c.success else style.textColor,
                    trackColor = c.surfaceMuted
                )
            }

            // High-Value Memory stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total cards stat
                StatChip(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = "$totalCards Words",
                    contentColor = c.textSecondary,
                    backgroundColor = c.surfaceMuted,
                    modifier = Modifier.weight(1f)
                )

                // Mastered / Learned stat
                StatChip(
                    icon = Icons.Default.CheckCircle,
                    label = "$learnedCards Learned",
                    contentColor = c.success,
                    backgroundColor = c.successMuted,
                    modifier = Modifier.weight(1f)
                )

                // Due cards stat
                StatChip(
                    icon = Icons.Default.Schedule,
                    label = if (reviewsDue > 0) "$reviewsDue Due" else "0 Due",
                    contentColor = if (reviewsDue > 0) c.warning else c.textFaint,
                    backgroundColor = if (reviewsDue > 0) c.warningMuted else c.surfaceMuted,
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

