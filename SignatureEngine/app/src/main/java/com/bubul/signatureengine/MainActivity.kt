package com.bubul.signatureengine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var signatureManager: SignatureManager
    private lateinit var tvOutput: TextView
    private lateinit var btnTest: Button
    private lateinit var scrollView: ScrollView
    
    private lateinit var btnOriginal: Button
    private lateinit var btnScreen: Button
    private lateinit var btnDifferent: Button

    private var bmpOriginal: Bitmap? = null
    private var bmpScreen: Bitmap? = null
    private var bmpDifferent: Bitmap? = null

    // Launchers for picking images from the phone's gallery
    private val pickOriginalLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { bmpOriginal = getBitmapFromUri(it); btnOriginal.text = "Original Image ✅"; checkReady() }
    }
    private val pickScreenLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { bmpScreen = getBitmapFromUri(it); btnScreen.text = "Screen Photo ✅"; checkReady() }
    }
    private val pickDifferentLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { bmpDifferent = getBitmapFromUri(it); btnDifferent.text = "Different Image ✅"; checkReady() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
        signatureManager = SignatureManager(this)
        requestStoragePermission()
    }

    private fun buildUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        val title = TextView(this).apply {
            text = "Person 2 — Real Image Test"
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }
        
        btnOriginal = Button(this).apply { text = "1. Select Original Image" }
        btnOriginal.setOnClickListener { pickOriginalLauncher.launch("image/*") }

        btnScreen = Button(this).apply { text = "2. Select Screen Photo" }
        btnScreen.setOnClickListener { pickScreenLauncher.launch("image/*") }

        btnDifferent = Button(this).apply { text = "3. Select Different Image" }
        btnDifferent.setOnClickListener { pickDifferentLauncher.launch("image/*") }

        btnTest = Button(this).apply {
            text = "Run Signature Test"
            textSize = 16f
            isEnabled = false // Disabled until images are picked
        }
        btnTest.setOnClickListener { runTest() }

        tvOutput = TextView(this).apply {
            text = "Please select all three test images above."
            textSize = 12f
            setTextIsSelectable(true)
        }
        scrollView = ScrollView(this).apply { 
            setPadding(0, 24, 0, 0)
            addView(tvOutput) 
        }

        layout.addView(title)
        layout.addView(btnOriginal)
        layout.addView(btnScreen)
        layout.addView(btnDifferent)
        layout.addView(btnTest)
        layout.addView(scrollView)
        
        setContentView(layout)
    }

    // Enable the test button only when all 3 images are loaded
    private fun checkReady() {
        btnTest.isEnabled = (bmpOriginal != null && bmpScreen != null && bmpDifferent != null)
        if (btnTest.isEnabled) {
            tvOutput.text = "All images loaded. Ready to test!"
        }
    }

    // Convert gallery URI directly to Bitmap
    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            log("Error loading image: ${e.message}")
            null
        }
    }

    private fun requestStoragePermission() {
        val perms = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.READ_MEDIA_IMAGES)
        val needed = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
    }

    private fun runTest() {
        btnTest.isEnabled = false
        btnTest.text = "Running Models..."
        btnOriginal.isEnabled = false
        btnScreen.isEnabled = false
        btnDifferent.isEnabled = false

        log("\n" + "=".repeat(44))
        log("Real Image Test Starting...")
        log("=".repeat(44))

        lifecycleScope.launch {
            try {
                log("\n[1] Loading AI model...")
                signatureManager.initialize()
                log("    Model loaded OK")

                log("\n[2] Processing uploaded images...")
                val origBmp = bmpOriginal!!
                val scrBmp = bmpScreen!!
                val diffBmp = bmpDifferent!!

                log("\n[3] Generating signatures (This can take a few seconds)...")
                val sigOrig = signatureManager.generateSignature(origBmp)
                log("    Original  pHash computed")
                
                val sigScr = signatureManager.generateSignature(scrBmp)
                log("    Screen    pHash computed")
                
                val sigDiff = signatureManager.generateSignature(diffBmp)
                log("    Different pHash computed")

                log("\n[4] Comparing...")
                val vsScr = signatureManager.compareSignatures(sigOrig, sigScr)
                val vsDiff = signatureManager.compareSignatures(sigOrig, sigDiff)

                log("\n    Original vs Screen Photo:")
                log("      pHash sim   : ${(vsScr.phashSimilarity * 100).toInt()}%")
                log("      Embed sim   : ${"%.1f".format(vsScr.embeddingSimilarity * 100)}%")
                log("      Combined    : ${"%.1f".format(vsScr.combinedScore * 100)}%")
                log("      Match?      : ${vsScr.isMatch} (Should be TRUE)")
                log("      Confidence  : ${vsScr.confidence}")

                log("\n    Original vs Different Photo:")
                log("      pHash sim   : ${(vsDiff.phashSimilarity * 100).toInt()}%")
                log("      Embed sim   : ${"%.1f".format(vsDiff.embeddingSimilarity * 100)}%")
                log("      Combined    : ${"%.1f".format(vsDiff.combinedScore * 100)}%")
                log("      Match?      : ${vsDiff.isMatch} (Should be FALSE)")

                log("\n" + "=".repeat(44))
                if (vsScr.isMatch && !vsDiff.isMatch) {
                    log("SUCCESS: Real images passed the test!")
                    log("Analog hole detection = WORKING")
                    log("False positive filter = WORKING")
                } else if (vsScr.isMatch) {
                    log("WARNING: Analog hole detected, but 'Different' image also triggered a match!")
                    log("Likely the 'Different' image looks too similar.")
                } else {
                    log("WARNING: Failed to match the Screen photo with Original.")
                    log("Lighting, glare, or angle clipping might be too extreme.")
                }
                log("=".repeat(44))

            } catch (e: Exception) {
                log("\nERROR: ${e.message}")
                Log.e("SigEngine", "Test failed", e)
            } finally {
                btnTest.isEnabled = true
                btnTest.text = "Run Signature Test"
                btnOriginal.isEnabled = true
                btnScreen.isEnabled = true
                btnDifferent.isEnabled = true
            }
        }
    }

    private fun log(text: String) {
        runOnUiThread {
            tvOutput.append("\n$text")
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
        Log.d("SigEngine", text)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::signatureManager.isInitialized) signatureManager.release()
    }
}