package com.byteshare.android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.byteshare.android.logic.AdManager
import com.byteshare.android.logic.RevenueCatManager
import com.byteshare.android.data.AuthRepository
import com.byteshare.android.data.UsageStatsCollector
import com.byteshare.android.data.UsageSyncWorker
import com.byteshare.android.data.UserRepository
import com.byteshare.android.data.XpRepository
import com.byteshare.android.ui.PermissionDisclosures
import com.byteshare.android.ui.SystemBarInsets
import com.byteshare.android.ui.fragments.CrewsFragment
import com.byteshare.android.ui.fragments.FriendsFragment
import com.byteshare.android.ui.fragments.MeFragment
import com.byteshare.android.ui.fragments.StatsFragment
import com.byteshare.android.ui.fragments.TasksFragment
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
        SystemBarInsets.apply(this, bottomBar = bottomNav)
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
        PermissionDisclosures.showUsageAccess(this, cancelable = false)
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        return true
    }
}