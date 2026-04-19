# Project Blueprint: Digital Asset Protection (Google Solutions Challenge)

## 1. Project Overview

An opt-in **"Asset Vault"** system for Android. The user explicitly selects which media files to protect using the system file picker. The app reads **only those files**, generates a visual signature (hex vector), and registers ownership on a Blockchain via Zero-Knowledge Proofs.

**Target Device:** Realme 8 (Android 13, API 33)  
**Primary Goal:** Let a user "mint" a selected photo's hex signature and track unauthorized copies (including "Analog Hole" captures) via Blockchain and Vertex AI Vector Search.

> ⚠️ **Core Design Principle:** The app does NOT scan the user's full photo library. There is no background scanner, no FileObserver on /DCIM/, and no WorkManager bulk-processing MediaStore. The user manually picks each file they want to protect.

---

## 2. Key Features

- **Asset DNA Engine:** Uses Perceptual Hashing (pHash) and Face Embeddings to create a 512-dim hex signature that survives screenshots and photo-of-screen captures.
- **Opt-in Vault UI:** User taps "Add Asset" → system file picker opens → only the selected file is read and processed.
- **Analog Hole Guard:** An Accessibility Service that monitors screen state to detect when "View Once" or sensitive content is displayed.
- **Blockchain Registry:** An immutable ledger to "mint" ownership of a visual signature on Polygon.
- **Provenance Dashboard:** A web-based view for creators to see where their assets have been detected (timestamp, rough location, match %).

---

## 3. Required File Structure (Kotlin/Android)

### `/ui` (User Interface)

- `MainActivity.kt`: Entry point, permission handling, and "Add Asset" button.
- `AssetPickerFragment.kt`: Launches `ACTION_OPEN_DOCUMENT` and handles persistable URI permission grant. **[NEW]**
- `VaultFragment.kt`: Displays list of user-added protected assets with sign/status badges. **[NEW]**
- `AssetDetailActivity.kt`: Shows the "Distribution Map" for a specific asset.

### `/services` (Core Logic)

- `ContentMonitorAccessibilityService.kt`: Detects on-screen activity to trigger "Analog Hole" protection.
- `BootReceiver.kt`: Ensures the Accessibility Service restarts automatically when the phone reboots.

> ❌ `AssetScannerService.kt` has been **removed**. There is no background Foreground Service scanning the library. All processing is triggered by the user picking a file.

### `/ai` (Signature Generation)

- `VectorEngine.kt`: Handles TFLite model inference — reads bytes from a single user-provided URI and converts to a 512-dim hex vector.
- `PHashGenerator.kt`: Generates structural perceptual hash for analog-hole resistance.

### `/data` (Local Persistence)

- `AppDatabase.kt`: Room database storing URI, hex vector, pHash, and blockchain status for each protected asset.
- `SignatureEntity.kt`: Data model — `uri`, `hexVector`, `pHash`, `timestamp`, `blockchainTxId`.
- `SecurePreferences.kt`: EncryptedSharedPreferences for Private Keys and UUID.

### `/network` (Cloud & Web3)

- `CloudApiClient.kt`: Interface for Google Cloud Vertex AI Vector Search.
- `BlockchainManager.kt`: Connects to Polygon/Ethereum nodes via Web3j.
- `ZkpGenerator.kt`: Implements Zero-Knowledge Proof logic before sending any data to the cloud.

---

## 4. Android 13 (Realme 8) Specifics

### Permissions

- `POST_NOTIFICATIONS` — Required for any foreground notification.
- `BIND_ACCESSIBILITY_SERVICE` — For screen state monitoring.
- `INTERNET` — For Blockchain and Vertex AI calls.

> ✅ `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` are **NOT needed**.  
> The app uses scoped URI access via `ACTION_OPEN_DOCUMENT`. The user grants per-file access through the system picker. Call `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)` immediately after receiving the URI.

### Power Management

- Use `PowerManager.WakeLock` only within the Accessibility Service if needed. No long-running scanner service exists.
- Realme UI aggressive battery management should not be an issue since there is no background scanning loop.

---

## 5. Security Constraints (What NOT to include)

### ❌ DO NOT:

1. **Request broad storage permissions:** Do not request `READ_MEDIA_IMAGES`. Use scoped URI access only. Requesting broad permissions when not needed will be flagged by Google Play.
2. **Hardcode API Keys:** Never put Google Cloud or Blockchain API keys in Kotlin files. Use `BuildConfig` or a Secrets Gradle plugin.
3. **Store Raw Images in Cloud:** Only send the **Hex Vector/Signature**. Storing actual user images on a server is a privacy violation.
4. **Block Main Thread:** All AI processing (Vectorization) and Blockchain calls MUST be inside `Dispatchers.Default` or `Dispatchers.IO` using Coroutines.
5. **Request Unnecessary PII:** Do not request the user's phone number or name. Use a UUID generated on the first boot.
6. **Re-process Without Checking:** Before vectorizing a file, check `AppDatabase` by URI. If a `SignatureEntity` already exists for that URI and the timestamp has not changed, skip re-processing.
7. **Scan files the user did not pick:** Do not use FileObserver, MediaStore queries, or WorkManager jobs that touch files the user has not explicitly selected.

---

## 6. User Flow (How the App Works)

1. User opens app → sees their **Vault** (empty at first).
2. User taps **"Add Asset"** → `AssetPickerFragment` launches `ACTION_OPEN_DOCUMENT`.
3. System file picker opens → user selects one photo.
4. App receives a scoped URI → calls `takePersistableUriPermission()`.
5. `VectorEngine` reads the file bytes from the URI and generates a 512-dim hex vector.
6. `PHashGenerator` generates a pHash from the same bytes.
7. Both are saved to `SignatureEntity` in Room DB.
8. User taps **"Mint / Protect"** → `ZkpGenerator` creates a proof → `BlockchainManager` registers the hex on Polygon.
9. `blockchainTxId` is saved back to `SignatureEntity`.
10. Asset now shows as **"Protected"** in `VaultFragment`.

---

## 7. Development Workflow for OpenClaw

1. **Module 1:** Build `AssetPickerFragment` using `ActivityResultContracts.OpenDocument`. Log the returned URI. Call `takePersistableUriPermission()` and verify it persists across app restarts.
2. **Module 2:** Pass the URI's bytes to `VectorEngine` (TFLite / MediaPipe). Log the generated 512-dim hex string for a sample picked image.
3. **Module 3:** Persist URI + hex + pHash in Room via `SignatureEntity`. Display the list in `VaultFragment` with status badges.
4. **Module 4:** Integrate `BlockchainManager` — mint the hex signature on Polygon. Store the returned `txId` back in Room.
5. **Module 5:** Connect `CloudApiClient` to Vertex AI Vector Search — run a similarity search using the minted vector to verify detection works.
6. **Module 6:** Wire `ContentMonitorAccessibilityService` for analog hole detection. When a suspected screenshot URI is available, pass it through `VectorEngine` + `PHashGenerator` and query Vertex AI for a match.
