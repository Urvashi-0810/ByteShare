package com.byteshare.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.byteshare.android.BuildConfig

/**
 * Prominent disclosures shown before ByteShare requests sensitive access (Play user-data policy).
 * Keep the wording in sync with what UserRepository and FriendsRepository actually upload.
 */
object PermissionDisclosures {

    fun showUsageAccess(context: Context, cancelable: Boolean = true) {
        AlertDialog.Builder(context)
            .setTitle("Usage Access Needed")
            .setMessage(
                "ByteShare uses Usage Access to measure how long you spend in each app.\n\n" +
                    "• We upload daily and 7-day totals per category (social, streaming, neutral, productivity) " +
                    "to our servers. App names and what you do inside apps stay on your phone.\n\n" +
                    "• Your friends and crew members can see these totals and your rank, and your crews " +
                    "use them to split bills.\n\n" +
                    "On the next screen, find ByteShare and allow access. You can turn it off anytime in Settings."
            )
            .setPositiveButton("Continue") { _, _ ->
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Not Now", null)
            .setNeutralButton("Privacy Policy") { _, _ -> openPrivacyPolicy(context) }
            .setCancelable(cancelable)
            .show()
    }

    fun showContacts(context: Context, onContinue: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Find Friends From Contacts")
            .setMessage(
                "To show which of your friends are already on ByteShare, the app reads the email " +
                    "addresses in your contacts and checks each one against ByteShare accounts on our servers.\n\n" +
                    "• Your contacts are not saved or shared, and we never message them.\n\n" +
                    "• Only matches are used, to add those friends to your leaderboard."
            )
            .setPositiveButton("Continue") { _, _ -> onContinue() }
            .setNegativeButton("Not Now", null)
            .setNeutralButton("Privacy Policy") { _, _ -> openPrivacyPolicy(context) }
            .show()
    }

    fun openPrivacyPolicy(context: Context) {
        val url = BuildConfig.PRIVACY_POLICY_URL
        if (!url.startsWith("https://")) {
            Toast.makeText(context, "Privacy policy is unavailable right now.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found to open $url", Toast.LENGTH_LONG).show()
        }
    }
}
