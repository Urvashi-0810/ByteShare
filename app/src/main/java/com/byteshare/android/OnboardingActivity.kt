package com.byteshare.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.byteshare.android.ui.SystemBarInsets

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsLayout: LinearLayout
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        SystemBarInsets.apply(this)

        viewPager = findViewById(R.id.viewPager)
        dotsLayout = findViewById(R.id.layout_dots)
        btnNext = findViewById(R.id.btn_next)

        val pages = listOf(
            OnboardingPage(
                "⚡",
                "Welcome to ByteShare",
                "Your screen time pays the bill. Join crews, hold each other accountable, and settle your digital footprint."
            ),
            OnboardingPage(
                "⚖️",
                "The Fame Engine",
                "Not all apps are equal. Social Media gets a 2.0x penalty, Streaming 1.5x, Neutral 1.0x, and Productivity is just 0.5x."
            ),
            OnboardingPage(
                "⏱️",
                "Two Kinds of Time",
                "On the leaderboard, you'll see two numbers: Raw Time (actual minutes on your phone) and Weighted Score (your penalty time). Your Rank is based entirely on your Weighted Score!"
            ),
            OnboardingPage(
                "💸",
                "Split the Bill",
                "At the end of the week, the person with the highest weighted screen time pays the largest share of the bill. No doomscroll goes unpunished."
            )
        )

        viewPager.adapter = OnboardingAdapter(pages)
        setupDots(pages.size)
        updateDots(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                if (position == pages.size - 1) {
                    btnNext.text = "Finish"
                } else {
                    btnNext.text = "Next"
                }
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < pages.size - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun setupDots(count: Int) {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(8, 0, 8, 0) }

        for (i in 0 until count) {
            val dot = ImageView(this).apply {
                setImageResource(R.drawable.circle_dot) // Fallback if missing
                layoutParams = params
            }
            dotsLayout.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        for (i in 0 until dotsLayout.childCount) {
            val dot = dotsLayout.getChildAt(i) as ImageView
            if (i == position) {
                dot.setColorFilter(getColor(R.color.primary_ink))
                dot.scaleX = 1.2f
                dot.scaleY = 1.2f
            } else {
                dot.setColorFilter(getColor(R.color.gray_ink))
                dot.scaleX = 0.8f
                dot.scaleY = 0.8f
            }
        }
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("ByteSharePrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_completed_onboarding", true).apply()
        
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String
)

class OnboardingAdapter(private val pages: List<OnboardingPage>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emoji: TextView = view.findViewById(R.id.txt_onboarding_emoji)
        val title: TextView = view.findViewById(R.id.txt_onboarding_title)
        val desc: TextView = view.findViewById(R.id.txt_onboarding_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val page = pages[position]
        holder.emoji.text = page.emoji
        holder.title.text = page.title
        holder.desc.text = page.description
    }

    override fun getItemCount() = pages.size
}
