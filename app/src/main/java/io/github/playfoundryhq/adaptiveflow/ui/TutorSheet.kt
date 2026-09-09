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
import io.github.playfoundryhq.adaptiveflow.ui.theme.AppTheme
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AiTutorBottomSheet(
    viewModel: StudyViewModel,
    onClose: () -> Unit
) {
    val c = AppTheme.colors
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
            containerColor = c.surface,
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = c.danger
                )
            },
            title = {
                Text(
                    text = "Clear tutor chat history?",
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
            },
            text = {
                Text(
                    text = "This permanently deletes this conversation with the AI Tutor. This cannot be undone.",
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearChatConfirmation = false
                    viewModel.clearChatHistory()
                }) {
                    Text("Clear", color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmation = false }) {
                    Text("Cancel", color = c.textSecondary)
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
                        .background(c.accentMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "AI Adaptive Tutor",
                        color = c.textPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time semantic guidance",
                        color = c.textSecondary,
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
                        tint = c.textSecondary
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = c.textPrimary
                    )
                }
            }
        }

        HorizontalDivider(color = c.hairline, thickness = 1.dp)

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
                                .background(c.accentMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "How can I support your study today?",
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Ask for pronunciation, custom example sentences, or cultural origin hints in any language!",
                            color = c.textSecondary,
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
                            .background(c.accentMuted)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = c.accent,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Tutor is thinking...",
                            color = c.accent,
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
                        color = c.textFaint,
                        fontSize = 14.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.hairline,
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface
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
                        if (textInput.isBlank() || isAiLoading) c.hairline
                        else c.accent
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
                    tint = if (textInput.isBlank() || isAiLoading) c.textFaint else c.onAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


@Composable
fun ChatBubbleItem(log: ChatLog) {
    val c = AppTheme.colors
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
                    if (isUser) c.accent else c.surfaceMuted
                )
                .border(
                    1.dp,
                    if (isUser) c.accent else c.hairline,
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
                color = if (isUser) c.onAccent else c.textPrimary,
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
    val c = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = c.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

