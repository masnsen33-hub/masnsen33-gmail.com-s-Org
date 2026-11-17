package com.example.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiOcrRepository
import com.example.data.local.DictionaryEntryEntity
import com.example.data.local.LocalDictionaryRepository
import com.example.data.local.ThemePreferencesRepository
import com.example.data.model.CommunityWord
import com.example.data.model.MultilingualTranslationResult
import com.example.data.model.OcrScanExtractionResult
import com.example.data.model.ScannedDocument
import com.example.data.model.ScannedTerm
import com.example.data.model.WordBookmark
import com.example.data.remote.TasniremtFirestoreRepository
import com.example.ui.theme.AppThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface OcrProcessState {
    data object Idle : OcrProcessState
    data class Processing(val statusLabel: String, val previewBitmap: Bitmap?) : OcrProcessState
    data class Ready(
        val previewBitmap: Bitmap?,
        val sourceType: String,
        val sourcePage: Int,
        val extraction: OcrScanExtractionResult,
        val savedDocId: String? = null
    ) : OcrProcessState
    data class Error(val message: String, val previewBitmap: Bitmap?) : OcrProcessState
}

sealed interface TranslationActionState {
    data object Idle : TranslationActionState
    data class Translating(val entryId: String) : TranslationActionState
    data class Done(val entryId: String, val result: MultilingualTranslationResult) : TranslationActionState
    data class Error(val message: String) : TranslationActionState
}

sealed interface ContributeSubmitState {
    data object Idle : ContributeSubmitState
    data object Assisting : ContributeSubmitState
    data object Submitting : ContributeSubmitState
    data class Success(val message: String) : ContributeSubmitState
    data class Error(val message: String) : ContributeSubmitState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val firestoreRepo: TasniremtFirestoreRepository,
    private val localDictRepo: LocalDictionaryRepository,
    private val geminiRepo: GeminiOcrRepository,
    private val themePrefs: ThemePreferencesRepository,
    val currentUserId: String,
    val currentUserName: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedDialect = MutableStateFlow("All")
    val selectedDialect: StateFlow<String> = _selectedDialect.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrProcessState>(OcrProcessState.Idle)
    val ocrState: StateFlow<OcrProcessState> = _ocrState.asStateFlow()

    private val _translationState = MutableStateFlow<TranslationActionState>(TranslationActionState.Idle)
    val translationState: StateFlow<TranslationActionState> = _translationState.asStateFlow()

    private val _contributeState = MutableStateFlow<ContributeSubmitState>(ContributeSubmitState.Idle)
    val contributeState: StateFlow<ContributeSubmitState> = _contributeState.asStateFlow()

