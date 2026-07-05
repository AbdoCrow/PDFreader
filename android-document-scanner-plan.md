# Android Document Scanner App — Full Development Plan
### A CamScanner-style app with zero cloud dependency
*Researched July 2026. Dependency versions move every few weeks — treat exact numbers below as "current as of this writing" and confirm against Android Studio / Google's docs at build time.*

---

## 1. Overview & assumptions

**What this app is:** a native Android app that captures paper documents, auto-detects and crops the page, cleans up the image, optionally runs on-device OCR, and exports multi-page PDFs — with no accounts, no sync, no server-side processing, and no `INTERNET` permission at all.

**Assumptions made to keep this plan concrete** (flag if any are wrong):
- Native Kotlin + Jetpack Compose, not Flutter/React Native — you asked specifically about "the android application."
- Solo or small-team project, not an enterprise codebase with existing infra.
- You want to ship to the Play Store, but might also care about F-Droid / direct-APK distribution given the "no cloud" framing — this plan covers both.

---

## 2. The one big decision: two viable paths

There are two legitimate ways to build the core scanning engine, and they trade off very differently. Most of this plan applies to either path — the difference is mainly in Section 8.1.

| | **Path A — ML Kit Document Scanner API** | **Path B — Custom CameraX + OpenCV** |
|---|---|---|
| What it is | Google's pre-built scanning UI (`play-services-mlkit-document-scanner`) — camera, edge detection, perspective correction, filters, multi-page, PDF export, all handled for you | You build the camera screen and image pipeline yourself with CameraX + OpenCV |
| Dev time for MVP | Days — one Activity Result contract | Weeks — this is the "hard part" of a scanner app |
| On-device / private | Yes — runs locally, no network call | Yes — runs locally |
| Truly zero-Google-dependency | No — requires Google Play Services | Yes — works on Play-Services-less devices (GrapheneOS, some China-market devices, F-Droid users) |
| UI customization | Limited — Google owns the scan screen's look | Full control over every pixel |
| Camera permission | Not required in your manifest — the scanner uses Play Services' own camera permission | You must request `CAMERA` yourself |
| Device requirement | Needs ~1.7GB+ RAM or it throws `MlKitException(UNSUPPORTED)` | Works on lower-end devices |
| APK size impact | ~300KB (models download via Play Services) | OpenCV adds several MB per ABI (trimmable) |

**Recommendation:** build Path A first. Real teams that have shipped this report it eliminates weeks of camera engineering and covers the vast majority of use cases — auto capture, edge detection, rotation correction, filters, stain/shadow cleanup, and multi-page PDF/JPEG export are all included out of the box.<cite index="4-1">After evaluating custom CameraX implementations, third-party SDKs, and ML Kit, one team chose ML Kit's Document Scanner API because it eliminated weeks of camera engineering while delivering professional-grade scanning with auto edge detection, perspective correction, and multi-page support</cite> Fall back to Path B only if you specifically need to run without Google Play Services (privacy-maximalist audience, F-Droid distribution) or need pixel-level control Google doesn't expose. Section 14 lists a real open-source app (MakeACopy) that did exactly this.

You can also do both: ship Path A first for speed, and swap in a Path B pipeline later behind the same repository interface — the architecture in Section 5 is designed so that swap doesn't touch the rest of the app.

---

## 3. Current technology landscape (research findings)

