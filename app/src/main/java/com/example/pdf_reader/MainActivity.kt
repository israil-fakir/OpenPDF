package com.example.pdf_reader

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.util.Consumer
import com.example.pdf_reader.data.RecentPdfStore
import com.example.pdf_reader.ui.HomeScreen
import com.example.pdf_reader.ui.PdfViewerScreen
import com.example.pdf_reader.ui.SettingsScreen
import com.example.pdf_reader.ui.theme.PDF_readerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf<Boolean?>(null) }
            val systemDarkTheme = isSystemInDarkTheme()
            val currentDarkTheme = isDarkTheme ?: systemDarkTheme

            PDF_readerTheme(darkTheme = currentDarkTheme) {
                PdfReaderApp(
                    initialIntent = intent,
                    isDarkTheme = currentDarkTheme,
                    onToggleDarkTheme = {
                        isDarkTheme = !currentDarkTheme
                    },
                )
            }
        }
    }
}

@Composable
fun PdfReaderApp(
    initialIntent: Intent?,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
) {
    val activity = LocalContext.current as? ComponentActivity
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // List of recent files loaded from SharedPreferences
    var recentPdfs by remember {
        mutableStateOf(RecentPdfStore.getRecentPdfs(context))
    }

    // Navigation state
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // Currently opened PDF URI and name preserved across configuration/activity changes
    var activePdfUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var activePdfName by rememberSaveable { mutableStateOf<String?>(null) }
    var activePdfInitialPage by rememberSaveable { mutableIntStateOf(0) }

    // Function to open a PDF Uri and save it to recents
    fun openPdfUri(uri: Uri) {
        // Persist read permission if supported
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // Content provider might not support persistable permissions
        }

        val fileMeta = getFileMetadata(context, uri)

        coroutineScope.launch {
            RecentPdfStore.addOrUpdateRecentPdf(
                context = context,
                uri = uri,
                name = fileMeta.name,
                sizeBytes = fileMeta.sizeBytes,
                page = 0,
            )
            recentPdfs = RecentPdfStore.getRecentPdfs(context)
        }

        activePdfUriString = uri.toString()
        activePdfName = fileMeta.name
        activePdfInitialPage = 0
        isSettingsOpen = false
    }

    // Handle initial incoming Intent from "Open with" when Activity starts
    LaunchedEffect(initialIntent) {
        val uri = extractPdfUri(initialIntent)
        if (uri != null) {
            openPdfUri(uri)
        }
    }

    // Handle new incoming Intents when app is already running in background
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { newIntent ->
            val uri = extractPdfUri(newIntent)
            if (uri != null) {
                openPdfUri(uri)
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    // Launcher for selecting PDF documents from storage inside the app
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            openPdfUri(uri)
        }
    }

    val currentUriString = activePdfUriString
    val currentName = activePdfName

    when {
        currentUriString != null && currentName != null -> {
            // PDF Viewer Screen with Back, Title, Search, and Rotation
            PdfViewerScreen(
                pdfUri = currentUriString.toUri(),
                pdfName = currentName,
                initialPage = activePdfInitialPage,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onBackClick = {
                    // Return to Home Screen and refresh recent files
                    activePdfUriString = null
                    activePdfName = null
                    activePdfInitialPage = 0
                    recentPdfs = RecentPdfStore.getRecentPdfs(context)
                },
            )
        }
        isSettingsOpen -> {
            // Settings Screen
            SettingsScreen(
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onClearRecentFiles = {
                    RecentPdfStore.clearAllRecentPdfs(context)
                    recentPdfs = emptyList()
                },
                onBackClick = {
                    isSettingsOpen = false
                },
            )
        }
        else -> {
            // Home Screen with Recent PDFs Grid
            HomeScreen(
                recentPdfs = recentPdfs,
                onOpenPdfClick = {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                },
                onSettingsClick = {
                    isSettingsOpen = true
                },
                onPdfSelected = { selected ->
                    activePdfUriString = selected.uriString
                    activePdfName = selected.name
                    activePdfInitialPage = selected.page
                },
                onRemoveRecentPdf = { toRemove ->
                    RecentPdfStore.removeRecentPdf(context, toRemove.uriString)
                    recentPdfs = RecentPdfStore.getRecentPdfs(context)
                },
            )
        }
    }
}

/**
 * Extracts a valid PDF [Uri] from an incoming [Intent] (e.g. from ACTION_VIEW).
 */
private fun extractPdfUri(intent: Intent?): Uri? {
    if (intent == null) return null
    val action = intent.action
    if (Intent.ACTION_VIEW == action) {
        val dataUri = intent.data
        if (dataUri != null) {
            return dataUri
        }
    }
    return null
}

data class FileMetadata(
    val name: String,
    val sizeBytes: Long,
)

/**
 * Retrieves the display name and file size of a document from its content [Uri].
 */
fun getFileMetadata(context: Context, uri: Uri): FileMetadata {
    var fileName: String? = null
    var sizeBytes: Long = 0L

    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
                if (sizeIndex != -1) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    val fallbackName = fileName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
    return FileMetadata(fallbackName, sizeBytes)
}
