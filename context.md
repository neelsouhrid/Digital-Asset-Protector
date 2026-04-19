# Context: Digital Asset Protection — Google Solutions Challenge 2026

## Who You Are Helping

You are assisting **Neel**, a developer building an Android app for the Google Solutions Challenge 2026. This document gives you full context on the project so you can assist with code, architecture decisions, debugging, and implementation without needing repeated explanation.

---

## What This Project Is

A privacy-first Android application called the **"Asset Vault"** that allows a user to:

1. **Select** specific media files they want to protect (via system file picker — no bulk scanning).
2. **Generate** a unique visual "DNA" signature (a 512-dimensional hex vector) for each selected file using on-device AI.
3. **Register** that signature on a blockchain (Polygon) as proof of ownership.
4. **Detect** unauthorized copies of that media — including photos taken of a screen ("Analog Hole" captures) — using cloud-based vector similarity search.
5. **Track** where their asset was detected via a web dashboard.

The app's social impact use case is protecting non-consensual media sharing (e.g. "View Once" photos being re-captured on a second device).

---

## What This Project Is NOT

- It is **not** a background photo scanner. It does not crawl the user's 19k photo library.
- It does **not** request `READ_MEDIA_IMAGES` or any broad storage permission.
- It does **not** upload raw images to any server — only hex vectors leave the device.
- It does **not** store any PII. Users are identified by a UUID generated on first boot.

---

## Target Device & Platform

- **Device:** Realme 8
- **OS:** Android 13 (API 33)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material Design 3

---

## Core Architecture

### The User Flow (Step by Step)

1. User opens app → sees their Vault (empty initially).
2. User taps **"Add Asset"** → system file picker opens (`ACTION_OPEN_DOCUMENT`).
3. User picks one photo → app receives a scoped URI.
4. App calls `contentResolver.takePersistableUriPermission()` to retain access across restarts.
5. `VectorEngine` reads the file bytes from the URI → generates a 512-dim hex vector via TFLite.
6. `PHashGenerator` generates a perceptual hash (pHash) from the same bytes.
7. Both are saved to a `SignatureEntity` in the local Room database.
8. User taps **"Mint / Protect"** → `ZkpGenerator` creates a Zero-Knowledge Proof → `BlockchainManager` registers the hex on Polygon.
9. The returned `blockchainTxId` is saved back to the `SignatureEntity`.
10. Asset shows as **"Protected"** in the Vault UI.

### Analog Hole Detection Flow

1. `ContentMonitorAccessibilityService` detects that a "View Once" or protected asset is on screen.
2. When a new photo is taken on a *receiving* device, the service triggers.
3. The new photo's URI is passed through `VectorEngine` + `PHashGenerator`.
4. The resulting vector is sent to Vertex AI Vector Search.
5. If a match above threshold is found, the event is logged (timestamp, rough location, match %).
6. The original creator sees this on the web Provenance Dashboard.

---

## File Structure