| Technology | Current state (July 2026) | Notes |
|---|---|---|
| Kotlin | 2.3.x / 2.4.0 stable | Pair the Compose compiler plugin version 1:1 with your Kotlin version; manage both via a version catalog |
| Jetpack Compose | BOM `2026.06.00`, core libs at 1.11 stable | Compose 1.12 (later in 2026) will require `compileSdk 37` + AGP 9 — worth knowing if you build over a long timeline |
| CameraX | 1.6.0 stable (March 2026) | Migrated to the CameraPipe stack (same one powering Pixel Camera); `camera-compose` is now stable for a `CameraXViewfinder` composable |
| ML Kit Document Scanner API | GA via `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0` | <cite index="2-1">Users can optionally crop scanned documents, apply filters, remove shadows or stains, and send the digitized files back to the app; the UI flow, ML models, and other large resources are delivered using Google Play services</cite> |
| ML Kit Text Recognition v2 | On-device OCR, `com.google.mlkit:text-recognition` (+ per-script artifacts) | <cite index="22-1">Recognizes text in Chinese, Devanagari, Japanese, Korean, and Latin character sets</cite>; minSdk 23 for this artifact |
| OpenCV for Android | 4.x, distributed via Maven Central since 4.9.0 | <cite index="33-1">Since OpenCV 4.9.0, the OpenCV Android package is available via Maven Central and can be installed automatically as a Gradle dependency</cite> — no more manual NDK/module-import dance |
| Room | 2.8.x is the stable line; Room 3.0 just shipped (July 1, 2026) | Room 3.0 targets Kotlin Multiplatform under a new `androidx.room3` package — skip it unless you want iOS/desktop sharing later; plain Room 2.x is the right choice for a pure-Android app |
| Android Studio | "Quail" series (2026.1.x) current stable, "Panda 4" the prior stable line | Gemini-powered agent mode can scaffold dependencies and self-correct build errors if you want to use it |
| Play Store target API | API 35 (Android 15) minimum for new apps/updates since Aug 31, 2025 | <cite index="61-1">New apps and app updates must target Android 15 (API level 35) or higher to be submitted to Google Play</cite>. Google has bumped this requirement every August in recent years — budget time to re-target before you ship if development stretches past summer |
| Android Developer Verification | Rolling out from September 2026 | <cite index="92-1">Starting in September 2026, apps in select regions must be registered by a verified developer to be installed on certified Android devices</cite>, first in Brazil/Indonesia/Singapore/Thailand, expanding globally in 2027. Applies to sideloaded and F-Droid-style distribution too, not just Play Store — see Section 13 |

---

## 4. Recommended tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin (latest 2.3/2.4 stable) | Standard for all new Android development |
| UI toolkit | Jetpack Compose + Material 3, single Activity | Google's current recommended toolkit for all new UI |
| Navigation | Navigation Compose | Mature and stable; Navigation 3 exists as a newer alternative if you want to track bleeding-edge patterns |
| Scan engine | ML Kit Document Scanner API (Path A) *or* CameraX 1.6 + OpenCV (Path B) | See Section 2 |
| OCR | ML Kit Text Recognition v2 (on-device) | Free, offline, no account needed |
| Local database | Room 2.8.x | Metadata: documents, pages, OCR text, folders |
| File storage | App-specific storage (`filesDir` / `getExternalFilesDir`) for working files; `MediaStore` for user-facing exports | Scoped-storage compliant since Android 10 |
| PDF assembly | Android's built-in `PdfDocument` class | No extra dependency needed for image-based PDFs; see Section 8.4 for the searchable-PDF technique |
| Dependency injection | Hilt | Google's recommended DI for Android; Koin is a valid lighter-weight alternative if you prefer less codegen |
| Async | Kotlin Coroutines + Flow | Repository exposes `Flow` for reads, `suspend fun` for writes |
| Image loading | Coil 3 | Thumbnail rendering in the library grid |
| Architecture | MVVM + unidirectional data flow (UDF), Google's recommended layered architecture | See Section 5 |

---

## 5. App architecture

Google's current official guidance is a layered architecture — UI layer, optional domain layer, data layer — connected by unidirectional data flow: <cite index="71-1">state flows in only one direction, typically from parent component to child component, while the events that modify the data flow in the opposite direction</cite>. The diagram above maps this onto the scanner app specifically. A few points worth calling out beyond what's in the diagram:

