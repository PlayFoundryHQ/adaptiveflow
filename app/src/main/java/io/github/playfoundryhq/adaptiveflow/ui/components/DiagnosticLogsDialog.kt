package io.github.playfoundryhq.adaptiveflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.DiagnosticLogger
import io.github.playfoundryhq.adaptiveflow.ui.viewmodel.StudyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogsDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = In-App Logs, 1 = ADB System Logcat
    var searchQuery by remember { mutableStateOf("") }
    
    val inAppLogs by DiagnosticLogger.inAppLogs.collectAsStateWithLifecycle()
    var systemLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isRefreshingSystemLogs by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Load System Logcat Logs
    val refreshSystemLogs: () -> Unit = {
        isRefreshingSystemLogs = true
        coroutineScope.launch(Dispatchers.IO) {
            val logs = DiagnosticLogger.getLogcatLogs()
            withContext(Dispatchers.Main) {
                systemLogs = logs
                isRefreshingSystemLogs = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            refreshSystemLogs()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Title Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFEF2F2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Diagnostics",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Diagnostic Console",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Trace chunking & background process errors",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selectors
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("🎯 In-App Logs", "🔌 ADB System Logcat")
                    tabs.forEachIndexed { idx, title ->
                        val isSelected = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White else Color.Transparent)
                                .clickable { selectedTab = idx }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF1E293B) else Color(0xFF64748B)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar (Filter, Clear, Copy, Refresh)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        placeholder = { Text("Filter logs...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFCBD5E1),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    // Clear button
                    IconButton(
                        onClick = {
                            if (selectedTab == 0) {
                                DiagnosticLogger.clear()
                            } else {
                                systemLogs = emptyList()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Copy All Button
                    IconButton(
                        onClick = {
                            val fullLogs = if (selectedTab == 0) {
                                inAppLogs.joinToString("\n") { it.format() }
                            } else {
                                systemLogs.joinToString("\n")
                            }
                            clipboardManager.setText(AnnotatedString(fullLogs))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy logs",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Refresh Button (for Logcat tab)
                    if (selectedTab == 1) {
                        IconButton(
                            onClick = refreshSystemLogs,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEFF6FF))
                        ) {
                            if (isRefreshingSystemLogs) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF3B82F6)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Console area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A)) // terminal style dark background
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    if (selectedTab == 0) {
                        val filteredInApp = inAppLogs.filter {
                            searchQuery.isBlank() ||
                            it.message.contains(searchQuery, ignoreCase = true) ||
                            it.tag.contains(searchQuery, ignoreCase = true) ||
                            it.level.contains(searchQuery, ignoreCase = true) ||
                            (it.stackTrace != null && it.stackTrace.contains(searchQuery, ignoreCase = true))
                        }

                        if (filteredInApp.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No matching logs captured.", color = Color(0xFF64748B), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            DiagnosticLogger.i("Diagnostics", "Interactive testing log entry!")
                                            DiagnosticLogger.e("Diagnostics", "Simulated Chunk Parsing Exception", RuntimeException("HTTP 429: Too many requests for chunk 2 of 4 (pages 5-8)"))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Simulate Test Log Entry", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredInApp) { entry ->
                                    InAppLogItemRow(entry = entry)
                                }
                            }
                        }
                    } else {
                        val filteredSystem = systemLogs.filter {
                            searchQuery.isBlank() || it.contains(searchQuery, ignoreCase = true)
                        }

                        if (isRefreshingSystemLogs) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Fetching ADB system logcat...", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        } else if (filteredSystem.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No matching system logcat logs found.", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(filteredSystem) { logLine ->
                                    Text(
                                        text = logLine,
                                        color = getLogcatColor(logLine),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device USB/ADB status: Connected (Port fallback enabled)",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "V1.2 Debug Console",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun InAppLogItemRow(entry: DiagnosticLogger.LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    
    val color = when (entry.level.uppercase()) {
        "E" -> Color(0xFFF87171) // light red
        "W" -> Color(0xFFFBBF24) // gold yellow
        "I" -> Color(0xFF60A5FA) // light blue
        "D" -> Color(0xFF34D399) // green
        else -> Color(0xFF94A3B8) // slate gray
    }

    val hasStackTrace = !entry.stackTrace.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.5f))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable(enabled = hasStackTrace) { expanded = !expanded }
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "[${entry.level}]",
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.timestamp,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.tag,
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.message,
                color = Color(0xFFF1F5F9),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            
            if (hasStackTrace) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand stacktrace",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (expanded && hasStackTrace) {
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.stackTrace.orEmpty(),
                color = Color(0xFFFDA4AF), // rose pink for readability
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun getLogcatColor(line: String): Color {
    return when {
        line.contains(" E ") || line.contains("/E ") || line.contains(" E/") -> Color(0xFFF87171)
        line.contains(" W ") || line.contains("/W ") || line.contains(" W/") -> Color(0xFFFBBF24)
        line.contains(" I ") || line.contains("/I ") || line.contains(" I/") -> Color(0xFF60A5FA)
        line.contains(" D ") || line.contains("/D ") || line.contains(" D/") -> Color(0xFF34D399)
        else -> Color(0xFF94A3B8)
    }
}
