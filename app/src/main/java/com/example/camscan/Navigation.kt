package com.example.camscan

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.camscan.ui.library.LibraryScreen
import com.example.camscan.ui.viewer.DocumentViewerScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Library)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Library> {
                LibraryScreen(
                    onDocumentClick = { docId ->
                        backStack.add(Viewer(docId))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<Viewer> { navKey ->
                DocumentViewerScreen(
                    documentId = navKey.documentId,
                    onBackClick = {
                        backStack.removeLastOrNull()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
    )
}
