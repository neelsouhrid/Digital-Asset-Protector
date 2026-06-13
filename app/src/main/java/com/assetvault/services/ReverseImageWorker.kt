package com.assetvault.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.assetvault.network.BraveSearchApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ReverseImageWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ReverseImageWorker", "Starting Reverse Image / Plagiarism Monitor...")
        val apiKey = "" // LEFT BLANK AS REQUESTED BY USER

        if (apiKey.isEmpty()) {
            Log.d("ReverseImageWorker", "Brave Search API key is missing. Skipping monitor.")
            return Result.success()
        }

        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.search.brave.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(BraveSearchApi::class.java)
            
            // In a real app, we would query specific keywords from the user's uploaded assets.
            // Using a mock query for testing.
            val query = "\"digital asset protector\" filetype:jpg OR filetype:png"
            val response = api.search(apiKey = apiKey, query = query)

            response.web?.results?.let { results ->
                if (results.isNotEmpty()) {
                    Log.d("ReverseImageWorker", "Found potential infringements: ${results.size}")
                    sendInfringementNotification(results.first().title, results.first().url)
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("ReverseImageWorker", "Error during search: ${e.message}", e)
            return Result.retry()
        }
    }

    private fun sendInfringementNotification(title: String, url: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "infringement_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Infringement Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Potential Copyright Infringement")
            .setContentText("Found match: $title")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Found match at: $url"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
