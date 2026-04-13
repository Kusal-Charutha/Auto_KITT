package com.example.autokitt

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task

class LoginActivity : AppCompatActivity() {

    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val TAG = "LoginActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("33413763022-mr47rgouvcocfofoendvirhb3njpptmt.apps.googleusercontent.com")
            .requestEmail()
            .requestProfile()
            .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // Set the dimensions of the sign-in button.
        val signInButton = findViewById<android.view.View>(R.id.sign_in_button)
        // signInButton.setSize(SignInButton.SIZE_STANDARD) // Removed as we are using custom view

        signInButton.setOnClickListener {
            signIn()
        }

        val btnGuestMode = findViewById<android.widget.Button>(R.id.btnGuestMode)
        btnGuestMode.setOnClickListener {
            val prefs = getSharedPreferences("AutoKITT_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_guest_mode", true).apply()
            
            Log.d(TAG, "Entering Guest Mode, navigating to MainActivity")
            Toast.makeText(this, "Continuing as Guest (Data will not be saved)", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        val account = GoogleSignIn.getLastSignedInAccount(this)
        updateUI(account)
    }

    private fun signIn() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            // Signed in successfully, show authenticated UI.
            updateUI(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
            Toast.makeText(this, "Sign-in failed with code: " + e.statusCode, Toast.LENGTH_LONG).show()
            updateUI(null)
        }
    }

    private fun updateUI(account: GoogleSignInAccount?) {
        if (account != null) {
            val prefs = getSharedPreferences("AutoKITT_Prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_guest_mode", false)
                .putString("user_email", account.email ?: "")
                .putString("user_name", account.displayName ?: "")
                .apply()

            Log.d(TAG, "Sign-in successful, navigating to MainActivity")
            Toast.makeText(this, "Signed in as: " + account.displayName, Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            Log.d(TAG, "Sign-in failed or account is null")
        }
    }
}
