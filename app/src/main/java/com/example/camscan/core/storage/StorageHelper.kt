package com.example.camscan.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

data class ImportedPdfResult(
    val title: String,
    val pdfPath: String,
    val lastModified: Long,
    val pageImagePaths: List<String>
)

object StorageHelper {

    fun copyUriToInternalStorage(context: Context, uri: Uri, fileName: String): File? {
        val appDocsDir = File(context.filesDir, "scanned_documents")
        if (!appDocsDir.exists()) appDocsDir.mkdirs()
        val destFile = File(appDocsDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractPagesFromPdf(context: Context, pdfFile: File, outputDirectory: File): List<String> {
        val imagePaths = mutableListOf<String>()
        try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fileDescriptor)
            val pageCount = renderer.pageCount
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                // Render the page to a bitmap (with high quality)
                val scale = 3.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                
                // Draw white background
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Save bitmap to disk
                val pageImageFile = File(outputDirectory, "${pdfFile.nameWithoutExtension}_page_${i + 1}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(pageImageFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                bitmap.recycle()
                
                imagePaths.add(pageImageFile.absolutePath)
            }
            renderer.close()
            fileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return imagePaths
    }

    fun scanAndImportCameraScanPdfs(context: Context): List<ImportedPdfResult> {
        val importedResults = mutableListOf<ImportedPdfResult>()
        val searchDirectories = listOf(
            File("/sdcard/CameraScan"),
            File("/sdcard/Documents/CameraScan"),
            File(Environment.getExternalStorageDirectory(), "CameraScan"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "CameraScan"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CameraScan")
        )
        
        val foundFiles = mutableSetOf<File>()
        
        // 1. Direct file listing (if permissions allow)
        for (dir in searchDirectories) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { _, name -> name.lowercase().endsWith(".pdf") }?.forEach {
                        foundFiles.add(it)
                    }
                }
            } catch (e: Exception) {
                // Ignore directory read exceptions (e.g. permission denied)
            }
        }
        
        // 2. MediaStore query (Android 10+ standard way)
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf")
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex)
                    if (path != null && (path.contains("CameraScan", ignoreCase = true) || path.contains("camera_scan", ignoreCase = true))) {
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            foundFiles.add(file)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val appDocsDir = File(context.filesDir, "scanned_documents")
        if (!appDocsDir.exists()) appDocsDir.mkdirs()
        
        for (file in foundFiles) {
            try {
                val destinationFile = File(appDocsDir, "${System.currentTimeMillis()}_${file.name}")
                // Copy file content
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // Extract pages as images
                val pageImages = extractPagesFromPdf(context, destinationFile, appDocsDir)
                
                importedResults.add(
                    ImportedPdfResult(
                        title = file.nameWithoutExtension,
                        pdfPath = destinationFile.absolutePath,
                        lastModified = file.lastModified(),
                        pageImagePaths = pageImages
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return importedResults
    }
}