```
/ui
  MainActivity.kt              — Entry point, permissions, "Add Asset" button
  AssetPickerFragment.kt       — ACTION_OPEN_DOCUMENT launcher + URI permission grant
  VaultFragment.kt             — List of protected assets with status badges
  AssetDetailActivity.kt       — Distribution map for one asset

/services
  ContentMonitorAccessibilityService.kt  — Screen-state watcher for analog hole
  BootReceiver.kt                        — Restarts accessibility service on reboot

/ai
  VectorEngine.kt              — TFLite inference on a single URI's bytes → 512-dim hex
  PHashGenerator.kt            — Perceptual hash for analog-hole resistance

/data
  AppDatabase.kt               — Room database
  SignatureEntity.kt           — uri, hexVector, pHash, timestamp, blockchainTxId
  SecurePreferences.kt         — EncryptedSharedPreferences for private key + UUID

/network
  CloudApiClient.kt            — Vertex AI Vector Search interface
  BlockchainManager.kt         — Web3j → Polygon node connection
  ZkpGenerator.kt              — Zero-Knowledge Proof before any cloud transmission
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose, Material 3, Room, WorkManager |
| On-device AI | TensorFlow Lite (TFLite), MediaPipe, pHash algorithm |
| Blockchain | Solidity smart contract, Polygon (PoS), Web3j (Android client) |
| Privacy | Zero-Knowledge Proofs (Circom or ZoKrates), Android Keystore |
| Cloud | Google Cloud Vertex AI Vector Search 2.0 |
| Backend | Node.js or Go on Google Cloud Run |
| Database | Cloud Firestore (non-sensitive tracking events only) |
| Web Dashboard | React + Google Maps API |

---

## Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Required for any foreground notification |
| `BIND_ACCESSIBILITY_SERVICE` | Screen state monitoring for analog hole detection |
| `INTERNET` | Blockchain + Vertex AI calls |

> `READ_MEDIA_IMAGES` is explicitly **NOT requested**. Scoped URI access via the system file picker is the correct Android 13 pattern and is sufficient.

---

## Key Technical Concepts You Should Know

### Perceptual Hash (pHash)
Unlike a cryptographic hash (which changes completely if one pixel changes), a pHash measures *visual similarity*. If someone takes a photo of a screen showing a protected image, the pHash of that new photo will still match the original by ~85–95%. This is the core mechanism for "Analog Hole" detection.

### Face Embeddings / Vector Encoding
A TFLite model (e.g. FaceNet or a MediaPipe image encoder) converts an image into a 512-dimensional floating point vector. This vector captures the semantic content of the image. Vectors of visually similar images will be close together in vector space (high cosine similarity).

### Vertex AI Vector Search
Google's managed Approximate Nearest Neighbor (ANN) service. It stores millions of hex vectors and can find the closest match to a query vector in milliseconds. This is how the system detects if a new photo is an unauthorized copy of a registered asset.

### Zero-Knowledge Proofs (ZKP)
Before any vector is sent to the cloud, `ZkpGenerator` creates a cryptographic proof that the sender *knows* the original asset, without revealing the actual hex vector. This protects user privacy even from the server.

### Blockchain (Polygon)
A Solidity smart contract stores a mapping of `SignatureHash => CreatorAddress`. "Minting" means writing this mapping on-chain. It is the immutable proof of who owned a signature first. Polygon is used over Ethereum mainnet for lower gas fees.

### Scoped URI / `ACTION_OPEN_DOCUMENT`
Android 13's scoped storage model. Instead of granting the app access to all photos, the user picks one file and the app gets a URI with permission limited to that file. `takePersistableUriPermission()` makes this survive app restarts.

---

## Development Modules (Build Order)

1. `AssetPickerFragment` — file picker + URI grant + logging
2. `VectorEngine` — TFLite inference on the picked URI's bytes
3. Room persistence — `SignatureEntity` + `VaultFragment` list UI
4. `BlockchainManager` — mint on Polygon, store `txId`
5. `CloudApiClient` — Vertex AI similarity search
6. `ContentMonitorAccessibilityService` — analog hole detection

---

## Hard Rules (Never Break These)

- Never scan files the user did not explicitly pick.
- Never upload raw image bytes to any server.
- Never hardcode API keys — use `BuildConfig` or Secrets Gradle plugin.
- Never block the main thread — all AI and blockchain calls use `Dispatchers.IO` or `Dispatchers.Default`.
- Never request unnecessary permissions, especially broad storage access.
- Never store PII — UUID only, no name, no phone number.
- Always check Room DB before re-processing a URI — if the URI + timestamp already exists, skip it.

---

## What "Done" Looks Like

A user can:
1. Open the app, pick a photo, and see it appear in their Vault.
2. Tap "Mint" and get a confirmation with a Polygon transaction ID.
3. Have another device take a photo of that image on screen.
4. Log into the web dashboard and see: *"Unauthorized copy detected in [location] at [time] — 91% match."*
