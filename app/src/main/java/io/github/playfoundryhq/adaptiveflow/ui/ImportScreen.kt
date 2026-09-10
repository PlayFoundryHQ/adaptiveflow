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
import androidx.compose.ui.res.stringResource
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
import io.github.playfoundryhq.adaptiveflow.domain.ImportPipeline
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ImportTab(viewModel: StudyViewModel) {
    val c = AppTheme.colors
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDiagnosticsOpen by remember { mutableStateOf(false) }

    if (isSettingsOpen) {
        ApiKeySettingsDialog(viewModel = viewModel, onDismiss = { isSettingsOpen = false })
    }

    if (isDiagnosticsOpen) {
        DiagnosticLogsDialog(viewModel = viewModel, onDismiss = { isDiagnosticsOpen = false })
    }

    var rawText by rememberSaveable { mutableStateOf("") }
    var topicHint by rememberSaveable { mutableStateOf("") }
    var selectedImportMode by rememberSaveable { mutableStateOf("Text") } // "Text", "YouTube", "PDF"
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
    var isMergeEnabled by rememberSaveable { mutableStateOf(false) }
    var selectedMergeDeckId by rememberSaveable { mutableStateOf<Long?>(null) }
    var isDeckDropdownExpanded by remember { mutableStateOf(false) }
    var selectedDensity by rememberSaveable { mutableStateOf("Balanced") } // "Focused", "Balanced", "Exhaustive"

    (importState as? ImportPipeline.ImportState.MergePreview)?.let { preview ->
        val p = preview.plan
        AlertDialog(
            onDismissRequest = { viewModel.cancelMerge() },
            shape = RoundedCornerShape(24.dp),
            containerColor = c.surface,
            icon = { Icon(Icons.Default.Layers, contentDescription = null, tint = c.accent) },
            title = { Text(stringResource(R.string.import_merge_into, p.targetDeckName), fontWeight = FontWeight.Bold, color = c.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MergeStatRow(p.newCount.toString(), stringResource(R.string.import_merge_new), c.success, c)
                    MergeStatRow(p.enrichCount.toString(), stringResource(R.string.import_merge_enrich), c.accent, c)
                    MergeStatRow(p.skipCount.toString(), stringResource(R.string.import_merge_skip), c.textFaint, c)
                    if (p.newCount == 0 && p.enrichCount == 0) {
                        Text(
                            stringResource(R.string.import_merge_nothing),
                            color = c.textSecondary, fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmMerge() },
                    enabled = p.newCount > 0 || p.enrichCount > 0
                ) { Text(stringResource(R.string.import_merge_confirm), color = c.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelMerge() }) {
                    Text(stringResource(R.string.action_cancel), color = c.textSecondary)
                }
            }
        )
    }

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
            is ImportPipeline.ImportState.Success -> {
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

    // Local Document Picker Launcher — restricted to the formats import can read.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                var name = context.getString(R.string.import_selected_file)
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
                            name = it.getString(nameIndex) ?: context.getString(R.string.import_selected_file)
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
                                AppSnackbar.show(context.getString(R.string.import_attached_ok, name))
                            }
                        } else {
                            throw Exception("Cached file is empty or missing")
                        }
                    } catch (e: Exception) {
                        DiagnosticLogger.e("MainActivity", "Failed to cache selected file", e)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            AppSnackbar.show(context.getString(R.string.import_cache_failed, e.message ?: ""))
                        }
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.e("MainActivity", "Error querying file info", e)
                AppSnackbar.show(context.getString(R.string.import_query_failed, e.message ?: ""))
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
                        text = stringResource(R.string.import_title),
                        color = c.textPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.import_subtitle),
                        color = c.textSecondary,
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
                            .background(c.dangerMuted)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.import_diagnostics_desc),
                            tint = c.danger,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { isSettingsOpen = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c.surfaceMuted)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = stringResource(R.string.decks_api_key_desc),
                            tint = c.textSecondary,
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
                    .background(c.surfaceMuted)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modes = listOf(
                    "Text" to stringResource(R.string.import_mode_text),
                    "YouTube" to stringResource(R.string.import_mode_youtube),
                    "PDF" to stringResource(R.string.import_mode_pdf)
                )
                modes.forEach { (modeKey, label) ->
                    val isSelected = selectedImportMode == modeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) c.surface else Color.Transparent)
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
                            color = if (isSelected) c.accent else c.textSecondary,
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
                colors = CardDefaults.cardColors(containerColor = c.accentMuted.copy(alpha = 0.8f))
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
                            .background(c.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (selectedImportMode) {
                                "YouTube" -> Icons.Default.SmartDisplay
                                "PDF" -> Icons.AutoMirrored.Filled.InsertDriveFile
                                else -> Icons.AutoMirrored.Filled.Subject
                            },
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (selectedImportMode) {
                                "YouTube" -> stringResource(R.string.import_guide_title_youtube)
                                "PDF" -> stringResource(R.string.import_guide_title_pdf)
                                else -> stringResource(R.string.import_guide_title_text)
                            },
                            color = c.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (selectedImportMode) {
                                "YouTube" -> stringResource(R.string.import_guide_desc_youtube)
                                "PDF" -> stringResource(R.string.import_guide_desc_pdf)
                                else -> stringResource(R.string.import_guide_desc_text)
                            },
                            color = c.textSecondary,
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
                    containerColor = if (isExternalHelperExpanded) c.surfaceMuted else c.surfaceMuted
                ),
                border = BorderStroke(
                    1.dp, 
                    if (isExternalHelperExpanded) c.hairline else c.hairline
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
                                .background(if (isExternalHelperExpanded) c.surfaceMuted else c.surfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExternalHelperExpanded) Icons.Default.AutoAwesome else Icons.AutoMirrored.Filled.Help,
                                contentDescription = null,
                                tint = if (isExternalHelperExpanded) Color(0xFF9333EA) else c.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.import_helper_title),
                                color = if (isExternalHelperExpanded) Color(0xFF7E22CE) else c.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isExternalHelperExpanded) stringResource(R.string.import_helper_sub_expanded) else stringResource(R.string.import_helper_sub_collapsed),
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = if (isExternalHelperExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(R.string.import_expand_collapse_desc),
                            tint = c.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (isExternalHelperExpanded) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = c.hairline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.import_helper_why),
                            color = c.textPrimary,
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
                                    stringResource(R.string.import_helper_bullet1),
                                    color = c.textSecondary,
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
                                    stringResource(R.string.import_helper_bullet2),
                                    color = c.textSecondary,
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
                                    stringResource(R.string.import_helper_bullet3),
                                    color = c.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.import_helper_prompt_label),
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
                                .background(c.surfaceMuted)
                                .border(1.dp, c.hairline, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = promptTemplate,
                                    color = c.textPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(promptTemplate))
                                        AppSnackbar.show(context.getString(R.string.import_prompt_copied))
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
                                            contentDescription = stringResource(R.string.import_copy_prompt_desc),
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(stringResource(R.string.import_copy_agent_prompt), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.import_how_to_title),
                            color = c.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.import_how_to_steps),
                            color = c.textSecondary,
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
                    colors = CardDefaults.cardColors(containerColor = c.surfaceMuted),
                    border = BorderStroke(1.dp, c.hairline)
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
                                    if (attachedFileMimeType.contains("pdf")) c.dangerMuted else c.successMuted
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = if (attachedFileMimeType.contains("pdf")) c.danger else c.success,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachedFileName,
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.import_ready_to_parse, formatFileSize(attachedFileSize)),
                                color = c.textSecondary,
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
                                contentDescription = stringResource(R.string.import_remove_file_desc),
                                tint = c.textFaint
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
                            stringResource(R.string.import_ph_file)
                        } else {
                            when (selectedImportMode) {
                                "YouTube" -> stringResource(R.string.import_ph_youtube)
                                "PDF" -> stringResource(R.string.import_ph_pdf)
                                else -> stringResource(R.string.import_ph_text)
                            }
                        },
                        color = c.textFaint,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                textStyle = LocalTextStyle.current.copy(color = c.textPrimary, fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent,
                    unfocusedBorderColor = c.hairline,
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface
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
                        text = stringResource(R.string.import_hint_ph),
                        color = c.textFaint,
                        fontSize = 14.sp
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.import_hint_label),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/pdf",
                                "text/plain",
                                "text/csv",
                                "text/comma-separated-values",
                                "application/json",
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.accent),
                    border = BorderStroke(1.dp, c.hairline)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = stringResource(R.string.import_upload_desc),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.import_load_file),
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
                        text = stringResource(R.string.import_yt_test_label),
                        color = c.textSecondary,
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
                            enabled = importState !is ImportPipeline.ImportState.Loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.hairline),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.import_yt_sample_fr), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                rawText = "https://www.youtube.com/watch?v=pPy7643bZGo"
                                topicHint = "Japanese (Tokyo travel phrases)"
                            },
                            enabled = importState !is ImportPipeline.ImportState.Loading,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.hairline),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(stringResource(R.string.import_yt_sample_jp), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    containerColor = if (isMergeEnabled) c.successMuted else c.surfaceMuted
                ),
                border = BorderStroke(
                    width = 1.2.dp,
                    color = if (isMergeEnabled) c.successMuted else c.hairline
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
                                    .background(if (isMergeEnabled) c.successMuted else c.surfaceMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = if (isMergeEnabled) c.success else c.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.import_merge_title),
                                    color = if (isMergeEnabled) c.success else c.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.import_merge_sub),
                                    color = c.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = isMergeEnabled,
                            onCheckedChange = { isMergeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.success
                            )
                        )
                    }

                    if (isMergeEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = c.successMuted.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.import_merge_select),
                            color = c.success,
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
                                .background(c.surface)
                                .border(1.dp, c.successMuted, RoundedCornerShape(12.dp))
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
                                        tint = c.success,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = targetDeck?.name ?: stringResource(R.string.import_merge_select_ph),
                                        color = c.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.import_dropdown_desc),
                                    tint = c.success
                                )
                            }

                            DropdownMenu(
                                expanded = isDeckDropdownExpanded,
                                onDismissRequest = { isDeckDropdownExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(c.surface)
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
                                                    tint = if (deckWithCards.deck.name.contains("Master Vocabulary Pool")) Color(0xFF8B5CF6) else c.success,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Column {
                                                    Text(
                                                        text = deckWithCards.deck.name,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                    Text(
                                                        text = stringResource(R.string.import_card_count, deckWithCards.flashcards.size),
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
                            colors = CardDefaults.cardColors(containerColor = c.successMuted.copy(alpha = 0.5f)),
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
                                    tint = c.success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.import_merge_info),
                                    color = c.success,
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
                colors = CardDefaults.cardColors(containerColor = c.surfaceMuted),
                border = BorderStroke(1.dp, c.hairline)
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
                                .background(c.accentMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DensityMedium,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.import_density_title),
                                color = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.import_density_sub),
                                color = c.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surfaceMuted)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val densityOptions = listOf(
                            "Focused" to stringResource(R.string.import_density_focused),
                            "Balanced" to stringResource(R.string.import_density_balanced),
                            "Exhaustive" to stringResource(R.string.import_density_exhaustive)
                        )
                        densityOptions.forEach { (densityKey, label) ->
                            val isSelected = selectedDensity == densityKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) c.surface else Color.Transparent)
                                    .clickable { selectedDensity = densityKey }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) c.accent else c.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when (densityKey) {
                                            "Focused" -> stringResource(R.string.import_density_focused_hint)
                                            "Balanced" -> stringResource(R.string.import_density_balanced_hint)
                                            "Exhaustive" -> stringResource(R.string.import_density_exhaustive_hint)
                                            else -> ""
                                        },
                                        color = if (isSelected) c.accent.copy(alpha = 0.7f) else c.textFaint,
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
                enabled = (rawText.isNotBlank() || attachedFileUri != null) && importState !is ImportPipeline.ImportState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("import_parse_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = c.accent,
                    disabledContainerColor = c.textFaint
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (importState is ImportPipeline.ImportState.Loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = c.onAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = (importState as ImportPipeline.ImportState.Loading).message,
                            color = c.onAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = c.onAccent)
                        Text(
                            text = if (selectedImportMode == "YouTube") stringResource(R.string.import_btn_generate) else stringResource(R.string.import_btn_parse),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp,
                            color = c.onAccent
                        )
                    }
                }
            }
        }

        val currentImportState = importState
        if (currentImportState is ImportPipeline.ImportState.Error) {
            item {
                // Persistent, dismissible error banner. Replaces a transient Toast (LENGTH_LONG) which
                // was easy to miss since it auto-dismisses in ~3.5s with no lasting visual change.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.dangerMuted)
                        .border(1.dp, c.dangerMuted, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = c.danger,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = currentImportState.message,
                        color = c.danger,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.resetImportState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.import_dismiss_desc),
                            tint = c.danger,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (currentImportState is ImportPipeline.ImportState.Success) {
            item {
                // Persistent, dismissible success banner - mirrors the error banner above. Replaces a
                // plain Toast.LENGTH_SHORT that fired at the exact moment the form silently cleared,
                // which was easy to miss.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.successMuted)
                        .border(1.dp, c.successMuted, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = c.success,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.import_deck_saved, currentImportState.deckName),
                            color = c.success,
                            fontSize = 13.sp
                        )
                        if (currentImportState.undoable) {
                            Text(
                                text = stringResource(R.string.import_undo_merge),
                                color = c.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { viewModel.undoLastImport() }
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.resetImportState() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.import_dismiss_desc),
                            tint = c.success,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

    }
}



@Composable
private fun MergeStatRow(
    value: String,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    c: io.github.playfoundryhq.adaptiveflow.ui.theme.AppColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, color = valueColor, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(label, color = c.textSecondary, fontSize = 12.sp)
    }
}
