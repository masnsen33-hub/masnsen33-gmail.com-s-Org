package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val themeMode: String = "SYSTEM",
    val preferredLanguage: String = "ALL",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WordBookmark(
    @DocumentId val id: String = "",
    val entryId: String = "",
    val tamazightTerm: String = "",
    val tamazightPlural: String = "",
    val tifinagh: String = "",
    val frenchTerm: String = "",
    val arabicMeaning: String = "",
    val englishMeaning: String = "",
    val etymologyNote: String = "",
    val personalNote: String = "",
    val category: String = "Society & Culture",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScannedDocument(
    @DocumentId val id: String = "",
    val title: String = "",
    val sourceType: String = "IMAGE",
    val sourcePage: Long = 0L,
    val rawOcrText: String = "",
    val detectedTermsCount: Long = 0L,
    val summaryNote: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScannedTerm(
    @DocumentId val id: String = "",
    val tamazightTerm: String = "",
    val tamazightPlural: String = "",
    val tifinagh: String = "",
    val frenchTerm: String = "",
    val arabicMeaning: String = "",
    val englishMeaning: String = "",
    val etymologyNote: String = "",
    val dialectTags: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CommunityWord(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val visibility: String = "COMMUNITY",
    val tamazightTerm: String = "",
    val tamazightPlural: String = "",
    val tifinagh: String = "",
    val arabicMeaning: String = "",
    val frenchMeaning: String = "",
    val englishMeaning: String = "",
    val dialect: String = "",
    val category: String = "Society & Culture",
    val exampleNote: String = "",
    val endorsementsCount: Long = 0L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class ParsedOcrTermDraft(
    val tamazightTerm: String,
    val tamazightPlural: String,
    val tifinagh: String,
    val frenchTerm: String,
    val arabicMeaning: String,
    val englishMeaning: String,
    val etymologyNote: String,
    val dialectTags: String
)

data class OcrScanExtractionResult(
    val suggestedTitle: String,
    val summaryNote: String,
    val rawOcrText: String,
    val terms: List<ParsedOcrTermDraft>
)

data class MultilingualTranslationResult(
    val tamazightTerm: String,
    val tamazightPlural: String,
    val tifinagh: String,
    val arabicMeaning: String,
    val frenchMeaning: String,
    val englishMeaning: String,
    val etymologyNote: String
)

fun String?.takeBounded(maxLen: Int): String =
    (this ?: "").trim().take(maxLen)

fun buildServerTimestampsCreate(fields: Map<String, Any?>): Map<String, Any> {
    val clean = fields.filterValues { it != null }.toMutableMap()
    clean["createdAt"] = FieldValue.serverTimestamp()
    clean["updatedAt"] = FieldValue.serverTimestamp()
    @Suppress("UNCHECKED_CAST")
    return clean as Map<String, Any>
}
