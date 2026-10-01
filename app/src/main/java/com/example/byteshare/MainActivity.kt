package com.example.byteshare

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.byteshare.logic.AdManager
import com.example.byteshare.logic.RevenueCatManager
import com.example.byteshare.data.AuthRepository
import com.example.byteshare.data.UsageStatsCollector
import com.example.byteshare.data.UsageSyncWorker
import com.example.byteshare.data.UserRepository
import com.example.byteshare.data.XpRepository
import com.example.byteshare.ui.fragments.CrewsFragment
import com.example.byteshare.ui.fragments.FriendsFragment
import com.example.byteshare.ui.fragments.MeFragment
import com.example.byteshare.ui.fragments.StatsFragment
import com.example.byteshare.ui.fragments.TasksFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AuthRepository.isSignedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val prefs = getSharedPreferences("ByteSharePrefs", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("has_completed_onboarding", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Initialize AdMob and RevenueCat SDKs
        AdManager.initialize(this)
        RevenueCatManager.configure(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_friends -> loadFragment(FriendsFragment())
                R.id.nav_crews -> loadFragment(CrewsFragment())
                R.id.nav_stats -> loadFragment(StatsFragment())
                R.id.nav_tasks -> loadFragment(TasksFragment())
                R.id.nav_me -> loadFragment(MeFragment())
                else -> false
            }
        }

        // Default fragment
        if (savedInstanceState == null) {
            loadFragment(FriendsFragment())
        }

        if (savedInstanceState == null && !UsageStatsCollector.hasPermission(this)) {
            showUsageAccessPrompt()
        }

        // Publish profile + today's usage so friends' leaderboards include us
        UserRepository.syncProfileAndUsage(this)
        UsageSyncWorker.schedule(this)

        // Seed XP from Firebase and record daily activity
        XpRepository.fetchOnce()
        XpRepository.recordDailyActivity()
    }

    private fun showUsageAccessPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Usage Access Needed")
            .setMessage("ByteShare needs Usage Access to track your screen time and calculate crew scores. On the next screen, find ByteShare and allow access.")
            .setPositiveButton("Grant Access") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Not Now", null)
            .setCancelable(false)
            .show()
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        return true
    }
}