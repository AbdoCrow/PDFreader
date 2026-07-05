package com.example.camscan.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.camscan.data.local.DocumentWithPages
import com.example.camscan.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: DocumentRepository
) : ViewModel() {

    val searchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<List<DocumentWithPages>> = searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getDocuments()
            } else {
                repository.searchDocuments(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
}
