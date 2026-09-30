package com.example.byteshare.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Discovers ByteShare users: device contacts matched via emailIndex + members of the user's crews.
 */
object FriendsRepository {

    private const val TAG = "FriendsRepository"

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
     * Discovers friends = (contacts registered on ByteShare) + (members of my crews),
     * excluding myself. Returns entries with today's usage for ranking.
     */
    fun discoverFriends(context: Context, onResult: (List<FriendEntry>) -> Unit) {
        val myUid = AuthRepository.currentUserId
        if (myUid == null) {
            onResult(emptyList())
            return
        }

        val discovered = mutableSetOf<String>()
        val emails = getContactEmails(context)

        // Phase 1: resolve contact emails -> uids
        if (emails.isEmpty()) {
            collectCrewMemberUids(myUid, discovered) { uids ->
                fetchEntries(uids, onResult)
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
                    collectCrewMemberUids(myUid, discovered) { uids ->
                        fetchEntries(uids, onResult)
                    }
                }
            }
        }
    }

    /** Adds uids of everyone in my crews into [into], then returns the union. */
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
            onResult(emptyList())
            return
        }
        val entries = mutableListOf<FriendEntry>()
        val pending = AtomicInteger(uids.size)
        for (uid in uids) {
            UserRepository.fetchFriendEntry(uid) { entry ->
                synchronized(entries) { entry?.let { entries.add(it) } }
                if (pending.decrementAndGet() == 0) {
                    onResult(entries.sortedByDescending { it.weightedMinutes })
                }
            }
        }
    }
}
