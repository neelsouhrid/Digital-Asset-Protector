# 🛡️ Digital Asset Protector (Asset Vault)

Welcome to the **Digital Asset Protector** (also known as Asset Vault) repository. This project is a decentralized ecosystem designed to secure digital intellectual property (images, pdf, music, videos etc.) directly from the creator's device. 

By bridging the gap between Web3 proof-of-ownership and practical Web2 on-device enforcement, this application makes digital IP theft mathematically verifiable and physically difficult to execute.

---

## ✨ Key Features

1. **Zero-Knowledge Asset Registration**
   Raw images never leave the user's device. Instead, the app calculates a mathematical representation (Perceptual Hash or pHash) locally on the device.
2. **Real-time On-Device Enforcement**
   Using Android Accessibility Services, the app proactively monitors the screen at the OS level. If a non-owner attempts to view a protected asset (e.g., to take a screenshot), the app instantly overlays a dynamic, frosted-glass blur to block unauthorized viewing.
3. **Immutable Blockchain Registration**
   Asset ownership is permanently anchored to the **Polygon Amoy Testnet** via custom Smart Contracts.
4. **Ultra-Fast Cloud Similarity Search**
   Utilizes **Google Vertex AI Vector Search** running on Google Cloud Run to perform high-speed, scalable fuzzy matching (Hamming Distance) to detect slightly altered or cropped images.
5. **"Sighting" vs "Protection" Verification**
   The system intelligently detects if an uploaded asset is the original owned asset or an unauthorized "sighting" copy, and logs it accordingly in Supabase.
6. **Multi-User & Seamless Wallet Generation**
   Integrated with **Supabase Auth** and Google Sign-In. The app deterministically generates a Web3 wallet locally based on the user's authenticated email, removing the friction of seed phrases.

---

## 🏗️ Architecture & Tech Stack

*   **Android Mobile App:** Kotlin, Android SDK, Accessibility Services API, Room Database, Coil.
*   **Web Portal / Website:** React.js, TypeScript, Vite, Tailwind CSS, shadcn/ui (Built with Lovable).
*   **AI & Search Infrastructure:** Google Vertex AI Vector Search.
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
*   ☁️ **[`Samiran` branch](https://github.com/neelsouhrid/Digital-Asset-Protector/tree/Samiran):** Contains the **Python backend**, `main.py` for Google Cloud Run, and `requirements.txt`.
*   🏠 **`main` branch:** The landing page for the repository containing this documentation.

---

## 📥 Download & Try it Out

You can download the latest compiled APK directly from our GitHub Releases page!

> **[⬇️ Download the Latest Android APK Here](https://github.com/neelsouhrid/Digital-Asset-Protector/releases/latest)**

---

## 🚀 Future Development

*   **Smart Contract Upgrades:** Full cryptographic ownership transfer and automated licensing agreements.
*   **Cross-Platform Ecosystem:** Extending the blur-protection mechanism via iOS apps and Web-browser extensions.
*   **NFT Marketplace Integration:** Allowing creators to securely monetize locally-protected assets seamlessly.
*   **Improved AI & ML:** Using advanced AI algorithms to detect even more sophisticated forms of image theft.
*   **User Experience:** Improving the user interface and user experience to make the app more intuitive and user-friendly.
*   **Marketplace:** Creating a marketplace for users to buy and sell digital assets.
*   **Admin Dashboard:** Creating an admin dashboard to manage users and assets.
*   **AI Agent:** Creating an AI agent to help users protect their digital assets.

