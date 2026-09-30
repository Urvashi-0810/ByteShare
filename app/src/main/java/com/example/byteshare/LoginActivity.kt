package com.example.byteshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.byteshare.data.AuthRepository

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthRepository.isSignedIn()) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        val btnSignIn = findViewById<Button>(R.id.btn_google_sign_in)
        val progress = findViewById<ProgressBar>(R.id.login_progress)

        btnSignIn.setOnClickListener {
            btnSignIn.isEnabled = false
            progress.visibility = View.VISIBLE

            AuthRepository.signInWithGoogle(this) { user, error ->
                if (user != null) {
                    goToMain()
                } else {
                    btnSignIn.isEnabled = true
                    progress.visibility = View.GONE
                    Toast.makeText(this, error ?: "Sign-in failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
