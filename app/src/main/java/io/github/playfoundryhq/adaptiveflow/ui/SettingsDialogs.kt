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
fun ApiKeySettingsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
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
                    Toast.makeText(context, "Saved. Using ${selectedProvider.displayName}.", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0054D1)),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save & use") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF64748B)) }
        },
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, null, tint = Color(0xFF0054D1), modifier = Modifier.size(28.dp))
                Text("AI provider & key", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "AdaptiveFlow uses your own API key — nothing is bundled or shared. Pick a provider and paste its key. Without a key, AI import and the tutor are off, but you can still import a plain \"word: meaning\" list offline.",
                    fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 16.sp
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
                                containerColor = if (sel) Color(0xFFEFF6FF) else Color.Transparent,
                                contentColor = if (sel) Color(0xFF0054D1) else Color(0xFF64748B)
                            ),
                            border = BorderStroke(1.dp, if (sel) Color(0xFF0054D1) else Color(0xFFE2E8F0))
                        ) { Text(p.displayName, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (hasActiveKey) Color(0xFFF0FDF4) else Color(0xFFFFF7ED)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (hasActiveKey) Color(0xFFBBF7D0) else Color(0xFFFED7AA))
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (hasActiveKey) Icons.Default.CheckCircle else Icons.Default.Info, null,
                            tint = if (hasActiveKey) Color(0xFF16A34A) else Color(0xFFEA580C), modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (hasActiveKey) "AI ready — using ${activeProvider.displayName}."
                            else "No key set. AI import & tutor are off; offline list import still works.",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (hasActiveKey) Color(0xFF15803D) else Color(0xFF9A3412)
                        )
                    }
                }

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text("${selectedProvider.displayName} API key") },
                    placeholder = { Text(keyHint) },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isKeyVisible) "Hide" else "Show", tint = Color(0xFF64748B)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0054D1), unfocusedBorderColor = Color(0xFFE2E8F0)
                    )
                )

                if (viewModel.apiKeyFor(selectedProvider).isNotBlank()) {
                    TextButton(
                        onClick = {
                            viewModel.saveApiKey(selectedProvider, "")
                            inputKey = ""
                            Toast.makeText(context, "${selectedProvider.displayName} key cleared.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Text("Clear key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Need a key?", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        "Get one from ${selectedProvider.displayName}",
                        fontSize = 11.sp, color = Color(0xFF0054D1), fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(keyUrl)))
                            }.onFailure { Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show() }
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
