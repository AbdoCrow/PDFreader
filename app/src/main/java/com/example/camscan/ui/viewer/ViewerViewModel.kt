package com.example.camscan.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camscan.data.local.DocumentEntity
import com.example.camscan.data.local.DocumentWithPages
import com.example.camscan.data.local.PageEntity
import com.example.camscan.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    private val documentIdFlow = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DocumentWithPages?> = documentIdFlow
        .flatMapLatest { id ->
            if (id != null) {
                repository.getDocumentById(id)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun loadDocument(id: Long) {
        documentIdFlow.value = id
    }

    fun renameDocument(title: String) {
        val currentDoc = uiState.value?.document ?: return
        viewModelScope.launch {
            repository.updateDocument(currentDoc.copy(title = title))
        }
    }

    fun deleteDocument() {
        val currentDoc = uiState.value?.document ?: return
        viewModelScope.launch {
            repository.deleteDocument(currentDoc)
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }

    fun movePageUp(page: PageEntity) {
        val doc = uiState.value ?: return
        val pages = doc.pages.sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == page.id }
        if (index > 0) {
            val temp = pages[index]
            pages[index] = pages[index - 1]
            pages[index - 1] = temp
            viewModelScope.launch {
                repository.reorderPages(doc.document.id, pages)
            }
        }
    }

    fun movePageDown(page: PageEntity) {
        val doc = uiState.value ?: return
        val pages = doc.pages.sortedBy { it.position }.toMutableList()
        val index = pages.indexOfFirst { it.id == page.id }
        if (index != -1 && index < pages.size - 1) {
            val temp = pages[index]
            pages[index] = pages[index + 1]
            pages[index + 1] = temp
            viewModelScope.launch {
                repository.reorderPages(doc.document.id, pages)
            }
        }
    }
}
