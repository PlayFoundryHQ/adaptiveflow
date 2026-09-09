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

    var showTutorSheet by rememberSaveable { mutableStateOf(false) }
    var showContextDrawer by rememberSaveable { mutableStateOf(false) }
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

