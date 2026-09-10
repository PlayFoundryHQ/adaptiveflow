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
import androidx.compose.ui.res.stringResource
import io.github.playfoundryhq.adaptiveflow.data.ai.AiProviderId
import io.github.playfoundryhq.adaptiveflow.domain.Languages
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
fun ApiKeySettingsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    val activeProvider by viewModel.aiProviderId.collectAsStateWithLifecycle()
    var selectedProvider by remember { mutableStateOf(activeProvider) }
    var inputKey by remember(selectedProvider) { mutableStateOf(viewModel.apiKeyFor(selectedProvider)) }
    var isKeyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val keyUrl = when (selectedProvider) {
        AiProviderId.GEMINI -> "https://aistudio.google.com/apikey"
        AiProviderId.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
    }
    val keyHint = when (selectedProvider) {
        AiProviderId.GEMINI -> "AIza…"
        AiProviderId.DEEPSEEK -> "sk-…"
    }
    val hasActiveKey = viewModel.getEffectiveApiKey().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveApiKey(selectedProvider, inputKey)
                    viewModel.setAiProvider(selectedProvider)
                    AppSnackbar.show(context.getString(R.string.key_dialog_saved, selectedProvider.displayName))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                shape = RoundedCornerShape(10.dp)
            ) { Text(stringResource(R.string.key_dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
        },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, null, tint = c.accent, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.key_dialog_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.key_dialog_blurb),
                    fontSize = 12.sp, color = c.textSecondary, lineHeight = 16.sp
                )
                Text(
                    stringResource(R.string.key_dialog_privacy),
                    fontSize = 11.sp, color = c.textFaint, lineHeight = 15.sp
                )

                // Provider toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AiProviderId.entries.forEach { p ->
                        val sel = selectedProvider == p
                        OutlinedButton(
                            onClick = { selectedProvider = p },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sel) c.accentMuted else Color.Transparent,
                                contentColor = if (sel) c.accent else c.textSecondary
                            ),
                            border = BorderStroke(1.dp, if (sel) c.accent else c.hairline)
                        ) { Text(p.displayName, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (hasActiveKey) c.successMuted else c.warningMuted),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (hasActiveKey) c.successMuted else c.warningMuted)
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasActiveKey) Icons.Default.CheckCircle else Icons.Default.Info, null,
                            tint = if (hasActiveKey) c.success else c.warning, modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (hasActiveKey) stringResource(R.string.key_dialog_ready, activeProvider.displayName)
                            else stringResource(R.string.key_dialog_no_key),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (hasActiveKey) c.success else c.warning
                        )
                    }
                }

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text(stringResource(R.string.key_field_label, selectedProvider.displayName)) },
                    placeholder = { Text(keyHint) },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(if (isKeyVisible) R.string.action_hide else R.string.action_show), tint = c.textSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.hairline
                    )
                )

                if (viewModel.apiKeyFor(selectedProvider).isNotBlank()) {
                    TextButton(
                        onClick = {
                            viewModel.saveApiKey(selectedProvider, "")
                            inputKey = ""
                            AppSnackbar.show(context.getString(R.string.key_dialog_cleared, selectedProvider.displayName))
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = c.danger),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.key_dialog_clear), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.key_dialog_need_key), fontSize = 11.sp, color = c.textSecondary)
                    Text(
                        stringResource(R.string.key_dialog_get_from, selectedProvider.displayName),
                        fontSize = 11.sp, color = c.accent, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(keyUrl)))
                            }.onFailure { AppSnackbar.show(context.getString(R.string.open_browser_failed)) }
                        }
                    )
                }
            }
        },
        containerColor = c.surface,
        shape = RoundedCornerShape(20.dp)
    )
}


@Composable
fun LearningGoalSettingsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
    val c = AppTheme.colors
    val nativeLanguage by viewModel.nativeLanguage.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    
    var nativeInput by remember { mutableStateOf(nativeLanguage) }
    var targetInput by remember { mutableStateOf(targetLanguage) }
    // A target language outside the curated list is entered free-text via "Other…".
    var targetIsOther by remember { mutableStateOf(targetLanguage.isNotBlank() && Languages.find(targetLanguage) == null) }

    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (nativeInput.isBlank() || targetInput.isBlank()) {
                        AppSnackbar.show(context.getString(R.string.goal_empty))
                    } else {
                        viewModel.updateLearningGoal(nativeInput, targetInput)
                        AppSnackbar.show(context.getString(R.string.goal_updated, targetInput))
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.goal_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = c.textSecondary)
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
                    tint = c.warning,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = stringResource(R.string.goal_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.goal_blurb),
                    fontSize = 12.sp,
                    color = c.textSecondary,
                    lineHeight = 16.sp
                )

                Text(stringResource(R.string.goal_native_label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                LanguageDropdown(
                    label = if (nativeInput.isBlank()) stringResource(R.string.goal_pick_native) else Languages.label(nativeInput),
                    onPick = { nativeInput = it },
                    c = c,
                )

                Text(stringResource(R.string.goal_target_label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                LanguageDropdown(
                    label = when {
                        targetIsOther -> stringResource(R.string.goal_other)
                        targetInput.isBlank() -> stringResource(R.string.goal_pick_target)
                        else -> Languages.label(targetInput)
                    },
                    onPick = { picked ->
                        if (picked == OTHER_LANGUAGE) {
                            targetIsOther = true
                            targetInput = ""
                        } else {
                            targetIsOther = false
                            targetInput = picked
                        }
                    },
                    c = c,
                    includeOther = true,
                )
                if (targetIsOther) {
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        placeholder = { Text(stringResource(R.string.goal_target_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.accent,
                            unfocusedBorderColor = c.textFaint
                        )
                    )
                }
            }
        },
        containerColor = c.surface,
        shape = RoundedCornerShape(20.dp)
    )
}

/** Sentinel [LanguageDropdown] emits when the learner picks "Other…" for the target. */
const val OTHER_LANGUAGE = " other"

@Composable
private fun LanguageDropdown(
    label: String,
    onPick: (String) -> Unit,
    c: io.github.playfoundryhq.adaptiveflow.ui.theme.AppColors,
    includeOther: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.textFaint, RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = c.textSecondary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 320.dp)
                .background(c.surface)
        ) {
            Languages.all.forEach { lang ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (lang.english == lang.endonym) lang.english else "${lang.english}  ·  ${lang.endonym}",
                            fontSize = 13.sp,
                            color = c.textPrimary
                        )
                    },
                    onClick = { onPick(lang.english); expanded = false }
                )
            }
            if (includeOther) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.goal_other), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent) },
                    onClick = { onPick(OTHER_LANGUAGE); expanded = false }
                )
            }
        }
    }
}
