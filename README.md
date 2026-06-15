# 🛡️ Digital Asset Protector (Asset Vault)

Welcome to the **Digital Asset Protector** (also known as Asset Vault) repository. This project is a decentralized ecosystem designed to secure digital intellectual property (images, music, videos, etc.) directly from the creator's device. 

By bridging the gap between Web3 proof-of-ownership and practical Web2 on-device enforcement, this application makes digital IP theft mathematically verifiable and physically difficult to execute.

---

## ✨ Key Features

1. **Zero-Knowledge Asset Registration**
   Raw images never leave the user's device. Instead, the app calculates a mathematical representation (Perceptual Hash or pHash) locally on the device.
2. **Real-time On-Device Enforcement**
   Using Android Accessibility Services, the app proactively monitors the screen at the OS level. If a non-owner attempts to view a protected asset (e.g., to take a screenshot), the app instantly overlays a dynamic, frosted-glass blur to block unauthorized viewing.
3. **Immutable Blockchain Registration & Transfers**
   Asset ownership is permanently anchored to the **Polygon Amoy Testnet** via custom Smart Contracts. Users can securely transfer ownership to another wallet or add "Collaborative Owners" to share viewing rights.
4. **Ultra-Fast Cloud Similarity Search & Sighting Maps**
   Utilizes **Supabase Vector Search (pgvector)** to perform high-speed, scalable fuzzy matching. If an unauthorized copy is uploaded elsewhere, the system plots the exact location of the "Sighting" on a live interactive map (Red Marker) visible only to the true owners.
5. **AI-Powered Deepfake Detection & Tagging**
   Integrated with **Google Gemini AI** to automatically analyze media, generate smart tags, and detect if an asset was AI-generated before it gets vaulted.
6. **Multi-User & Seamless Wallet Generation**
   Integrated with **Supabase Auth** and Google Sign-In. The app deterministically generates a Web3 wallet locally based on the user's authenticated email. An automated POL token faucet ensures a completely gasless experience, removing the friction of seed phrases and crypto top-ups.
7. **Immersive Gallery Vault**
   Browse secured files (images and `.mp4` videos) in an immersive, swipeable Vault gallery that blurs restricted media natively.
8. **Multi-Language Support**
   Designed for global accessibility, the application and web portal natively support multiple languages including **English, Hindi, and Bengali**, allowing creators to navigate and secure assets in their preferred language.
9. **Companion Web Portal & AI Support**
   A dedicated web dashboard for enterprise users to manage their portfolio, track global leak analytics, and interact with an AI-agent for automated customer support ticketing.

---

## 🏗️ Architecture & Tech Stack

*   **Android Mobile App:** Kotlin, Android SDK, Accessibility Services API, ViewPager2, Room Database, osmdroid (Mapping).
*   **Web Portal / Website:** React.js, TypeScript, Vite, Tailwind CSS, shadcn/ui.
*   **AI & Search Infrastructure:** Supabase Vector Search (pgvector), Google Gemini Pro.
*   **Cloud Processing:** Python, FastAPI, Google Cloud Run.
*   **Backend & Relational Data:** Supabase (PostgreSQL, Real-time Sync).
*   **Cryptography:** Custom pHash algorithms, Android `EncryptedSharedPreferences`.
*   **Blockchain Infrastructure:** Solidity, Web3j, Polygon Amoy Testnet, Alchemy RPC.

---

## 🌿 Repository Structure (Branch Guide)

Because this project consists of multiple distinct tech stacks, the source code is divided across specific branches based on team contribution:

*   📱 **[`Neel` branch](https://github.com/neelsouhrid/Digital-Asset-Protector/tree/Neel):** Contains the complete source code for the **Android Application** (Kotlin).
*   ⛓️ **[`Jinia` branch](https://github.com/neelsouhrid/Digital-Asset-Protector/tree/Jinia):** Contains the **Solidity Smart Contracts** deployed on the Polygon Amoy Testnet.
*   🧠 **[`Tanisha` branch](https://github.com/neelsouhrid/Digital-Asset-Protector/tree/Tanisha):** Contains the **TensorFlow Lite (TFLite) models** and ML architecture.
*   ☁️ **[`Samiran` branch](https://github.com/neelsouhrid/Digital-Asset-Protector/tree/Samiran):** Contains the **Python backend** for Cloud Run, and the **React Companion Web Portal**.
*   🏠 **`main` branch:** The landing page for the repository containing this documentation.

---

## 📥 Download & Try it Out

You can download the latest compiled APK (v2.0.0) directly from our GitHub Releases page!

> **[⬇️ Download the Latest Android APK Here](https://github.com/neelsouhrid/Digital-Asset-Protector/releases/latest)**

---

## 🚀 Future Development

*   **Cross-Platform Ecosystem:** Extending the blur-protection mechanism via iOS apps and Web-browser extensions.
*   **NFT Marketplace Integration:** Allowing creators to securely monetize locally-protected assets seamlessly.
*   **Automated Licensing:** Implementing smart contracts for temporary licensing and automatic royalty distribution.
