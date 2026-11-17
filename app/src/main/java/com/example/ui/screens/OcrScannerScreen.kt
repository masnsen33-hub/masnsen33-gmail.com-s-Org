package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.PdfBitmapHelper
import com.example.data.model.ParsedOcrTermDraft
import com.example.ui.MainViewModel
import com.example.ui.OcrProcessState
import com.example.ui.theme.ArabicNotoFamily
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPillChip
import com.example.ui.theme.LanguageBadge
import com.example.ui.theme.LocalGlassTokens
import kotlinx.coroutines.launch

private val BOOK_BOOKMARKS = listOf(
    "Cover (p.1)" to 1,
    "Intro (p.5)" to 5,
    "Lexicon A (p.11)" to 11,
    "Lexicon M (p.29)" to 29,
    "Analytical (p.47)" to 47
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrScannerScreen(
    viewModel: MainViewModel,
    initialBookPage: Int?,
    onClearInitialPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val tokens = LocalGlassTokens.current

    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeSourceType by remember { mutableStateOf("BOOK_PDF") }
    var activePageOneBased by remember { mutableIntStateOf(initialBookPage ?: 11) }
    var totalPdfPages by remember { mutableIntStateOf(214) }
    var externalPdfUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var editableTitle by remember { mutableStateOf("") }
    var showRawText by remember { mutableStateOf(false) }

    fun loadBookPage(pageOneBased: Int) {
        scope.launch {
            localError = null
            PdfBitmapHelper.renderAssetPdfPage(context, (pageOneBased - 1).coerceAtLeast(0))
                .fold(
                    onSuccess = { rendered ->
                        activeBitmap = rendered.bitmap
                        activeSourceType = "BOOK_PDF"
                        activePageOneBased = rendered.pageIndex + 1
                        totalPdfPages = rendered.totalPages
                        externalPdfUri = null
                    },
                    onFailure = { err ->
                        localError = err.localizedMessage ?: "Failed to render PDF page."
                    }
                )
        }
    }

    fun loadExternalPdfPage(uri: Uri, pageOneBased: Int) {
        scope.launch {
            localError = null
            PdfBitmapHelper.renderUriPdfPage(context, uri, (pageOneBased - 1).coerceAtLeast(0))
                .fold(
                    onSuccess = { rendered ->
                        activeBitmap = rendered.bitmap
                        activeSourceType = "PDF_PAGE"
                        activePageOneBased = rendered.pageIndex + 1
                        totalPdfPages = rendered.totalPages
                        externalPdfUri = uri
                    },
                    onFailure = { err ->
                        localError = err.localizedMessage ?: "Failed to render selected PDF."
                    }
                )
        }
    }

    LaunchedEffect(initialBookPage) {
        if (initialBookPage != null) {
            loadBookPage(initialBookPage)
            onClearInitialPage()
        } else if (activeBitmap == null) {
            loadBookPage(11)
        }
    }

    LaunchedEffect(ocrState) {
        if (ocrState is OcrProcessState.Ready) {
            editableTitle = (ocrState as OcrProcessState.Ready).extraction.suggestedTitle
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                localError = null
                PdfBitmapHelper.decodeUriBitmap(context, uri).fold(
                    onSuccess = { bmp ->
                        activeBitmap = bmp
                        activeSourceType = "IMAGE"
                        activePageOneBased = 1
                        externalPdfUri = null
                        viewModel.runOcrExtraction(
                            bitmap = bmp,
                            sourceType = "IMAGE",
                            sourcePage = 1,
                            sourceHint = "Uploaded document image for Tamazight/French/Arabic/English OCR"
                        )
                    },
                    onFailure = { err ->
                        localError = err.localizedMessage ?: "Unable to open selected image."
                    }
                )
            }
        }
    }

    val pdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadExternalPdfPage(uri, 1)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            scope.launch {
                localError = null
                PdfBitmapHelper.decodeUriBitmap(context, uri).fold(
                    onSuccess = { bmp ->
                        activeBitmap = bmp
                        activeSourceType = "CAMERA"
                        activePageOneBased = 1
                        externalPdfUri = null
                        viewModel.runOcrExtraction(
                            bitmap = bmp,
                            sourceType = "CAMERA",
                            sourcePage = 1,
                            sourceHint = "Camera photo of dictionary/historical document"
                        )
                    },
                    onFailure = { err ->
                        localError = err.localizedMessage ?: "Failed to decode captured photo."
                    }
                )
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = PdfBitmapHelper.createCameraCaptureFile(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraTempUri = uri
            takePictureLauncher.launch(uri)
        } else {
            localError = context.getString(R.string.camera_permission_rationale)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            // Input Sources Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevated = true,
                    contentPadding = 16.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "OCR Document & PDF Scanner",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Capture a page, open any PDF/image, or scan the bundled Tasniremt PDF to extract structured multilingual fields and sync to Cloud Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GlassPillChip(
                                label = "Camera OCR",
                                selected = activeSourceType == "CAMERA",
                                leadingIcon = Icons.Rounded.CameraAlt,
                                onClick = {
                                    val hasCam = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasCam) {
                                        val file = PdfBitmapHelper.createCameraCaptureFile(context)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        cameraTempUri = uri
                                        takePictureLauncher.launch(uri)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.testTag("ocr_camera_button")
                            )

                            GlassPillChip(
                                label = "Pick Image",
                                selected = activeSourceType == "IMAGE",
                                leadingIcon = Icons.Rounded.PhotoLibrary,
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("ocr_pick_image_button")
                            )

                            GlassPillChip(
                                label = "Open PDF",
                                selected = activeSourceType == "PDF_PAGE",
                                leadingIcon = Icons.Rounded.PictureAsPdf,
                                onClick = { pdfDocumentLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.testTag("ocr_open_pdf_button")
                            )

                            GlassPillChip(
                                label = "Tasniremt PDF",
                                selected = activeSourceType == "BOOK_PDF",
                                leadingIcon = Icons.Rounded.MenuBook,
                                onClick = { loadBookPage(activePageOneBased) },
                                modifier = Modifier.testTag("ocr_book_pdf_button")
                            )
                        }

                        if (activeSourceType == "BOOK_PDF" || activeSourceType == "PDF_PAGE") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val prev = (activePageOneBased - 1).coerceAtLeast(1)
                                        val ext = externalPdfUri
                                        if (ext != null) loadExternalPdfPage(ext, prev) else loadBookPage(prev)
                                    },
                                    enabled = activePageOneBased > 1,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                        contentDescription = "Previous PDF page"
                                    )
                                }

                                Text(
                                    text = "Page $activePageOneBased of $totalPdfPages",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                IconButton(
                                    onClick = {
                                        val next = (activePageOneBased + 1).coerceAtMost(totalPdfPages)
                                        val ext = externalPdfUri
                                        if (ext != null) loadExternalPdfPage(ext, next) else loadBookPage(next)
                                    },
                                    enabled = activePageOneBased < totalPdfPages,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Next PDF page"
                                    )
                                }
                            }

                            if (activeSourceType == "BOOK_PDF") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(BOOK_BOOKMARKS) { (label, page) ->
                                        GlassPillChip(
                                            label = label,
                                            selected = activePageOneBased == page,
                                            onClick = { loadBookPage(page) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!localError.isNullOrBlank()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = localError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Preview + Run OCR Action
            val previewBmp = activeBitmap
            if (previewBmp != null) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Document page preview for OCR",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val isProcessing = ocrState is OcrProcessState.Processing
                            Button(
                                onClick = {
                                    viewModel.runOcrExtraction(
                                        bitmap = previewBmp,
                                        sourceType = activeSourceType,
                                        sourcePage = activePageOneBased,
                                        sourceHint = "Page $activePageOneBased ($activeSourceType) of Tasniremt Tamazight-French Historical Lexicon"
                                    )
                                },
                                enabled = !isProcessing,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("run_ocr_extract_button")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text((ocrState as OcrProcessState.Processing).statusLabel)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.DocumentScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Parse Document Fields with OCR")
                                }
                            }
                        }
                    }
                }
            }

            // OCR Results & Cloud Sync
            when (val state = ocrState) {
                is OcrProcessState.Error -> {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                is OcrProcessState.Ready -> {
                    item {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            elevated = true,
                            contentPadding = 16.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Parsed ${state.extraction.terms.size} Terms",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.arabicAccent
                                    )
                                    if (state.savedDocId != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Synced",
                                                tint = tokens.arabicAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Synced to Cloud",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = tokens.arabicAccent
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = editableTitle,
                                    onValueChange = { editableTitle = it },
                                    label = { Text("Cloud Document Title") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ocr_doc_title_input")
                                )

                                if (state.extraction.summaryNote.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = state.extraction.summaryNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.syncCurrentOcrToCloud(editableTitle) },
                                        enabled = state.savedDocId == null,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("ocr_sync_cloud_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudUpload,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (state.savedDocId == null) "Sync to Cloud" else "Saved")
                                    }

                                    OutlinedButton(
                                        onClick = { showRawText = !showRawText },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text(if (showRawText) "Hide Raw OCR" else "Raw OCR")
                                    }
                                }

                                AnimatedVisibility(visible = showRawText) {
                                    Text(
                                        text = state.extraction.rawOcrText.ifBlank { "No raw text captured." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(top = 12.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                            .padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    items(state.extraction.terms) { term ->
                        ParsedOcrTermCard(term = term)
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun ParsedOcrTermCard(term: ParsedOcrTermDraft) {
    val tokens = LocalGlassTokens.current
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = term.tamazightTerm.ifBlank { term.frenchTerm },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.tamazightAccent,
                    modifier = Modifier.weight(1f)
                )
                if (term.dialectTags.isNotBlank()) {
                    LanguageBadge(term.dialectTags, MaterialTheme.colorScheme.primary)
                }
            }
            if (term.tifinagh.isNotBlank()) {
                Text(
                    text = term.tifinagh,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (term.tamazightPlural.isNotBlank()) {
                Text(
                    text = "Pl.: ${term.tamazightPlural}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageBadge("FR", tokens.frenchAccent)
                Text(term.frenchTerm, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageBadge("EN", tokens.englishAccent)
                Text(term.englishMeaning, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageBadge("AR", tokens.arabicAccent)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        text = term.arabicMeaning,
                        style = TextStyle(fontFamily = ArabicNotoFamily, fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = tokens.arabicAccent
                    )
                }
            }
            if (term.etymologyNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = term.etymologyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
