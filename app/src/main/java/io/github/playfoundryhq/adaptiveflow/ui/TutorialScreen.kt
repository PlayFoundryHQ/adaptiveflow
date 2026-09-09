package io.github.playfoundryhq.adaptiveflow.ui

import io.github.playfoundryhq.adaptiveflow.BuildConfig
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
    val c = AppTheme.colors

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
                color = c.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Universal Semantic Learning",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = c.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        item {
            // Generated Visual Artwork Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(1.dp, c.hairline, RoundedCornerShape(20.dp)),
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
                        text = "ONE GOAL · EVERY DECK",
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
                    title = "Paste It, Study It",
                    desc = "Paste text or a word list, or load a PDF / TXT / CSV. With your own API key, the AI turns it into flashcard pairs. No key? A plain \"word: meaning\" list still imports offline."
                )

                TutorialPillarItem(
                    icon = Icons.Default.CenterFocusStrong,
                    title = "Set Your Goal Once",
                    desc = "Choose your native and target language on the Quest tab. Every new deck, quiz, and tutor reply is tuned to that goal."
                )

                TutorialPillarItem(
                    icon = Icons.Default.SupportAgent,
                    title = "AI Tutor",
                    desc = "Stuck on a card? Open the tutor chat for pronunciation tips, usage examples, and simpler explanations. Needs an API key."
                )
            }
        }

        item {
            Text(
                text = "AdaptiveFlow v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = c.textFaint,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            )
        }
    }
}


@Composable
fun TutorialPillarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.accentMuted),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                color = c.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// Data structures for styling our decks beautifully with custom themes based on languages