- **ViewModel is the state holder.** <cite index="72-1">The ViewModel type is the recommended implementation for managing screen-level UI state with access to the data layer</cite>. Each screen (scan, library, viewer) gets one ViewModel exposing a single `StateFlow<UiState>`.
- **The domain layer is optional and worth adding once, not before.** For a solo project, `ViewModel → Repository` directly is fine at first. Add use-case classes (`CaptureDocumentUseCase`, `RunOcrUseCase`, `ExportPdfUseCase`) when the same logic starts getting duplicated across ViewModels, or when a single action needs to coordinate more than one repository.
- **Repository is the single source of truth.** It hides whether a read comes from Room or a file on disk, and is the seam where Path A and Path B implementations can be swapped without touching UI code.

### Suggested package structure

```
app/
 ├─ core/
 │   ├─ camera/           # ML Kit scanner launcher, or CameraX use-case wiring
 │   ├─ imageprocessing/  # OpenCV helpers — only needed for Path B
 │   ├─ ocr/              # ML Kit Text Recognition wrapper
 │   ├─ pdf/              # PdfDocument assembly + searchable-text-layer logic
 │   └─ storage/          # File I/O, MediaStore helpers, scoped-storage utilities
 ├─ data/
 │   ├─ local/            # Room database, DAOs, entities
 │   └─ repository/       # DocumentRepository (the single source of truth)
 ├─ domain/                # Optional use cases — add when duplication appears
 ├─ ui/
 │   ├─ scan/              # Camera / capture screen
 │   ├─ crop/              # Manual crop + filter adjustment (Path B, or if you skip ML Kit's built-in editor)
 │   ├─ library/           # Document grid/list, search
 │   ├─ viewer/            # Page viewer, reorder, share, export
 │   ├─ settings/
 │   └─ theme/
 └─ di/                    # Hilt modules
```

---

## 6. Data model

Two entities cover the core use case; add a `folders`/`tags` table later if you want organization beyond a flat list.

```kotlin
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folder: String? = null,
    val pdfPath: String?        // null until export/save-as-PDF has run
)

@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,          // page order within the document
    val imagePath: String,      // cropped/enhanced image on disk
    val ocrText: String? = null // null until OCR has run on this page
)
```

For searching OCR text across documents, either `LIKE '%query%'` on `ocrText` (fine at small scale) or a Room FTS4 virtual table if your users tend to accumulate hundreds of scanned pages.

---

## 7. Screens & user flow

