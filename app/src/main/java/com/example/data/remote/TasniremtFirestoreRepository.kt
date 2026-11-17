package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CommunityWord
import com.example.data.model.OcrScanExtractionResult
import com.example.data.model.ScannedDocument
import com.example.data.model.ScannedTerm
import com.example.data.model.UserProfile
import com.example.data.model.WordBookmark
import com.example.data.model.buildServerTimestampsCreate
import com.example.data.model.takeBounded
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TasniremtFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { UUID.randomUUID().toString().replace("-", "") }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    unwrapAndThrow(error, OperationType.GET, docRef.path, userId)
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, themeMode: String = "SYSTEM"): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val payload = buildServerTimestampsCreate(
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName.takeBounded(100).ifBlank { "Scholar" },
                        "themeMode" to themeMode,
                        "preferredLanguage" to "ALL"
                    )
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateThemePreference(themeMode: String): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "themeMode" to themeMode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = buildServerTimestampsCreate(
                    mapOf(
                        "userId" to uid,
                        "displayName" to (auth.currentUser?.displayName ?: "Scholar").takeBounded(100),
                        "themeMode" to themeMode,
                        "preferredLanguage" to "ALL"
                    )
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeBookmarks(userId: String): Flow<List<WordBookmark>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/bookmarks"
        emitAll(
            db.collection("users").document(userId).collection("bookmarks")
                .snapshots()
                .map { snap ->
                    snap.toObjects(WordBookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveBookmark(
        entryId: String,
        tamazightTerm: String,
        tamazightPlural: String,
        tifinagh: String,
        frenchTerm: String,
        arabicMeaning: String,
        englishMeaning: String,
        etymologyNote: String,
        personalNote: String,
        category: String
    ): Result<Unit> {
        val docId = sanitizeDocId(entryId)
        var path = "users/unknown/bookmarks/$docId"
        return try {
            val uid = requireUserId()
            require(tamazightTerm.isNotBlank() && frenchTerm.isNotBlank()) {
                "Tamazight and French terms are required."
            }
            val docRef = db.collection("users").document(uid).collection("bookmarks").document(docId)
            path = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "tamazightTerm" to tamazightTerm.takeBounded(300),
                        "tamazightPlural" to tamazightPlural.takeBounded(300),
                        "tifinagh" to tifinagh.takeBounded(300),
                        "frenchTerm" to frenchTerm.takeBounded(300),
                        "arabicMeaning" to arabicMeaning.takeBounded(600),
                        "englishMeaning" to englishMeaning.takeBounded(600),
                        "etymologyNote" to etymologyNote.takeBounded(1500),
                        "personalNote" to personalNote.takeBounded(1000),
                        "category" to category.takeBounded(80).ifBlank { "Society & Culture" },
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = buildServerTimestampsCreate(
                    mapOf(
                        "entryId" to entryId.takeBounded(128).ifBlank { docId },
                        "tamazightTerm" to tamazightTerm.takeBounded(300),
                        "tamazightPlural" to tamazightPlural.takeBounded(300),
                        "tifinagh" to tifinagh.takeBounded(300),
                        "frenchTerm" to frenchTerm.takeBounded(300),
                        "arabicMeaning" to arabicMeaning.takeBounded(600),
                        "englishMeaning" to englishMeaning.takeBounded(600),
                        "etymologyNote" to etymologyNote.takeBounded(1500),
                        "personalNote" to personalNote.takeBounded(1000),
                        "category" to category.takeBounded(80).ifBlank { "Society & Culture" }
                    )
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun removeBookmark(bookmarkId: String): Result<Unit> {
        val docId = sanitizeDocId(bookmarkId)
        var path = "users/unknown/bookmarks/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("bookmarks").document(docId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeScannedDocuments(userId: String): Flow<List<ScannedDocument>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/scanned_documents"
        emitAll(
            db.collection("users").document(userId).collection("scanned_documents")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ScannedDocument::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, userId) }
        )
    }

    fun observeScannedTerms(userId: String, docId: String): Flow<List<ScannedTerm>> = flow {
        if (userId.isBlank() || docId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val safeDocId = sanitizeDocId(docId)
        val path = "users/$userId/scanned_documents/$safeDocId/extracted_terms"
        emitAll(
            db.collection("users").document(userId)
                .collection("scanned_documents").document(safeDocId)
                .collection("extracted_terms")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ScannedTerm::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, userId) }
        )
    }

    suspend fun saveScannedDocumentWithTerms(
        title: String,
        sourceType: String,
        sourcePage: Int,
        extraction: OcrScanExtractionResult
    ): Result<String> {
        val docId = "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        var path = "users/unknown/scanned_documents/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("scanned_documents").document(docId)
            path = docRef.path

            val safeSourceType = if (sourceType in setOf("CAMERA", "IMAGE", "PDF_PAGE", "BOOK_PDF")) sourceType else "IMAGE"
            val boundedTerms = extraction.terms.take(25)

            val batch = db.batch()
            val docPayload = buildServerTimestampsCreate(
                mapOf(
                    "title" to title.takeBounded(160).ifBlank { extraction.suggestedTitle.takeBounded(160).ifBlank { "OCR Document" } },
                    "sourceType" to safeSourceType,
                    "sourcePage" to sourcePage.coerceIn(0, 5000),
                    "rawOcrText" to extraction.rawOcrText.takeBounded(15000),
                    "detectedTermsCount" to boundedTerms.size,
                    "summaryNote" to extraction.summaryNote.takeBounded(2000)
                )
            )
            batch.set(docRef, docPayload)

            boundedTerms.forEachIndexed { idx, term ->
                val termId = "term_${idx + 1}_${UUID.randomUUID().toString().take(6)}"
                val termRef = docRef.collection("extracted_terms").document(termId)
                val termPayload = buildServerTimestampsCreate(
                    mapOf(
                        "tamazightTerm" to term.tamazightTerm.takeBounded(300).ifBlank { term.frenchTerm.takeBounded(300).ifBlank { "Unparsed term" } },
                        "tamazightPlural" to term.tamazightPlural.takeBounded(300),
                        "tifinagh" to term.tifinagh.takeBounded(300),
                        "frenchTerm" to term.frenchTerm.takeBounded(300).ifBlank { term.tamazightTerm.takeBounded(300).ifBlank { "Terme" } },
                        "arabicMeaning" to term.arabicMeaning.takeBounded(600),
                        "englishMeaning" to term.englishMeaning.takeBounded(600),
                        "etymologyNote" to term.etymologyNote.takeBounded(1500),
                        "dialectTags" to term.dialectTags.takeBounded(120)
                    )
                )
                batch.set(termRef, termPayload)
            }

            batch.commit().await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteScannedDocument(docId: String): Result<Unit> {
        val safeId = sanitizeDocId(docId)
        var path = "users/unknown/scanned_documents/$safeId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("scanned_documents").document(safeId)
            path = docRef.path
            // Clean up child terms first so no orphans remain
            val termsSnap = docRef.collection("extracted_terms").get().await()
            val batch = db.batch()
            termsSnap.documents.forEach { batch.delete(it.reference) }
            batch.delete(docRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeCommunityWords(currentUserId: String): Flow<List<CommunityWord>> = flow {
        if (currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "community_words"
        emitAll(
            db.collection("community_words")
                .whereEqualTo("visibility", "COMMUNITY")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CommunityWord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error -> unwrapAndThrow(error, OperationType.LIST, path, currentUserId) }
        )
    }

    suspend fun contributeCommunityWord(
        tamazightTerm: String,
        tamazightPlural: String,
        tifinagh: String,
        arabicMeaning: String,
        frenchMeaning: String,
        englishMeaning: String,
        dialect: String,
        category: String,
        exampleNote: String
    ): Result<String> {
        val wordId = "word_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        var path = "community_words/$wordId"
        return try {
            val uid = requireUserId()
            require(tamazightTerm.trim().isNotEmpty()) { "Tamazight word cannot be empty." }
            require(arabicMeaning.trim().isNotEmpty()) { "Arabic translation cannot be empty." }
            require(frenchMeaning.trim().isNotEmpty()) { "French translation cannot be empty." }
            require(englishMeaning.trim().isNotEmpty()) { "English translation cannot be empty." }

            val authorName = (auth.currentUser?.displayName ?: "Amazigh Learner").takeBounded(100)
            val docRef = db.collection("community_words").document(wordId)
            path = docRef.path
            val payload = buildServerTimestampsCreate(
                mapOf(
                    "authorUid" to uid,
                    "authorName" to authorName,
                    "visibility" to "COMMUNITY",
                    "tamazightTerm" to tamazightTerm.takeBounded(250),
                    "tamazightPlural" to tamazightPlural.takeBounded(250),
                    "tifinagh" to tifinagh.takeBounded(250),
                    "arabicMeaning" to arabicMeaning.takeBounded(500),
                    "frenchMeaning" to frenchMeaning.takeBounded(500),
                    "englishMeaning" to englishMeaning.takeBounded(500),
                    "dialect" to dialect.takeBounded(80),
                    "category" to category.takeBounded(80).ifBlank { "Society & Culture" },
                    "exampleNote" to exampleNote.takeBounded(1000),
                    "endorsementsCount" to 0L
                )
            )
            docRef.set(payload).await()
            Result.success(wordId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun endorseCommunityWord(wordId: String, currentEndorsements: Long): Result<Unit> {
        val safeWordId = sanitizeDocId(wordId)
        var path = "community_words/$safeWordId"
        return try {
            val uid = requireUserId()
            val wordRef = db.collection("community_words").document(safeWordId)
            val endorseRef = wordRef.collection("endorsements").document(uid)
            path = endorseRef.path

            val alreadyEndorsed = endorseRef.get().await().exists()
            if (alreadyEndorsed) {
                return Result.failure(IllegalStateException("You have already endorsed this word."))
            }

            val batch = db.batch()
            batch.set(
                endorseRef,
                mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                wordRef,
                mapOf(
                    "endorsementsCount" to currentEndorsements + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCommunityWord(wordId: String): Result<Unit> {
        val safeWordId = sanitizeDocId(wordId)
        val path = "community_words/$safeWordId"
        return try {
            requireUserId()
            db.collection("community_words").document(safeWordId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun unwrapAndThrow(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) {
            throw error
        }
        val toLog = firestoreError ?: (error as? Exception)
        if (toLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(toLog, operationType, path)
        }
        throw (firestoreError ?: error)
    }
}
