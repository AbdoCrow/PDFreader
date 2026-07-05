package com.example.camscan.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.camscan.MainActivity
import com.example.camscan.data.local.DocumentWithPages
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onDocumentClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    
    val documents by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("CamScan Library", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { activity?.triggerAutomaticImport() }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Import CameraScan PDFs"
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { activity?.startScan() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Start Scan")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search by title or OCR content...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "No documents yet" else "No matches found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) 
                                "Tap + to scan, or tap the download button above to auto-import PDFs." 
                                else "Try refining your search query.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(documents, key = { it.document.id }) { doc ->
                        DocumentCard(
                            documentWithPages = doc,
                            onClick = { onDocumentClick(doc.document.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentCard(
    documentWithPages: DocumentWithPages,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstPage = documentWithPages.pages.minByOrNull { it.position }
    val formattedDate = remember(documentWithPages.document.updatedAt) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(documentWithPages.document.updatedAt))
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (firstPage != null && File(firstPage.imagePath).exists()) {
                    AsyncImage(
                        model = firstPage.imagePath,
                        contentDescription = "Document Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
            
            // Text Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = documentWithPages.document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${documentWithPages.pages.size} pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
