package com.example.camscan

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.camscan.core.camera.ScannerLauncher
import com.example.camscan.core.storage.StorageHelper
import com.example.camscan.data.repository.DocumentRepository
import com.example.camscan.theme.CamScanTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var documentRepository: DocumentRepository

    // Activity result launcher for ML Kit scanner
    val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.let { res ->
                lifecycleScope.launch(Dispatchers.IO) {
                    processScanResult(res)
                }
            }
        }
    }

    // Permission launcher for Bulk Import (API < 33)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            runBulkImport()
        } else {
            Toast.makeText(this, "Storage permission is required for bulk import.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamScanTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    fun startScan() {
        val scanner = ScannerLauncher.getScannerClient(this)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                try {
                    scannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Toast.makeText(this, "Failed to launch scanner UI: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to initialize scanner: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private suspend fun processScanResult(result: GmsDocumentScanningResult) {
        val timestamp = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        val docTitle = "Scan $dateStr"
        
        // 1. Copy PDF if available
        var localPdfPath: String? = null
        result.pdf?.let { pdf ->
            val pdfFile = StorageHelper.copyUriToInternalStorage(this, pdf.uri, "Scan_${timestamp}.pdf")
            localPdfPath = pdfFile?.absolutePath
        }

        // 2. Copy page images
        val localImagePaths = mutableListOf<String>()
        result.pages?.forEachIndexed { index, page ->
            val imgFile = StorageHelper.copyUriToInternalStorage(this, page.imageUri, "Page_${timestamp}_$index.jpg")
            imgFile?.let { localImagePaths.add(it.absolutePath) }
        }

        if (localImagePaths.isNotEmpty()) {
            val docId = documentRepository.createDocument(docTitle, localImagePaths, localPdfPath)
            documentRepository.runOcrOnDocument(this@MainActivity, docId)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Scan saved successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerAutomaticImport() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                runBulkImport()
            } else {
                requestPermissionLauncher.launch(permission)
            }
        } else {
            // Android 13+ queries MediaStore which does not require READ_EXTERNAL_STORAGE for app-specific or media files in standard collections
            runBulkImport()
        }
    }

    private fun runBulkImport() {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Searching for CameraScan PDFs...", Toast.LENGTH_SHORT).show()
            }
            val imported = StorageHelper.scanAndImportCameraScanPdfs(this@MainActivity)
            var count = 0
            for (res in imported) {
                if (res.pageImagePaths.isNotEmpty()) {
                    val docId = documentRepository.createDocument(res.title, res.pageImagePaths, res.pdfPath)
                    documentRepository.runOcrOnDocument(this@MainActivity, docId)
                    count++
                }
            }
            withContext(Dispatchers.Main) {
                if (count > 0) {
                    Toast.makeText(this@MainActivity, "Successfully imported $count document(s)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "No new CameraScan PDFs found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
