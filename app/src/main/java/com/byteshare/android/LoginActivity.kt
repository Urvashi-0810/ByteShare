package com.byteshare.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.byteshare.android.data.AuthRepository
import com.byteshare.android.ui.SystemBarInsets

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthRepository.isSignedIn()) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)
        SystemBarInsets.apply(this)

        val btnSignIn = findViewById<Button>(R.id.btn_google_sign_in)
        val btnGuest = findViewById<Button>(R.id.btn_guest_sign_in)
        val progress = findViewById<ProgressBar>(R.id.login_progress)

        btnSignIn?.setOnClickListener {
            btnSignIn.isEnabled = false
            btnGuest?.isEnabled = false
            progress?.visibility = View.VISIBLE

            AuthRepository.signInWithGoogle(this) { _, _ ->
                // Proceed immediately to MainActivity (either signed into Firebase or Guest Mode)
                goToMain()
            }
        }

        btnGuest?.setOnClickListener {
            btnSignIn?.isEnabled = false
            btnGuest.isEnabled = false
            progress?.visibility = View.VISIBLE

            AuthRepository.signInAnonymously(this) { _, _ ->
                // Proceed immediately to MainActivity
                goToMain()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}