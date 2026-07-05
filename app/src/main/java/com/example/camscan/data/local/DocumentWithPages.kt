package com.example.camscan.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class DocumentWithPages(
    @Embedded val document: DocumentEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "documentId"
    )
    val pages: List<PageEntity>
)
