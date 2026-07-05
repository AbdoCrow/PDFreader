package com.example.camscan.data.repository

import com.example.camscan.data.local.DocumentEntity
import com.example.camscan.data.local.DocumentWithPages
import com.example.camscan.data.local.PageEntity
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getDocuments(): Flow<List<DocumentWithPages>>
    fun getDocumentById(id: Long): Flow<DocumentWithPages?>
    suspend fun getDocumentByIdDirect(id: Long): DocumentWithPages?
    suspend fun createDocument(title: String, imagePaths: List<String>, pdfPath: String? = null): Long
    suspend fun addPagesToDocument(documentId: Long, imagePaths: List<String>)
    suspend fun updateDocument(document: DocumentEntity)
    suspend fun deleteDocument(document: DocumentEntity)
    suspend fun updatePage(page: PageEntity)
    suspend fun deletePage(page: PageEntity)
    suspend fun reorderPages(documentId: Long, pages: List<PageEntity>)
    suspend fun runOcrOnDocument(context: android.content.Context, documentId: Long)
    fun searchDocuments(query: String): Flow<List<DocumentWithPages>>
}
