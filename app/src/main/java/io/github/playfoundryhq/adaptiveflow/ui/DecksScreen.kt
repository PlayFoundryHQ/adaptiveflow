package io.github.playfoundryhq.adaptiveflow.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
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
    deckWithCards: io.github.playfoundryhq.adaptiveflow.data.model.DeckWithCards,
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

