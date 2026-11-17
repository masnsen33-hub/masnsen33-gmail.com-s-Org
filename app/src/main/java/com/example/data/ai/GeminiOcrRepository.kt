package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.MultilingualTranslationResult
import com.example.data.model.OcrScanExtractionResult
import com.example.data.model.ParsedOcrTermDraft
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "GeminiOcrRepo"
private const val AI_TIMEOUT_MS = 60_000L

class GeminiOcrRepository {

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "suggestedTitle" to Schema.string("Short descriptive document title (max 80 chars)"),
            "summaryNote" to Schema.string("Brief 1-2 sentence summary of the scanned page or document"),
            "rawOcrText" to Schema.string("Full transcribed OCR text preserving French, Tamazight diacritics (ɣ, ɛ, ḍ, ḥ, ṛ, ṣ, ṭ, ẓ, č, ǧ), Arabic, or Tifinagh"),
            "terms" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "tamazightTerm" to Schema.string("Tamazight term in standard Berber Latin script"),
                        "tamazightPlural" to Schema.string("Plural form in parentheses if present, or empty string"),
                        "tifinagh" to Schema.string("Neo-Tifinagh transliteration of the Tamazight term"),
                        "frenchTerm" to Schema.string("French term or definition"),
                        "arabicMeaning" to Schema.string("Accurate Arabic translation of the meaning"),
                        "englishMeaning" to Schema.string("Accurate English translation of the meaning"),
                        "etymologyNote" to Schema.string("Morphological root, historical context, or note from text"),
                        "dialectTags" to Schema.string("Dialect codes like Kb., Tq., Mz., Gd., Tg., P.B. if present")
                    ),
                    optionalProperties = listOf("tamazightPlural", "tifinagh", "etymologyNote", "dialectTags")
                ),
                description = "Up to 20 structured dictionary or terminology entries extracted from the image"
            )
        )
    )

    private val translationSchema = Schema.obj(
        properties = mapOf(
            "tamazightTerm" to Schema.string("Standardized Tamazight Latin spelling"),
            "tamazightPlural" to Schema.string("Tamazight plural form if applicable, else empty string"),
            "tifinagh" to Schema.string("Neo-Tifinagh script spelling"),
            "arabicMeaning" to Schema.string("Clear Arabic translation and brief gloss"),
            "frenchMeaning" to Schema.string("Clear French translation and brief gloss"),
            "englishMeaning" to Schema.string("Clear English translation and brief gloss"),
            "etymologyNote" to Schema.string("Brief root/morphology or historical nuance note")
        ),
        optionalProperties = listOf("tamazightPlural", "etymologyNote")
    )

    private val ocrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    private val translatorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = translationSchema
            }
        )
    }

    suspend fun parseDocumentImageWithOcr(
        bitmap: Bitmap,
        sourceHint: String = "Historical Tamazight-French dictionary or document"
    ): Result<OcrScanExtractionResult> {
        return try {
            val scaledBitmap = scaleDownIfNeeded(bitmap, maxDim = 1800)
            val prompt = """
                Perform high-precision multilingual OCR and structured terminology extraction on this document image.
                Context: $sourceHint.
                Preserve Tamazight Latin characters faithfully (ɣ, ɛ, ḍ, ḥ, ṛ, ṣ, ṭ, ẓ, č, ǧ), as well as French accents, Arabic script, and Neo-Tifinagh if present.
                For every recognized term or historical concept in the image, extract the Tamazight term, its plural (if shown), Neo-Tifinagh script, French meaning, Arabic translation, English translation, dialect tags (Kb., Tq., Mz., Gd., Tg., P.B.), and any etymology/morphological breakdown.
                If the image shows general text rather than a dictionary table, extract up to 12 key terms/phrases from the OCR text with full Tamazight, Arabic, French, and English translations.
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                ocrModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("OCR model returned an empty response."))
            val json = JSONObject(jsonText)
            val termsArray = json.optJSONArray("terms")
            val parsedTerms = buildList {
                if (termsArray != null) {
                    for (i in 0 until termsArray.length()) {
                        val item = termsArray.getJSONObject(i)
                        add(
                            ParsedOcrTermDraft(
                                tamazightTerm = item.optString("tamazightTerm", "").trim(),
                                tamazightPlural = item.optString("tamazightPlural", "").trim(),
                                tifinagh = item.optString("tifinagh", "").trim(),
                                frenchTerm = item.optString("frenchTerm", "").trim(),
                                arabicMeaning = item.optString("arabicMeaning", "").trim(),
                                englishMeaning = item.optString("englishMeaning", "").trim(),
                                etymologyNote = item.optString("etymologyNote", "").trim(),
                                dialectTags = item.optString("dialectTags", "").trim()
                            )
                        )
                    }
                }
            }
            Result.success(
                OcrScanExtractionResult(
                    suggestedTitle = json.optString("suggestedTitle", "Scanned Document").trim(),
                    summaryNote = json.optString("summaryNote", "").trim(),
                    rawOcrText = json.optString("rawOcrText", "").trim(),
                    terms = parsedTerms
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "OCR extraction timed out", e)
            Result.failure(IllegalStateException("OCR analysis timed out after 60 seconds. Please try a clearer image or single page."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR extraction failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateTermMultilingual(
        tamazightTerm: String,
        frenchHint: String = "",
        contextNote: String = ""
    ): Result<MultilingualTranslationResult> {
        return try {
            val prompt = """
                You are an expert Amazigh (Tamazight) historical linguist and multilingual lexicographer.
                Translate and analyze the following entry accurately across Tamazight, Arabic, French, English, and Neo-Tifinagh:
                - Tamazight term: $tamazightTerm
                - French clue / headword: ${frenchHint.ifBlank { "(not provided — infer from Tamazight)" }}
                - Etymology / historical context: ${contextNote.ifBlank { "(general historical / cultural usage)" }}

                Provide natural, accurate scholarly translations in Arabic (العربية), French (Français), and English, plus Neo-Tifinagh script and concise morphological/root commentary.
            """.trimIndent()

            val response = withTimeout(AI_TIMEOUT_MS) {
                translatorModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Translation model returned an empty response."))
            val json = JSONObject(jsonText)
            Result.success(
                MultilingualTranslationResult(
                    tamazightTerm = json.optString("tamazightTerm", tamazightTerm).trim(),
                    tamazightPlural = json.optString("tamazightPlural", "").trim(),
                    tifinagh = json.optString("tifinagh", "").trim(),
                    arabicMeaning = json.optString("arabicMeaning", "").trim(),
                    frenchMeaning = json.optString("frenchMeaning", frenchHint).trim(),
                    englishMeaning = json.optString("englishMeaning", "").trim(),
                    etymologyNote = json.optString("etymologyNote", contextNote).trim()
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Multilingual translation timed out", e)
            Result.failure(IllegalStateException("Translation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multilingual translation failed", e)
            Result.failure(e)
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / longest.toFloat()
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
