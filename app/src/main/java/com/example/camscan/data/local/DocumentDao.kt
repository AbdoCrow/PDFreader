package com.example.camscan.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Transaction
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getDocumentsFlow(): Flow<List<DocumentWithPages>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentByIdFlow(id: Long): Flow<DocumentWithPages?>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentWithPages?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: Long)

    @Transaction
    @Query("""
        SELECT DISTINCT d.* FROM documents d 
        LEFT JOIN pages p ON d.id = p.documentId 
        WHERE d.title LIKE '%' || :query || '%' OR p.ocrText LIKE '%' || :query || '%'
        ORDER BY d.updatedAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentWithPages>>
}
