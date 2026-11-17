package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CommunityWord
import com.example.data.model.ScannedDocument
import com.example.data.model.ScannedTerm
import com.example.data.model.WordBookmark
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.ArabicNotoFamily
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPillChip
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.LocalGlassTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel,
    onNavigateToContribute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scannedDocsState by viewModel.scannedDocsState.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val communityWordsState by viewModel.communityWordsState.collectAsStateWithLifecycle()
    val selectedDocId by viewModel.selectedScannedDocId.collectAsStateWithLifecycle()
    val selectedDocTermsState by viewModel.selectedDocTermsState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) }
    var inspectedDoc by remember { mutableStateOf<ScannedDocument?>(null) }
    val tokens = LocalGlassTokens.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), elevated = true, contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GlassPillChip(
                        label = "Scans",
                        selected = activeTab == 0,
                        leadingIcon = Icons.Rounded.Description,
                        onClick = { activeTab = 0 },
                        modifier = Modifier.weight(1f).testTag("library_tab_scans")
                    )
                    GlassPillChip(
                        label = "Saved",
                        selected = activeTab == 1,
                        leadingIcon = Icons.Rounded.Bookmark,
                        onClick = { activeTab = 1 },
                        modifier = Modifier.weight(1f).testTag("library_tab_bookmarks")
                    )
                    GlassPillChip(
                        label = "Community",
                        selected = activeTab == 2,
                        leadingIcon = Icons.Rounded.Groups,
                        onClick = { activeTab = 2 },
                        modifier = Modifier.weight(1f).testTag("library_tab_community")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (activeTab) {
                0 -> ScannedDocsList(
                    state = scannedDocsState,
                    onOpenDoc = { doc ->
                        viewModel.selectScannedDocument(doc.id)
                        inspectedDoc = doc
                    },
                    onDeleteDoc = { doc -> viewModel.deleteScannedDocument(doc.id) }
                )

                1 -> BookmarksList(
                    state = bookmarksState,
                    onRemove = { bm ->
                        viewModel.toggleBookmark(
                            entry = com.example.data.local.DictionaryEntryEntity(
                                id = bm.entryId.ifBlank { bm.id },
                                frenchTerm = bm.frenchTerm,
                                tamazightTerm = bm.tamazightTerm,
                                tamazightPlural = bm.tamazightPlural,
                                tamazightVariants = "",
                                tifinagh = bm.tifinagh,
                                englishMeaning = bm.englishMeaning,
                                arabicMeaning = bm.arabicMeaning,
                                frenchGloss = "",
                                etymologyNote = bm.etymologyNote,
                                dialectTags = "",
                                refCodes = "",
                                category = bm.category,
                                sourcePage = 1
                            ),
                            currentlyBookmarked = true
                        )
                    }
                )

                else -> CommunityWordsList(
                    state = communityWordsState,
                    currentUserId = viewModel.currentUserId,
                    onEndorse = viewModel::endorseCommunityWord,
                    onDelete = viewModel::deleteCommunityWord,
                    onNavigateToContribute = onNavigateToContribute
                )
            }
        }
    }

    if (inspectedDoc != null && selectedDocId != null) {
        val doc = inspectedDoc!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                inspectedDoc = null
                viewModel.selectScannedDocument(null)
            },
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
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageBadge(doc.sourceType, MaterialTheme.colorScheme.primary)
                    if (doc.sourcePage > 0) {
                        LanguageBadge("Page ${doc.sourcePage}", tokens.tamazightAccent)
                    }
                    LanguageBadge("Cloud + Offline", tokens.arabicAccent)
                }
                if (doc.summaryNote.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = doc.summaryNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Extracted Terms",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                when (val ts = selectedDocTermsState) {
                    is UiState.Loading -> CircularProgressIndicator()
                    is UiState.Error -> Text(ts.message, color = MaterialTheme.colorScheme.error)
                    is UiState.Success -> {
                        if (ts.data.isEmpty()) {
                            Text(
                                text = "No structured terms extracted for this scan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ts.data.forEach { term ->
                                ScannedTermCard(term = term)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                if (doc.rawOcrText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Raw OCR Transcription",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Text(
                            text = doc.rawOcrText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedDocsList(
    state: UiState<List<ScannedDocument>>,
    onOpenDoc: (ScannedDocument) -> Unit,
    onDeleteDoc: (ScannedDocument) -> Unit
) {
    val tokens = LocalGlassTokens.current
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> GlassCard(Modifier.fillMaxWidth()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No cloud-synced scans yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Scan a camera photo, image, or PDF page in OCR Scan and tap Sync to Cloud for cross-device offline viewing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(state.data, key = { it.id }) { doc ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().testTag("scanned_doc_card_${doc.id}"),
                            onClick = { onOpenDoc(doc) },
                            contentPadding = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        LanguageBadge(doc.sourceType, MaterialTheme.colorScheme.primary)
                                        LanguageBadge("${doc.detectedTermsCount} terms", tokens.arabicAccent)
                                        if (doc.sourcePage > 0) {
                                            LanguageBadge("p.${doc.sourcePage}", tokens.tamazightAccent)
                                        }
                                    }
                                    if (doc.summaryNote.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = doc.summaryNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteDoc(doc) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete scanned document",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksList(
    state: UiState<List<WordBookmark>>,
    onRemove: (WordBookmark) -> Unit
) {
    val tokens = LocalGlassTokens.current
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> GlassCard(Modifier.fillMaxWidth()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No saved bookmarks yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap the bookmark icon on any dictionary term to sync it across all your devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(state.data, key = { it.id }) { bm ->
                        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.tamazightTerm,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.tamazightAccent
                                    )
                                    if (bm.tifinagh.isNotBlank()) {
                                        Text(
                                            text = bm.tifinagh,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "FR: ${bm.frenchTerm}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (bm.englishMeaning.isNotBlank()) {
                                        Text(
                                            text = "EN: ${bm.englishMeaning}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (bm.arabicMeaning.isNotBlank()) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                            Text(
                                                text = bm.arabicMeaning,
                                                style = TextStyle(
                                                    fontFamily = ArabicNotoFamily,
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                                                ),
                                                color = tokens.arabicAccent,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { onRemove(bm) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Remove bookmark",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityWordsList(
    state: UiState<List<CommunityWord>>,
    currentUserId: String,
    onEndorse: (CommunityWord) -> Unit,
    onDelete: (String) -> Unit,
    onNavigateToContribute: () -> Unit
) {
    val tokens = LocalGlassTokens.current
    when (state) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> GlassCard(Modifier.fillMaxWidth()) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            if (state.data.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Be the first contributor!", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Share Tamazight words you know with Arabic, French, and English meanings for communal learning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onNavigateToContribute,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Add a New Word")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(state.data, key = { it.id }) { word ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().testTag("community_word_${word.id}"),
                            contentPadding = 14.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = word.tamazightTerm,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.tamazightAccent
                                        )
                                        if (word.tifinagh.isNotBlank()) {
                                            Text(
                                                text = word.tifinagh,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (word.tamazightPlural.isNotBlank()) {
                                            Text(
                                                text = "Pl.: ${word.tamazightPlural}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = { onEndorse(word) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("endorse_word_${word.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ThumbUpOffAlt,
                                                contentDescription = "Endorse",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("${word.endorsementsCount}")
                                        }
                                        if (word.authorUid == currentUserId) {
                                            IconButton(
                                                onClick = { onDelete(word.id) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete contribution",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("FR: ${word.frenchMeaning}", style = MaterialTheme.typography.bodyMedium)
                                Text("EN: ${word.englishMeaning}", style = MaterialTheme.typography.bodyMedium)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    Text(
                                        text = word.arabicMeaning,
                                        style = TextStyle(
                                            fontFamily = ArabicNotoFamily,
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = tokens.arabicAccent,
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    )
                                }

                                if (word.exampleNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = word.exampleNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "By ${word.authorName}${if (word.dialect.isNotBlank()) " • ${word.dialect}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = word.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannedTermCard(term: ScannedTerm) {
    val tokens = LocalGlassTokens.current
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = term.tamazightTerm,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.tamazightAccent
            )
            if (term.tifinagh.isNotBlank()) {
                Text(term.tifinagh, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("FR: ${term.frenchTerm}", style = MaterialTheme.typography.bodySmall)
            Text("EN: ${term.englishMeaning}", style = MaterialTheme.typography.bodySmall)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = term.arabicMeaning,
                    style = TextStyle(fontFamily = ArabicNotoFamily, fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = tokens.arabicAccent,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
