package com.example.camscan.data.repository

import com.example.camscan.data.local.DocumentDao
import com.example.camscan.data.local.DocumentEntity
import com.example.camscan.data.local.DocumentWithPages
import com.example.camscan.data.local.PageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDocumentRepository @Inject constructor(
    private val documentDao: DocumentDao
) : DocumentRepository {

    override fun getDocuments(): Flow<List<DocumentWithPages>> =
        documentDao.getDocumentsFlow()

    override fun getDocumentById(id: Long): Flow<DocumentWithPages?> =
        documentDao.getDocumentByIdFlow(id)

    override suspend fun getDocumentByIdDirect(id: Long): DocumentWithPages? =
        documentDao.getDocumentById(id)

    override suspend fun createDocument(title: String, imagePaths: List<String>, pdfPath: String?): Long {
        val now = System.currentTimeMillis()
        val document = DocumentEntity(
            title = title,
            createdAt = now,
            updatedAt = now,
            pdfPath = pdfPath
        )
        val documentId = documentDao.insertDocument(document)
        val pages = imagePaths.mapIndexed { index, path ->
            PageEntity(
                documentId = documentId,
                position = index,
                imagePath = path
            )
        }
        documentDao.insertPages(pages)
        return documentId
    }

    override suspend fun addPagesToDocument(documentId: Long, imagePaths: List<String>) {
        val existingDoc = documentDao.getDocumentById(documentId) ?: return
        val currentMaxPosition = existingDoc.pages.maxOfOrNull { it.position } ?: -1
        val now = System.currentTimeMillis()
        
        val newPages = imagePaths.mapIndexed { index, path ->
            PageEntity(
                documentId = documentId,
                position = currentMaxPosition + 1 + index,
                imagePath = path
            )
        }
        documentDao.insertPages(newPages)
        
        // Update document's updatedAt timestamp
        documentDao.updateDocument(existingDoc.document.copy(updatedAt = now))
    }

    override suspend fun updateDocument(document: DocumentEntity) {
        documentDao.updateDocument(document.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteDocument(document: DocumentEntity) {
        // Delete all page image files from storage
        val docWithPages = documentDao.getDocumentById(document.id)
        docWithPages?.pages?.forEach { page ->
            try {
                File(page.imagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        document.pdfPath?.let { pdfPath ->
            try {
                File(pdfPath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        documentDao.deleteDocument(document)
    }

    override suspend fun updatePage(page: PageEntity) {
        documentDao.updatePage(page)
        // Also touch document's updatedAt
        val doc = documentDao.getDocumentById(page.documentId)
        doc?.let {
            documentDao.updateDocument(it.document.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun deletePage(page: PageEntity) {
        // Delete file from disk
        try {
            File(page.imagePath).delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        documentDao.deletePage(page)
        
        // Adjust positions of remaining pages
        val doc = documentDao.getDocumentById(page.documentId)
        doc?.let { documentWithPages ->
            val remainingPagesSorted = documentWithPages.pages.sortedBy { it.position }
            remainingPagesSorted.forEachIndexed { index, remainingPage ->
                if (remainingPage.position != index) {
                    documentDao.updatePage(remainingPage.copy(position = index))
                }
            }
            documentDao.updateDocument(documentWithPages.document.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun reorderPages(documentId: Long, pages: List<PageEntity>) {
        pages.forEachIndexed { index, pageEntity ->
            documentDao.updatePage(pageEntity.copy(position = index))
        }
        val doc = documentDao.getDocumentById(documentId)
        doc?.let {
            documentDao.updateDocument(it.document.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun runOcrOnDocument(context: android.content.Context, documentId: Long) {
        val docWithPages = documentDao.getDocumentById(documentId) ?: return
        val ocrResults = mutableListOf<com.google.mlkit.vision.text.Text?>()
        val sortedPages = docWithPages.pages.sortedBy { it.position }
        val imagePaths = sortedPages.map { it.imagePath }
        
        for (page in sortedPages) {
            val resultText = com.example.camscan.core.ocr.OcrManager.recognizeText(context, page.imagePath)
            ocrResults.add(resultText)
            
            resultText?.let {
                documentDao.updatePage(page.copy(ocrText = it.text))
            }
        }
        
        val appDocsDir = java.io.File(context.filesDir, "scanned_documents")
        if (!appDocsDir.exists()) appDocsDir.mkdirs()
        val pdfFile = java.io.File(appDocsDir, "Searchable_${documentId}_${System.currentTimeMillis()}.pdf")
        
        val success = com.example.camscan.core.pdf.PdfExporter.generateSearchablePdf(
            context,
            imagePaths,
            ocrResults,
            pdfFile
        )
        
        if (success) {
            docWithPages.document.pdfPath?.let { oldPath ->
                try {
                    java.io.File(oldPath).delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            documentDao.updateDocument(docWithPages.document.copy(pdfPath = pdfFile.absolutePath, updatedAt = System.currentTimeMillis()))
        }
    }

    override fun searchDocuments(query: String): Flow<List<DocumentWithPages>> =
        documentDao.searchDocuments(query)
}
