package com.assetvault.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGeneratorUtil {

    /**
     * Generates a Court-Ready Evidence PDF document.
     * Returns the File object if successful, null otherwise.
     */
    fun generateEvidencePdf(
        context: Context,
        assetName: String,
        ownerName: String,
        pHash: String,
        txHash: String,
        walletAddress: String
    ): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 Size
        val page = document.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        val paint = Paint()

        // 1. Draw Header / Logo Placeholder
        paint.color = Color.parseColor("#6200EE") // Primary Purple
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ASSET VAULT - COURT-READY EVIDENCE", 50f, 80f, paint)

        // 2. Separator Line
        paint.color = Color.BLACK
        paint.strokeWidth = 2f
        canvas.drawLine(50f, 100f, 545f, 100f, paint)

        // 3. Document Meta
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())
        canvas.drawText("Generated On: $dateStr", 50f, 140f, paint)
        canvas.drawText("Document ID: DOC-${System.currentTimeMillis()}", 50f, 160f, paint)

        // 4. Asset Information
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. Asset Declaration", 50f, 220f, paint)
        
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Asset Name: $assetName", 50f, 250f, paint)
        canvas.drawText("Declared Owner: $ownerName", 50f, 270f, paint)
        canvas.drawText("Wallet Address: $walletAddress", 50f, 290f, paint)

        // 5. Blockchain Evidence
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("2. Cryptographic Proof (Polygon Network)", 50f, 350f, paint)
        
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Perceptual Hash (pHash):", 50f, 380f, paint)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText(pHash, 50f, 400f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Blockchain Transaction Hash:", 50f, 440f, paint)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText(txHash, 50f, 460f, paint)

        // 6. Footer / Legal
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textSize = 12f
        paint.color = Color.DKGRAY
        val legalText = "This document is cryptographically verifiable on the Polygon blockchain."
        val legalText2 = "Any tampering with the original asset will result in a pHash mismatch."
        canvas.drawText(legalText, 50f, 760f, paint)
        canvas.drawText(legalText2, 50f, 780f, paint)

        document.finishPage(page)

        // Save to app-specific Documents directory
        return try {
            val sanitizedName = assetName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = "Evidence_$sanitizedName.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val outputStream = FileOutputStream(file)
            document.writeTo(outputStream)
            document.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            null
        }
    }
}
