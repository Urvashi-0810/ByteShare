package com.example.byteshare

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.byteshare.ui.fragments.CrewsFragment
import com.example.byteshare.ui.fragments.FriendsFragment
import com.example.byteshare.ui.fragments.MeFragment
import com.example.byteshare.ui.fragments.StatsFragment
import com.example.byteshare.ui.fragments.TasksFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }

    private fun loadFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        return true
    }
}