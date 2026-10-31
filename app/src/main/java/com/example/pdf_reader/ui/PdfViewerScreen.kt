package com.example.pdf_reader.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pdf_reader.data.RecentPdfStore
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // Fast-scroll progress state (0.0f to 1.0f)
    var scrollProgress by remember { mutableFloatStateOf(0f) }
    var isDraggingFastScroll by remember { mutableStateOf(false) }
    var isUserInteracting by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    // Page Jump Dialog state
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }

    // Reference to PDFView for programmatic page jumps and scrubbing
    var pdfViewRef by remember { mutableStateOf<PDFView?>(null) }

    // Controls TopAppBar and handle visibility in read mode (single-tap toggles)
    var isTopBarVisible by remember { mutableStateOf(true) }

    // Auto-hide the fast scroll handle after inactivity if top bar is hidden
    LaunchedEffect(isUserInteracting) {
        if (isUserInteracting && !isDraggingFastScroll) {
            delay(2500L)
            isUserInteracting = false
        }
    }

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
    ) { innerPadding ->
        var containerHeightPx by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .onGloballyPositioned { coordinates ->
                    containerHeightPx = coordinates.size.height.toFloat()
                },
        ) {
            // PDF Viewer Core
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
                        .onLoad { count ->
                            totalPages = count
                            if (count > 1) {
                                scrollProgress = (currentPage.toFloat() / (count - 1).toFloat()).coerceIn(0f, 1f)
                            }
                        }
                        .onPageChange { page, count ->
                            currentPage = page
                            totalPages = count
                            if (!isDraggingFastScroll && count > 1) {
                                scrollProgress = (page.toFloat() / (count - 1).toFloat()).coerceIn(0f, 1f)
                            }
                            RecentPdfStore.updateCurrentPage(context, pdfUri, page)
                        }
                        .onPageScroll { page, _ ->
                            if (!isDraggingFastScroll) {
                                currentPage = page
                                scrollProgress = pdfView.positionOffset.coerceIn(0f, 1f)
                                isUserInteracting = true
                            }
                        }
                        .onTap {
                            // Single tap toggles TopAppBar and handle in read mode
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

            // Adobe-style Fast Scrolling Handle positioned on the right edge
            val isHandleVisible = totalPages > 1 && (isTopBarVisible || isDraggingFastScroll || isUserInteracting)

            AnimatedVisibility(
                visible = isHandleVisible,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                AdobeFastScrollHandle(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    scrollProgress = scrollProgress,
                    containerHeightPx = containerHeightPx,
                    isDragging = isDraggingFastScroll,
                    onStartDrag = {
                        isDraggingFastScroll = true
                        isUserInteracting = true
                    },
                    onDragProgress = { newProgress ->
                        scrollProgress = newProgress
                        pdfViewRef?.let { pv ->
                            pv.setPositionOffset(newProgress, false)
                            val targetPage = (newProgress * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                            currentPage = targetPage
                        }
                    },
                    onEndDrag = {
                        isDraggingFastScroll = false
                        pdfViewRef?.jumpTo(currentPage, false)
                        RecentPdfStore.updateCurrentPage(context, pdfUri, currentPage)
                    },
                    onTap = {
                        showJumpDialog = true
                    },
                )
            }
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
 * Adobe Acrobat-style fast scroll handle on the right edge of the screen:
 * - Single Tap: Opens Search/Jump Page Dialog
 * - Tap and Hold / Drag: Rapidly scrubs up to down and down to up through pages in real time
 */
@Composable
private fun AdobeFastScrollHandle(
    currentPage: Int,
    totalPages: Int,
    scrollProgress: Float,
    containerHeightPx: Float,
    isDragging: Boolean,
    onStartDrag: () -> Unit,
    onDragProgress: (Float) -> Unit,
    onEndDrag: () -> Unit,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val handleHeightDp = 38.dp
    val handleHeightPx = with(density) { handleHeightDp.toPx() }
    val topPaddingPx = with(density) { 16.dp.toPx() }
    val bottomPaddingPx = with(density) { 16.dp.toPx() }

    val availableTravelPx = (containerHeightPx - topPaddingPx - bottomPaddingPx - handleHeightPx).coerceAtLeast(1f)

    // Current vertical Y offset of the handle
    var currentHandleY by remember { mutableFloatStateOf(topPaddingPx) }

    // When not dragging, sync handle position with document scroll progress
    if (!isDragging) {
        currentHandleY = topPaddingPx + (scrollProgress * availableTravelPx).coerceIn(0f, availableTravelPx)
    }

    // Smoothly animate width and elevation when touched/held
    val handleWidth by animateDpAsState(
        targetValue = if (isDragging) 76.dp else 52.dp,
        label = "AdobeHandleWidth",
    )
    val handleElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        label = "AdobeHandleElevation",
    )

    Surface(
        modifier = Modifier
            .offset { IntOffset(x = 0, y = currentHandleY.roundToInt()) }
            .width(handleWidth)
            .height(handleHeightDp)
            .pointerInput(totalPages, availableTravelPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    val touchSlop = viewConfiguration.touchSlop
                    var accumulatedDragY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (change.changedToUp()) {
                            if (!isDrag) {
                                // Tap detected -> Open Search / Page Jump Dialog
                                onTap()
                            } else {
                                // Drag ended
                                onEndDrag()
                            }
                            break
                        }

                        val dragDeltaY = change.positionChange().y
                        accumulatedDragY += dragDeltaY

                        if (!isDrag && abs(accumulatedDragY) > touchSlop) {
                            isDrag = true
                            onStartDrag()
                        }

                        if (isDrag) {
                            change.consume()
                            // Update vertical handle position clamped within travel limits
                            currentHandleY = (currentHandleY + dragDeltaY).coerceIn(
                                topPaddingPx,
                                topPaddingPx + availableTravelPx,
                            )
                            val newProgress = ((currentHandleY - topPaddingPx) / availableTravelPx).coerceIn(0f, 1f)
                            onDragProgress(newProgress)
                        }
                    }
                }
            },
        shape = RoundedCornerShape(
            topStart = 19.dp,
            bottomStart = 19.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp,
        ),
        color = if (isDragging) Color(0xFF111111) else Color(0xFF212121),
        shadowElevation = handleElevation,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isDragging) "${currentPage + 1}/$totalPages" else (currentPage + 1).toString(),
                color = Color.White,
                fontSize = if (isDragging) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
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
