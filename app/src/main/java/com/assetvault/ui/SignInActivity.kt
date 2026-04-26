package com.assetvault.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.assetvault.Constants
import com.assetvault.R
import com.assetvault.data.SecurePreferences
import com.assetvault.databinding.ActivitySignInBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Google Sign-In screen. Shown on launch if user is not signed in.
 */
class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private val TAG = "SignInActivity"
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip if already signed in
        val prefs = SecurePreferences.getInstance(this)
        if (prefs.isSignedIn()) {
            goToMain()
            return
        }

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(Constants.GOOGLE_WEB_CLIENT_ID)
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogleSignIn.setOnClickListener {
            signIn()
        }

        // Check for existing Google Sign-In
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            saveAccountAndProceed(account.email, account.displayName, account.photoUrl?.toString())
        }
    }

    private fun signIn() {
        binding.progressBar.visibility = View.VISIBLE
        val signInIntent = googleSignInClient.signInIntent
        @Suppress("DEPRECATION")
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d(TAG, "Sign-in success: ${account?.email}")
                saveAccountAndProceed(
                    account?.email,
                    account?.displayName,
                    account?.photoUrl?.toString()
                )
            } catch (e: ApiException) {
                Log.e(TAG, "Sign-in failed: ${e.statusCode}", e)
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAccountAndProceed(email: String?, name: String?, photoUrl: String?) {
        if (email == null) {
            Toast.makeText(this, "Could not get email", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = SecurePreferences.getInstance(this)
        prefs.setGoogleEmail(email)
        name?.let { prefs.setGoogleDisplayName(it) }
        photoUrl?.let { prefs.setGooglePhotoUrl(it) }
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
