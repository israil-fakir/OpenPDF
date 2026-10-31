package com.example.pdf_reader.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pdf_reader.data.RecentPdfStore
import com.github.barteksc.pdfviewer.PDFView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    pdfName: String,
    initialPage: Int,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current

    // Detect if screen is currently in landscape mode
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Reset orientation back to default when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Persist current page across configuration changes or process death
    var currentPage by rememberSaveable { mutableIntStateOf(initialPage) }
    var totalPages by remember { mutableIntStateOf(0) }

    // Search state
    var isSearchActive by rememberSaveable { mutableStateOf(value = false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Page Jump Dialog state
    var showJumpDialog by rememberSaveable { mutableStateOf(value = false) }

    // Reference to PDFView for programmatic page jumps
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    // Controls TopAppBar and FAB visibility in read mode (single-tap toggles)
    var isTopBarVisible by remember { mutableStateOf(value = true) }

    // Handles hardware/gesture back press
    BackHandler {
        if (showJumpDialog) {
            showJumpDialog = false
        } else if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        } else {
            onBackClick()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AnimatedVisibility(
                visible = isTopBarVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (isSearchActive) {
                    // Active Search / Page Jump Bar
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close search",
                                )
                            }
                        },
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    val pageNum = query.trim().toIntOrNull()
                                    if ((pageNum != null) && (totalPages > 0)) {
                                        val targetPage = (pageNum - 1).coerceIn(0, totalPages - 1)
                                        pdfViewRef?.jumpTo(targetPage)
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = if (totalPages > 0) {
                                            "Jump to page (1-$totalPages)..."
                                        } else {
                                            "Search..."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                } else {
                    // Standard Top App Bar
                    TopAppBar(
                        navigationIcon = {
                            // Back Button
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                        title = {
                            // File Name & Page Count
                            Column {
                                Text(
                                    text = pdfName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val pageInfo = if (totalPages > 0) {
                                    "Page ${currentPage + 1} of $totalPages"
                                } else {
                                    "PDF Document"
                                }
                                Text(
                                    text = pageInfo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            // 1. Search Icon
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                )
                            }

                            // 2. Rotation Button (Toggles between Portrait and Landscape)
                            IconButton(
                                onClick = {
                                    activity?.let { act ->
                                        act.requestedOrientation = if (isLandscape) {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = if (isLandscape) {
                                        Icons.Default.ScreenLockLandscape
                                    } else {
                                        Icons.Default.ScreenLockPortrait
                                    },
                                    contentDescription = if (isLandscape) {
                                        "Switch to Portrait"
                                    } else {
                                        "Switch to Landscape"
                                    },
                                )
                            }

                            // 3. Dark / Light Theme Toggle Icon
                            IconButton(onClick = onToggleDarkTheme) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            // Floating fast-scroll button positioned on the right side
            AnimatedVisibility(
                visible = isTopBarVisible && totalPages > 0,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = { showJumpDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Fast-scroll page jump",
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentPage + 1}/$totalPages",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PDFView(ctx, null).also { view ->
                        pdfViewRef = view
                    }
                },
                update = { pdfView ->
                    pdfViewRef = pdfView
                    pdfView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    pdfView.fromUri(pdfUri)
                        .defaultPage(if (pdfView.pageCount > 0) pdfView.currentPage else currentPage)
                        .enableSwipe(true)
                        .swipeHorizontal(false)
                        .enableDoubletap(true)
                        .enableAntialiasing(true)
                        .spacing(10)
                        .nightMode(isDarkTheme)
                        .onPageChange { page, count ->
                            currentPage = page
                            totalPages = count
                            RecentPdfStore.updateCurrentPage(context, pdfUri, page)
                        }
                        .onTap {
                            // Single tap toggles TopAppBar and FAB visibility in read mode
                            isTopBarVisible = !isTopBarVisible
                            true
                        }
                        .onError {
                            // If file cannot be read, remove from recents and exit viewer
                            RecentPdfStore.removeRecentPdf(context, pdfUri.toString())
                            onBackClick()
                        }
                        .load()
                },
            )
        }
    }

    // Material 3 Page Jump Dialog
    if (showJumpDialog) {
        PageJumpDialog(
            currentPage = currentPage + 1,
            totalPages = totalPages,
            onDismiss = { showJumpDialog = false },
            onJumpToPage = { targetPageNumber ->
                val targetPageIndex = (targetPageNumber - 1).coerceIn(0, totalPages - 1)
                pdfViewRef?.jumpTo(targetPageIndex)
                showJumpDialog = false
            },
        )
    }
}

/**
 * Material 3 Dialog for fast-scroll page navigation with input validation.
 */
@Composable
private fun PageJumpDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
) {
    var inputPageText by rememberSaveable { mutableStateOf(currentPage.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun validateAndJump() {
        val parsedPage = inputPageText.trim().toIntOrNull()
        when {
            parsedPage == null -> {
                errorMessage = "Please enter a valid page number"
            }
            parsedPage !in 1..totalPages -> {
                errorMessage = "Enter a page between 1 and $totalPages"
            }
            else -> {
                errorMessage = null
                onJumpToPage(parsedPage)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Pin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "Go to Page",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = "Total pages: $totalPages (Currently on page $currentPage)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = inputPageText,
                    onValueChange = { newText ->
                        // Allow digits only
                        if (newText.all { it.isDigit() }) {
                            inputPageText = newText
                            errorMessage = null
                        }
                    },
                    label = { Text("Page Number (1 - $totalPages)") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { validateAndJump() },
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { validateAndJump() },
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
