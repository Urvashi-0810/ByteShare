package com.byteshare.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.byteshare.android.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
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
        val progress = findViewById<ProgressBar>(R.id.login_progress)

        fun setBusy(busy: Boolean) {
            btnSignIn?.isEnabled = !busy
            progress?.visibility = if (busy) View.VISIBLE else View.GONE
        }

        btnSignIn?.setOnClickListener {
            setBusy(true)
            // Only continue once Firebase has a signed-in user; otherwise stay here and say why
            AuthRepository.signInWithGoogle(this) { user: FirebaseUser?, error: String? ->
                if (user != null) {
                    goToMain()
                } else {
                    setBusy(false)
                    if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}