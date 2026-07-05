package com.example.camscan.ui.viewer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.camscan.data.local.PageEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    documentId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Load the document when this screen is opened
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }
    
    val documentWithPages by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var expandedPageIdForOcr by remember { mutableStateOf<Long?>(null) }
    
    // Sync rename text when dialog opens
    LaunchedEffect(showRenameDialog) {
        if (showRenameDialog) {
            renameText = documentWithPages?.document?.title ?: ""
        }
    }
    
    val doc = documentWithPages
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = doc?.document?.title ?: "Viewer",
                        maxLines = 1,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val pdfPath = doc?.document?.pdfPath
                        if (pdfPath != null) {
                            sharePdf(context, pdfPath)
                        } else {
                            Toast.makeText(context, "PDF file is not available.", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share PDF")
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Document", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (doc == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val pages = remember(doc.pages) { doc.pages.sortedBy { it.position } }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (pages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This document has no pages.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(pages, key = { _, page -> page.id }) { index, page ->
                            PageItemCard(
                                page = page,
                                pageIndex = index,
                                totalPages = pages.size,
                                ocrExpanded = expandedPageIdForOcr == page.id,
                                onOcrToggle = {
                                    expandedPageIdForOcr = if (expandedPageIdForOcr == page.id) null else page.id
                                },
                                onCopyOcr = { ocrText ->
                                    clipboardManager.setText(AnnotatedString(ocrText))
                                    Toast.makeText(context, "Copied OCR text to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onMoveUp = { viewModel.movePageUp(page) },
                                onMoveDown = { viewModel.movePageDown(page) },
                                onDelete = { viewModel.deletePage(page) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.renameDocument(renameText)
                        showRenameDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Document Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to delete this document and all its scanned pages? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument()
                        showDeleteDialog = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PageItemCard(
    page: PageEntity,
    pageIndex: Int,
    totalPages: Int,
    ocrExpanded: Boolean,
    onOcrToggle: () -> Unit,
    onCopyOcr: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page image preview
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (File(page.imagePath).exists()) {
                        AsyncImage(
                            model = page.imagePath,
                            contentDescription = "Page Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = "Missing File",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Page Info and Reordering
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Page ${pageIndex + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = pageIndex > 0
                        ) {
                            Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = "Move Up")
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = pageIndex < totalPages - 1
                        ) {
                            Icon(imageVector = Icons.Default.ArrowDownward, contentDescription = "Move Down")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Page",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // OCR Text Section
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOcrToggle),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recognized Text (OCR)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (ocrExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                    
                    AnimatedVisibility(visible = ocrExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            val textToShow = if (page.ocrText.isNullOrBlank()) {
                                "OCR is processing or no text was recognized on this page."
                            } else {
                                page.ocrText
                            }
                            Text(
                                text = textToShow,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!page.ocrText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { onCopyOcr(page.ocrText) },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Text")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sharePdf(context: Context, pdfPath: String) {
    val file = File(pdfPath)
    if (!file.exists()) {
        Toast.makeText(context, "PDF file not found.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "com.example.camscan.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF Document"))
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }
}
