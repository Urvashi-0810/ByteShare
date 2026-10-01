package com.example.byteshare.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Discovers ByteShare users: device contacts matched via emailIndex + members of the user's crews + demo friends.
 */
object FriendsRepository {

    private const val TAG = "FriendsRepository"

    private val defaultDemoFriends = listOf(
        FriendEntry("u_tejesh", "Tejesh Shigwan", 3157.0, 5088.0, 1740.0, 60.0, 1300.0, 57.0, true),
        FriendEntry("u_ankita", "Ankita Jadhav", 2417.0, 4425.0, 1980.0, 60.0, 377.0, 0.0, true),
        FriendEntry("u_priya", "Priya", 1820.0, 3120.0, 1200.0, 120.0, 500.0, 0.0, true),
        FriendEntry("u_arjun", "Arjun", 1540.0, 2800.0, 900.0, 180.0, 460.0, 0.0, true),
        FriendEntry("u_rahul", "Rahul", 376.0, 567.0, 180.0, 60.0, 136.0, 0.0, true)
    )

    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    /** Reads all contact email addresses. Requires READ_CONTACTS. */
    fun getContactEmails(context: Context): List<String> {
        if (!hasContactsPermission(context)) return emptyList()
        val emails = mutableSetOf<String>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
                null, null, null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    cursor.getString(idx)?.let { emails.add(it.lowercase().trim()) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading contacts", e)
        }
        return emails.toList()
    }

    /**
     * Discovers friends = (contacts registered on ByteShare) + (members of my crews) + (demo friends).
     */
    fun discoverFriends(context: Context, onResult: (List<FriendEntry>) -> Unit) {
        val myUid = AuthRepository.currentUserId

        val discovered = mutableSetOf<String>()
        val emails = getContactEmails(context)

        if (emails.isEmpty()) {
            if (myUid != null) {
                collectCrewMemberUids(myUid, discovered) { uids ->
                    fetchEntries(uids, onResult)
                }
            } else {
                onResult(defaultDemoFriends)
            }
            return
        }

        val pendingEmails = AtomicInteger(emails.size)
        val fromContacts = mutableSetOf<String>()
        for (email in emails) {
            UserRepository.lookupUidByEmail(email) { uid ->
                if (uid != null && uid != myUid) synchronized(fromContacts) { fromContacts.add(uid) }
                if (pendingEmails.decrementAndGet() == 0) {
                    discovered.addAll(fromContacts)
                    if (myUid != null) {
                        collectCrewMemberUids(myUid, discovered) { uids ->
                            fetchEntries(uids, onResult)
                        }
                    } else {
                        onResult(defaultDemoFriends)
                    }
                }
            }
        }
    }

    private fun collectCrewMemberUids(
        myUid: String,
        into: MutableSet<String>,
        onDone: (Set<String>) -> Unit
    ) {
        FirebaseCrewRepository.fetchMyCrewsOnce { crews ->
            for (crew in crews) {
                for (uid in crew.members.keys) {
                    if (uid != myUid) into.add(uid)
                }
            }
            onDone(into)
        }
    }

    private fun fetchEntries(uids: Set<String>, onResult: (List<FriendEntry>) -> Unit) {
        if (uids.isEmpty()) {
            onResult(defaultDemoFriends)
            return
        }
        val entries = mutableListOf<FriendEntry>()
        val pending = AtomicInteger(uids.size)
        for (uid in uids) {
            UserRepository.fetchFriendEntry(uid) { entry ->
                synchronized(entries) { entry?.let { entries.add(it) } }
                if (pending.decrementAndGet() == 0) {
                    val combined = (entries + defaultDemoFriends).distinctBy { it.uid }
                    onResult(combined.sortedBy { it.weightedMinutes })
                }
            }
        }
    }
}