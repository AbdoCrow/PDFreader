package com.example.camscan.core.pdf

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.google.mlkit.vision.text.Text
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun generateSearchablePdf(
        context: Context,
        imagePaths: List<String>,
        ocrTexts: List<Text?>,
        outputFile: File
    ): Boolean {
        val pdfDocument = PdfDocument()
        try {
            imagePaths.forEachIndexed { index, imgPath ->
                val imgFile = File(imgPath)
                if (!imgFile.exists()) return@forEachIndexed
                
                // Load bitmap
                val bitmap = BitmapFactory.decodeFile(imgPath) ?: return@forEachIndexed
                
                // Create PDF page with same size as bitmap
                val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                
                val canvas = page.canvas
                // Draw page image
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                
                // Draw invisible OCR text layer if available
                val ocrText = ocrTexts.getOrNull(index)
                if (ocrText != null) {
                    val paint = Paint().apply {
                        color = Color.TRANSPARENT
                        style = Paint.Style.FILL
                    }
                    
                    ocrText.textBlocks.forEach { block ->
                         block.lines.forEach { line ->
                             val box = line.boundingBox
                             if (box != null) {
                                 // Set text size to height of bounding box
                                 paint.textSize = box.height().toFloat() * 0.85f
                                 // Draw text at baseline (bottom-left of box)
                                 canvas.drawText(line.text, box.left.toFloat(), box.bottom.toFloat(), paint)
                             }
                         }
                    }
                }
                
                pdfDocument.finishPage(page)
                bitmap.recycle()
            }
            
            // Save to file
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            pdfDocument.close()
        }
    }
}
