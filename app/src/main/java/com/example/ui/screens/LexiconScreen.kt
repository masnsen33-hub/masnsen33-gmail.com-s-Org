package com.example.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DictionaryEntryEntity
import com.example.ui.MainViewModel
import com.example.ui.TranslationActionState
import com.example.ui.UiState
import com.example.ui.theme.ArabicNotoFamily
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPillChip
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.LocalGlassTokens
import java.util.Locale

private val DICTIONARY_CATEGORIES = listOf(
    "All",
    "Governance & Law",
    "Conflict & Liberation",
    "Faith & Heritage",
    "Economy & Land",
    "Eras & Civilizations",
    "Society & Culture"
)

private val DIALECT_FILTERS = listOf("All", "Kb.", "Tq.", "Mz.", "Gd.", "Tg.", "P.B.")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LexiconScreen(
    viewModel: MainViewModel,
    onNavigateToScanPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries by viewModel.filteredDictionaryEntries.collectAsStateWithLifecycle()
    val totalEntries by viewModel.allDictionaryEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedDialect by viewModel.selectedDialect.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val translationState by viewModel.translationState.collectAsStateWithLifecycle()

    val bookmarkedIds = remember(bookmarksState) {
        when (val s = bookmarksState) {
            is UiState.Success -> s.data.map { it.entryId.ifBlank { it.id } }.toSet()
            else -> emptySet()
        }
    }

    var activeEntryId by remember { mutableStateOf<String?>(null) }
    val activeEntry = remember(activeEntryId, totalEntries) {
        totalEntries.firstOrNull { it.id == activeEntryId }
    }

    val context = LocalContext.current
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.FRENCH
                ttsReady = true
            }
        }
        instance
    }
    DisposableEffect(tts) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val tokens = LocalGlassTokens.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Search & Filter Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                contentPadding = 14.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search dictionary"
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(
                                    onClick = { viewModel.updateSearchQuery("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lexicon_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(DICTIONARY_CATEGORIES) { cat ->
                            GlassPillChip(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { viewModel.selectCategory(cat) },
                                modifier = Modifier.testTag("category_chip_${cat.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = "Offline ready",
                                tint = tokens.arabicAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${entries.size} / ${totalEntries.size} terms offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.widthIn(max = 260.dp)
                        ) {
                            items(DIALECT_FILTERS) { dia ->
                                GlassPillChip(
                                    label = dia,
                                    selected = selectedDialect == dia,
                                    onClick = { viewModel.selectDialect(dia) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (totalEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Indexing Tasniremt PDF lexicon…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (entries.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No matching entries found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching another Tamazight root, French headword, or reset filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(entries.take(250), key = { it.id }) { entry ->
                        DictionaryWordCard(
                            entry = entry,
                            isBookmarked = entry.id in bookmarkedIds,
                            onClick = {
                                viewModel.resetTranslationState()
                                activeEntryId = entry.id
                            },
                            onToggleBookmark = {
                                viewModel.toggleBookmark(entry, entry.id in bookmarkedIds)
                            }
                        )
                    }
                }
            }
        }
    }

    if (activeEntry != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isBookmarked = activeEntry.id in bookmarkedIds
        val isTranslating = (translationState as? TranslationActionState.Translating)?.entryId == activeEntry.id

        ModalBottomSheet(
            onDismissRequest = { activeEntryId = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeEntry.tamazightTerm,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = tokens.tamazightAccent
                        )
                        if (activeEntry.tifinagh.isNotBlank()) {
                            Text(
                                text = activeEntry.tifinagh,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (activeEntry.tamazightPlural.isNotBlank()) {
                            Text(
                                text = "Plural: ${activeEntry.tamazightPlural}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                if (ttsReady) {
                                    tts.language = Locale.FRENCH
                                    tts.speak(
                                        "${activeEntry.tamazightTerm}. ${activeEntry.frenchTerm}",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        activeEntry.id
                                    )
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .testTag("detail_speak_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = "Speak pronunciation",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleBookmark(activeEntry, isBookmarked) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
                                .testTag("detail_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Save bookmark",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4-Language Meaning Cards
                TranslationMeaningTile(
                    badge = "FR",
                    languageLabel = "Français (Original Source)",
                    meaning = activeEntry.frenchTerm,
                    accentColor = tokens.frenchAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                TranslationMeaningTile(
                    badge = "AR",
                    languageLabel = "العربية (Arabic)",
                    meaning = activeEntry.arabicMeaning.ifBlank { "Tap AI Translate below to generate & cache Arabic meaning" },
                    accentColor = tokens.arabicAccent,
                    isRtl = true,
                    isPlaceholder = activeEntry.arabicMeaning.isBlank()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TranslationMeaningTile(
                    badge = "EN",
                    languageLabel = "English",
                    meaning = activeEntry.englishMeaning.ifBlank { "Tap AI Translate below to generate & cache English meaning" },
                    accentColor = tokens.englishAccent,
                    isPlaceholder = activeEntry.englishMeaning.isBlank()
                )

                if (activeEntry.tamazightVariants.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Variants & Forms",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeEntry.tamazightVariants,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (activeEntry.etymologyNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Column {
                            Text(
                                text = "Analytical Etymology (Part 2 • p.${activeEntry.sourcePage})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeEntry.etymologyNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LanguageBadge(activeEntry.category, MaterialTheme.colorScheme.primary)
                    if (activeEntry.dialectTags.isNotBlank()) {
                        LanguageBadge(activeEntry.dialectTags, tokens.tamazightAccent)
                    }
                    LanguageBadge("PDF p.${activeEntry.sourcePage}", MaterialTheme.colorScheme.tertiary)
                }

                if (translationState is TranslationActionState.Error) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = (translationState as TranslationActionState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.enrichDictionaryEntryWithAi(activeEntry) },
                    enabled = !isTranslating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 50.dp)
                        .testTag("detail_ai_translate_button")
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Translating to Arabic, French & English…")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (activeEntry.aiEnriched) "Refresh Multilingual AI Analysis"
                            else "Translate & Cache (AR • FR • EN)"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val targetPage = activeEntry.sourcePage
                        activeEntryId = null
                        onNavigateToScanPage(targetPage)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("detail_open_pdf_page_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inspect Original PDF Page ${activeEntry.sourcePage} with OCR")
                }
            }
        }
    }
}

@Composable
private fun DictionaryWordCard(
    entry: DictionaryEntryEntity,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val tokens = LocalGlassTokens.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lexicon_item_${entry.id}"),
        onClick = onClick,
        contentPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.tamazightTerm,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.tamazightAccent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entry.aiEnriched) {
                            LanguageBadge("AI 4L", tokens.arabicAccent)
                        }
                    }
                    if (entry.tifinagh.isNotBlank()) {
                        Text(
                            text = entry.tifinagh,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("bookmark_toggle_${entry.id}")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark term",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge("FR", tokens.frenchAccent)
                Text(
                    text = entry.frenchTerm,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (entry.englishMeaning.isNotBlank() || entry.arabicMeaning.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.englishMeaning.isNotBlank()) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LanguageBadge("EN", tokens.englishAccent)
                            Text(
                                text = entry.englishMeaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (entry.arabicMeaning.isNotBlank()) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = entry.arabicMeaning,
                                style = TextStyle(
                                    fontFamily = ArabicNotoFamily,
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                ),
                                color = tokens.arabicAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
                    text = entry.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (entry.dialectTags.isNotBlank()) "${entry.dialectTags} • p.${entry.sourcePage}" else "p.${entry.sourcePage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TranslationMeaningTile(
    badge: String,
    languageLabel: String,
    meaning: String,
    accentColor: Color,
    isRtl: Boolean = false,
    isPlaceholder: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accentColor.copy(alpha = 0.09f))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge(badge, accentColor)
                Text(
                    text = languageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (isRtl && !isPlaceholder) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = meaning,
                        style = TextStyle(
                            fontFamily = ArabicNotoFamily,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = meaning,
                    style = if (isPlaceholder) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaceholder) FontWeight.Normal else FontWeight.Medium,
                    color = if (isPlaceholder) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