    private val _selectedScannedDocId = MutableStateFlow<String?>(null)
    val selectedScannedDocId: StateFlow<String?> = _selectedScannedDocId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = themePrefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppThemeMode.SYSTEM)

    val allDictionaryEntries: StateFlow<List<DictionaryEntryEntity>> =
        localDictRepo.observeAllEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val filteredDictionaryEntries: StateFlow<List<DictionaryEntryEntity>> = combine(
        allDictionaryEntries,
        _searchQuery,
        _selectedCategory,
        _selectedDialect
    ) { entries, query, category, dialect ->
        val q = query.trim().lowercase()
        entries.filter { entry ->
            val catOk = category == "All" || entry.category == category
            val diaOk = dialect == "All" || entry.dialectTags.contains(dialect, ignoreCase = true)
            val queryOk = q.isEmpty() ||
                entry.tamazightTerm.lowercase().contains(q) ||
                entry.frenchTerm.lowercase().contains(q) ||
                entry.arabicMeaning.lowercase().contains(q) ||
                entry.englishMeaning.lowercase().contains(q) ||
                entry.tifinagh.contains(q) ||
                entry.etymologyNote.lowercase().contains(q)
            catOk && diaOk && queryOk
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val bookmarksState: StateFlow<UiState<List<WordBookmark>>> =
        firestoreRepo.observeBookmarks(currentUserId)
            .map<List<WordBookmark>, UiState<List<WordBookmark>>> { items ->
                UiState.Success(items.sortedByDescending { it.updatedAt?.seconds ?: 0L })
            }
            .catch { error -> handleStreamError(error, "bookmarks") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val scannedDocsState: StateFlow<UiState<List<ScannedDocument>>> =
        firestoreRepo.observeScannedDocuments(currentUserId)
            .map<List<ScannedDocument>, UiState<List<ScannedDocument>>> { docs ->
                UiState.Success(docs.sortedByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error -> handleStreamError(error, "scanned_documents") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val selectedDocTermsState: StateFlow<UiState<List<ScannedTerm>>> =
        _selectedScannedDocId
            .flatMapLatest { docId ->
                if (docId.isNullOrBlank()) {
                    flowOf(UiState.Success(emptyList()))
                } else {
                    firestoreRepo.observeScannedTerms(currentUserId, docId)
                        .map<List<ScannedTerm>, UiState<List<ScannedTerm>>> { UiState.Success(it) }
                        .catch { error ->
                            val firestoreError = generateSequence(error) { it.cause }
                                .filterIsInstance<FirebaseFirestoreException>()
                                .firstOrNull()
                            if (firestoreError == null && error is CancellationException) throw error
                            val rootError = firestoreError ?: error
                            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load extracted terms"))
                        }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

    val communityWordsState: StateFlow<UiState<List<CommunityWord>>> =
        firestoreRepo.observeCommunityWords(currentUserId)
            .map<List<CommunityWord>, UiState<List<CommunityWord>>> { words ->
                UiState.Success(words.sortedByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error -> handleStreamError(error, "community_words") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    init {
        viewModelScope.launch {
            localDictRepo.ensureSeeded()
        }
        viewModelScope.launch {
            firestoreRepo.ensureUserProfile(currentUserName, themeMode.value.name)
        }
        // Sync cloud theme setting on login
        viewModelScope.launch {
            firestoreRepo.observeUserProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Profile sync warning: ${error.localizedMessage}")
                }
                .collect { profile ->
                    if (profile != null) {
                        val remoteMode = AppThemeMode.fromRaw(profile.themeMode)
                        if (remoteMode != themeMode.value) {
                            themePrefs.setThemeMode(remoteMode)
                        }
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectDialect(dialect: String) {
        _selectedDialect.value = dialect
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            themePrefs.setThemeMode(mode)
            firestoreRepo.updateThemePreference(mode.name)
        }
    }

    fun cycleThemeMode() {
        val next = when (themeMode.value) {
            AppThemeMode.SYSTEM -> AppThemeMode.DARK
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.SYSTEM
        }
        setThemeMode(next)
    }

    fun enrichDictionaryEntryWithAi(entry: DictionaryEntryEntity) {
        viewModelScope.launch {
            _translationState.value = TranslationActionState.Translating(entry.id)
            val result = geminiRepo.translateTermMultilingual(
                tamazightTerm = entry.tamazightTerm,
                frenchHint = entry.frenchTerm,
                contextNote = entry.etymologyNote
            )
            result.fold(
                onSuccess = { translated ->
                    localDictRepo.cacheTranslation(
                        entryId = entry.id,
                        arabicMeaning = translated.arabicMeaning,
                        englishMeaning = translated.englishMeaning,
                        tifinagh = translated.tifinagh.ifBlank { entry.tifinagh },
                        etymologyNote = if (entry.etymologyNote.isBlank()) translated.etymologyNote else entry.etymologyNote
                    )
                    _translationState.value = TranslationActionState.Done(entry.id, translated)
                },
                onFailure = { err ->
                    _translationState.value = TranslationActionState.Error(
                        err.localizedMessage ?: "AI translation failed."
                    )
                }
            )
        }
    }

    fun resetTranslationState() {
        _translationState.value = TranslationActionState.Idle
    }

    fun toggleBookmark(entry: DictionaryEntryEntity, currentlyBookmarked: Boolean) {
        viewModelScope.launch {
            val res = if (currentlyBookmarked) {
                firestoreRepo.removeBookmark(entry.id)
            } else {
                firestoreRepo.saveBookmark(
                    entryId = entry.id,
                    tamazightTerm = entry.tamazightTerm,
                    tamazightPlural = entry.tamazightPlural,
                    tifinagh = entry.tifinagh,
                    frenchTerm = entry.frenchTerm,
                    arabicMeaning = entry.arabicMeaning,
                    englishMeaning = entry.englishMeaning,
                    etymologyNote = entry.etymologyNote,
                    personalNote = "",
                    category = entry.category
                )
            }
            res.fold(
                onSuccess = {
                    _statusBanner.value = if (currentlyBookmarked) {
                        "Removed '${entry.tamazightTerm}' from cloud bookmarks"
                    } else {
                        "Saved '${entry.tamazightTerm}' to cloud & offline library"
                    }
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Bookmark sync failed"
                }
            )
        }
    }

    fun updateBookmarkPersonalNote(bookmark: WordBookmark, note: String) {
        viewModelScope.launch {
            firestoreRepo.saveBookmark(
                entryId = bookmark.entryId.ifBlank { bookmark.id },
                tamazightTerm = bookmark.tamazightTerm,
                tamazightPlural = bookmark.tamazightPlural,
                tifinagh = bookmark.tifinagh,
                frenchTerm = bookmark.frenchTerm,
                arabicMeaning = bookmark.arabicMeaning,
                englishMeaning = bookmark.englishMeaning,
                etymologyNote = bookmark.etymologyNote,
                personalNote = note,
                category = bookmark.category
            ).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to update note"
            }
        }
    }

    fun runOcrExtraction(
        bitmap: Bitmap,
        sourceType: String,
        sourcePage: Int,
        sourceHint: String
    ) {
        viewModelScope.launch {
            _ocrState.value = OcrProcessState.Processing(
                statusLabel = "Running OCR & parsing Tamazight / FR / AR / EN fields…",
                previewBitmap = bitmap
            )
            val result = geminiRepo.parseDocumentImageWithOcr(bitmap, sourceHint)
            result.fold(
                onSuccess = { extraction ->
                    _ocrState.value = OcrProcessState.Ready(
                        previewBitmap = bitmap,
                        sourceType = sourceType,
                        sourcePage = sourcePage,
                        extraction = extraction,
                        savedDocId = null
                    )
                },
                onFailure = { err ->
                    _ocrState.value = OcrProcessState.Error(
                        message = err.localizedMessage ?: "OCR failed to parse document.",
                        previewBitmap = bitmap
                    )
                }
            )
        }
    }

    fun syncCurrentOcrToCloud(customTitle: String? = null) {
        val current = _ocrState.value as? OcrProcessState.Ready ?: return
        if (current.savedDocId != null) return
        viewModelScope.launch {
            val title = customTitle?.trim()?.ifBlank { current.extraction.suggestedTitle }
                ?: current.extraction.suggestedTitle
            val res = firestoreRepo.saveScannedDocumentWithTerms(
                title = title,
                sourceType = current.sourceType,
                sourcePage = current.sourcePage,
                extraction = current.extraction
            )
            res.fold(
                onSuccess = { docId ->
                    _ocrState.value = current.copy(savedDocId = docId)
                    _statusBanner.value = "Synced '$title' (${current.extraction.terms.size} terms) to Cloud Firestore"
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Cloud sync failed"
                }
            )
        }
    }

    fun clearOcrState() {
        _ocrState.value = OcrProcessState.Idle
    }

    fun selectScannedDocument(docId: String?) {
        _selectedScannedDocId.value = docId
    }

    fun deleteScannedDocument(docId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteScannedDocument(docId).fold(
                onSuccess = {
                    if (_selectedScannedDocId.value == docId) {
                        _selectedScannedDocId.value = null
                    }
                    _statusBanner.value = "Document deleted from cloud storage"
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Delete failed"
                }
            )
        }
    }

    fun assistContributeTranslations(
        tamazightTerm: String,
        frenchHint: String,
        contextHint: String,
        onSuggestionReady: (MultilingualTranslationResult) -> Unit
    ) {
        if (tamazightTerm.isBlank() && frenchHint.isBlank()) {
            _contributeState.value = ContributeSubmitState.Error("Enter at least a Tamazight or French term first.")
            return
        }
        viewModelScope.launch {
            _contributeState.value = ContributeSubmitState.Assisting
            geminiRepo.translateTermMultilingual(
                tamazightTerm = tamazightTerm.ifBlank { frenchHint },
                frenchHint = frenchHint,
                contextNote = contextHint
            ).fold(
                onSuccess = { suggestion ->
                    _contributeState.value = ContributeSubmitState.Idle
                    onSuggestionReady(suggestion)
                },
                onFailure = { err ->
                    _contributeState.value = ContributeSubmitState.Error(
                        err.localizedMessage ?: "AI multilingual suggestion failed."
                    )
                }
            )
        }
    }

    fun submitCommunityWord(
        tamazightTerm: String,
        tamazightPlural: String,
        tifinagh: String,
        arabicMeaning: String,
        frenchMeaning: String,
        englishMeaning: String,
        dialect: String,
        category: String,
        exampleNote: String,
        onSubmitted: () -> Unit
    ) {
        viewModelScope.launch {
            _contributeState.value = ContributeSubmitState.Submitting
            firestoreRepo.contributeCommunityWord(
                tamazightTerm = tamazightTerm,
                tamazightPlural = tamazightPlural,
                tifinagh = tifinagh,
                arabicMeaning = arabicMeaning,
                frenchMeaning = frenchMeaning,
                englishMeaning = englishMeaning,
                dialect = dialect,
                category = category,
                exampleNote = exampleNote
            ).fold(
                onSuccess = {
                    _contributeState.value = ContributeSubmitState.Success(
                        "Published '$tamazightTerm' to the communal Tamazight lexicon!"
                    )
                    onSubmitted()
                },
                onFailure = { err ->
                    _contributeState.value = ContributeSubmitState.Error(
                        err.localizedMessage ?: "Could not publish word."
                    )
                }
            )
        }
    }

    fun resetContributeState() {
        _contributeState.value = ContributeSubmitState.Idle
    }

    fun endorseCommunityWord(word: CommunityWord) {
        viewModelScope.launch {
            firestoreRepo.endorseCommunityWord(word.id, word.endorsementsCount).fold(
                onSuccess = {
                    _statusBanner.value = "Endorsed '${word.tamazightTerm}'"
                },
                onFailure = { err ->
                    _statusBanner.value = err.localizedMessage ?: "Already endorsed"
                }
            )
        }
    }

    fun deleteCommunityWord(wordId: String) {
        viewModelScope.launch {
            firestoreRepo.deleteCommunityWord(wordId).fold(
                onSuccess = { _statusBanner.value = "Contribution removed" },
                onFailure = { err -> _statusBanner.value = err.localizedMessage ?: "Delete failed" }
            )
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<List<T>>>.handleStreamError(
        error: Throwable,
        collectionName: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $collectionName", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Failed to sync $collectionName"))
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