1. **Scan** — camera viewfinder (Path A: ML Kit's own screen; Path B: your CameraX preview with live edge overlay) → capture → auto-crop → optional manual corner adjustment.
2. **Enhance** — filter selection: original color, grayscale, black & white / "scan" mode, auto-enhance.
3. **Multi-page session** — "add another page" loop, reorder via drag handle, delete a page.
4. **OCR review** *(optional feature)* — show recognized text, let the user correct obvious errors before it's indexed for search.
5. **Library** — grid or list of saved documents, thumbnail, title, date, search bar (searches titles + OCR text).
6. **Viewer / export** — paginated view of a saved document, share sheet (send the PDF via any app), rename, delete, move to folder.
7. **Settings** — default filter, PDF image quality/compression, OCR language pack management, storage location.

---

## 8. Feature-by-feature technical design

### 8.1 Capture & auto-crop

**Path A (ML Kit Document Scanner API):**

```kotlin
val options = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(false)
    .setPageLimit(20)
    .setResultFormats(
        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
    )
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // crop, filters, stain/shadow cleanup
    .build()

val scanner = GmsDocumentScanning.getClient(options)
val scannerLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        scanResult?.pdf?.let { pdf ->
            // copy pdf.uri into app storage, insert a DocumentEntity + PageEntity rows
        }
    }
}

scanner.getStartScanIntent(activity)
    .addOnSuccessListener { intentSender ->
        scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
    }
```

Three built-in modes control how much editing the user gets: <cite index="2-1">SCANNER_MODE_BASE offers basic editing capabilities like crop, rotate, and reorder pages; SCANNER_MODE_BASE_WITH_FILTER adds image filters such as grayscale and auto-enhancement; SCANNER_MODE_FULL adds ML-enabled cleaning like erasing stains and fingers</cite>, and is the default.

**Path B (CameraX + OpenCV), the classic pipeline:**

1. `Imgproc.cvtColor` → grayscale
2. `Imgproc.GaussianBlur` → reduce noise
3. `Imgproc.Canny` → edge map
4. `Imgproc.dilate` → close small gaps in edges
5. `Imgproc.findContours` → candidate shapes
6. Sort by area, run `Imgproc.approxPolyDP` on the largest ones looking for a 4-point polygon
7. Order the 4 points (top-left, top-right, bottom-right, bottom-left)
8. `Imgproc.getPerspectiveTransform` + `Imgproc.warpPerspective` → flatten the trapezoid into a rectangle

This is the part that's "fast to get working, slow to get robust" — low-contrast backgrounds (white paper on a white desk) and curved pages are where you'll spend most of your iteration time.

### 8.2 Image enhancement / filters

Grayscale conversion, contrast/brightness adjustment, adaptive thresholding for the black-and-white "scan" look, and unsharp-mask sharpening. If you're on Path A, `SCANNER_MODE_BASE_WITH_FILTER` or `SCANNER_MODE_FULL` already gives you grayscale and auto-enhancement — you may not need to build this yourself at all. On Path B, all of these are direct `Imgproc` calls.

### 8.3 OCR (ML Kit Text Recognition v2)

```kotlin
val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
val image = InputImage.fromFilePath(context, pageImageUri)

recognizer.process(image)
    .addOnSuccessListener { visionText ->
        // visionText.text -> store in PageEntity.ocrText
        // visionText.textBlocks -> bounding boxes, needed for the searchable-PDF text layer below
    }
```

<cite index="24-1">ML Kit's Text Recognition v2 runs entirely on-device: no network call, no cloud billing, no privacy concern</cite> — which is exactly the property you're building this app around. Runs fine on a background dispatcher per page; no need to block the UI thread.

### 8.4 PDF export — including a searchable PDF

For a straightforward image-based PDF, Android's built-in `android.graphics.pdf.PdfDocument` needs no extra dependency: create a page per image, `canvas.drawBitmap(...)`, `finishPage()`, repeat.

For a **searchable PDF** (a nice CamScanner-parity feature): draw the OCR'd words as invisible text positioned exactly under the image, scaled from the ML Kit bounding boxes to PDF page coordinates:

```kotlin
val page = pdfDocument.startPage(pageInfo)
page.canvas.drawBitmap(pageBitmap, 0f, 0f, null)

val invisiblePaint = Paint().apply { alpha = 0 } // fully transparent — but selectable/searchable
visionText.textBlocks.forEach { block ->
    block.lines.forEach { line ->
        val (x, y) = scaleToPageCoordinates(line.boundingBox, pageBitmap, pageInfo)
        page.canvas.drawText(line.text, x, y, invisiblePaint)
    }
}
pdfDocument.finishPage(page)
```

This is the same technique dedicated OCR tools use — a visible image layer with an invisible, positioned text layer underneath so `Ctrl+F` / text selection works while the page still looks like a scan.

### 8.5 Multi-page management & reordering

A simple `LazyColumn`/`LazyRow` with drag-and-drop reordering (via `Modifier.pointerInput` + a reorderable-list pattern, or a small library like `reorderable`), backed by updating each `PageEntity.position` on drop.

### 8.6 Local storage & scoped storage

- Working files (captured/cropped page images) → app-specific external storage (`context.getExternalFilesDir(null)`), which needs no runtime permission and is deleted automatically on uninstall.
- User-facing exports (a PDF the user wants to keep even if they uninstall) → `MediaStore.Downloads` via the scoped-storage APIs, or a Storage Access Framework `ACTION_CREATE_DOCUMENT` intent for "save as."
- No `WRITE_EXTERNAL_STORAGE` needed on modern targets; only add it with `android:maxSdkVersion="28"` if you need legacy-device compatibility.

### 8.7 Search

Search bar on the library screen filtering by title (simple `LIKE` query) and, once OCR has run, by document content. Room FTS4 is worth the small extra setup once you expect users to have more than a couple hundred pages.

---

## 9. Development roadmap (solo-developer estimate)

| Phase | Scope | Estimate |
|---|---|---|
| 0 — Setup | Project scaffold, Gradle version catalog, Compose theme, nav skeleton, pick Path A vs B | 2–4 days |
| 1 — Core capture MVP | End-to-end: capture → crop → save locally → basic library list. Path A is much faster here; Path B's edge-detection tuning is the long pole | 1–2 weeks |
| 2 — Multi-page & enhancement | Multi-page sessions, reorder, filter presets (if not already covered by Path A), multi-page PDF assembly | ~1 week |
| 3 — OCR & search | ML Kit Text Recognition integration, OCR storage, search UI, optional searchable-PDF text layer | 3–5 days |
| 4 — Library, organization & polish | Folders/rename/delete, share sheet, thumbnails, empty states, dark theme, accessibility pass, settings screen | ~1 week |
| 5 — Hardening & release | Edge-case testing, memory/performance profiling, privacy policy page, Play Store listing, target-API/developer-verification compliance | 3–7 days |

**Total: roughly 4–7 weeks solo on Path A; add 1–2 weeks for Path B's custom computer-vision work.** This matches the "few weeks to a couple months" range for a solo developer with some Android/CV background.

---

## 10. Testing strategy

- **Unit tests:** ViewModel logic, repository logic, and any pure image-processing utility functions — JUnit + `kotlinx-coroutines-test`, Turbine for asserting on `Flow` emissions.
- **UI tests:** Compose UI testing (`androidx.compose.ui:ui-test-junit4`) for the scan → save → view flow.
- **Manual device matrix:** deliberately test low-light captures, low-contrast backgrounds (white paper on white desk), curled/creased pages, a low-RAM device (to confirm your fallback path if Path A's 1.7GB RAM requirement isn't met), and both your minSdk floor and the latest Android version.
- **Screenshot/golden tests** (optional): Paparazzi for catching visual regressions in Compose screens without an emulator.

---

## 11. Permissions, privacy & manifest

- **No `INTERNET` permission at all.** This is both the core privacy promise of the app and a major simplifier for the Play Console's Data Safety form — "no data collected or shared" is a straightforward answer when there's genuinely no network stack.
- **`CAMERA`** — only needed if you're on Path B (custom CameraX). On Path A, ML Kit's Document Scanner uses Google Play Services' own camera permission, so your app doesn't need to declare or request it.
- **Privacy policy is still required** by the Play Store for any app requesting camera access or handling personal documents — even a one-page "we don't collect anything" policy hosted anywhere public satisfies this, but it's commonly forgotten precisely because there's no backend to justify it.
- **Watch for accidental network dependencies:** crash-reporting/analytics SDKs (Firebase Crashlytics, etc.) do phone home. If "no cloud, ever" is a hard requirement — not just for scanned content but for telemetry too — skip them or use an on-device-only logging approach.

---

## 12. Distribution & Play Store submission

- **Target API:** 35 (Android 15) is the mandatory floor as of this writing; expect Google to bump this again around August 2026 as it has in recent years — check the Play Console requirements page before you submit if your build stretches into late summer.
- **Android Developer Verification:** starting September 2026 in Brazil, Indonesia, Singapore, and Thailand (expanding globally in 2027), apps — including sideloaded ones — need to be tied to a verified developer identity to install on certified Android devices. <cite index="95-1">Developers remain free to sideload apps or use any store they prefer</cite>; students/hobbyists have a lighter-weight, no-fee verification path. Worth registering early regardless of your distribution channel, since it also covers direct-APK and F-Droid-style distribution, not just the Play Store.
- **If you go the fully-offline, no-Google-Play-Services route (Path B) for F-Droid distribution:** you'll need to build OpenCV (and any ONNX/ML models) from source rather than pulling prebuilt binaries, to stay F-Droid compatible. Section 14's MakeACopy project is a working example of exactly this setup.

---

## 13. Reference implementations (real open-source prior art)

Worth studying before or while you build — these are real, working examples of pieces of this exact app:

- **[MakeACopy](https://github.com/egdels/makeacopy)** — the closest existing match to what you're describing: <cite index="121-1">an open-source Android document scanner with OCR that is designed to be privacy-friendly, working completely offline without any cloud connection or tracking</cite>. Uses OpenCV + an ONNX edge-detection model + PaddleOCR, and is F-Droid compatible by building its native dependencies from source.
- **[OpenNoteScanner](https://github.com/allgood/OpenNoteScanner)** — long-running open-source OpenCV-based scanner, available on both Play and F-Droid.
- **[Simple_Document_Scanner_Android](https://github.com/shubham0204/Simple_Document_Scanner_Android)** — a deliberately simple, educational codebase with two branches: one API-based, one fully on-device — a good way to learn the OpenCV-in-Kotlin pipeline specifically.
- **[Document-Scanning-Android-SDK](https://github.com/zynkware/Document-Scanning-Android-SDK)** (zynkware) — CameraX + a trimmed-down OpenCV build, a clean reference for a lean auto-capture implementation.
- **[android-document-scanner](https://github.com/Kuama-IT/android-document-scanner)** (Kuama-IT) — another CameraX + OpenCV library, MIT-style reference code.

---

## 14. Risks & common pitfalls

- **Edge-detection robustness is the real engineering cost** on Path B — low-contrast backgrounds and curled pages are where "works in the demo" and "works for real users" diverge.
- **ML Kit Document Scanner's 1.7GB RAM floor** means Path A silently fails (`MlKitException(UNSUPPORTED)`) on some low-end devices — decide up front whether you need a Path B fallback for that segment or are fine excluding it.
- **Large bitmap memory management:** multi-page, high-resolution scans can OOM on capture or PDF assembly. Downsample aggressively for thumbnails; stream/recycle bitmaps rather than holding a full-resolution page per array entry.
- **OCR accuracy expectations:** ML Kit's text recognizer is tuned for printed text — set realistic expectations for handwriting.
- **Google Play Services dependency (Path A)** means the scanner won't work at all on Play-Services-less devices. If that audience matters to you, Path B is the only option.
- **Annual targetSdk bump:** budget a maintenance pass every year even after "done," or the app will eventually be blocked from Play Store updates.

---

## 15. Appendix — illustrative Gradle version catalog

```toml
[versions]
kotlin = "2.3.20"          # check latest 2.3.x/2.4.x stable
composeBom = "2026.06.00"
camerax = "1.6.0"
mlkitDocumentScanner = "16.0.0"
mlkitTextRecognition = "16.0.0"   # verify current version — this artifact updates often
room = "2.8.0"
coroutines = "1.9.0"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-camera-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
androidx-camera-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
androidx-camera-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
androidx-camera-compose = { module = "androidx.camera:camera-compose", version.ref = "camerax" }
mlkit-document-scanner = { module = "com.google.android.gms:play-services-mlkit-document-scanner", version.ref = "mlkitDocumentScanner" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkitTextRecognition" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

*(Hilt, Coil, and Navigation Compose omitted from the catalog above — check `dagger.dev/hilt`, Coil's release page, and `developer.android.com/jetpack/androidx/releases/navigation` for their current versions, since none of them showed up reliably enough in research to pin a confident number here.)*

---

## Quick summary

Start with **Path A (ML Kit Document Scanner API)** for the fastest path to a genuinely good scanner — it's on-device, requires no camera permission of your own, and covers crop/filter/multi-page/PDF out of the box. Layer Room + local file storage + on-device OCR on top with a standard MVVM/UDF architecture, and you have a full CamScanner-equivalent feature set without a single network call. Switch to custom CameraX + OpenCV (Path B) only if you specifically need to run without Google Play Services.
