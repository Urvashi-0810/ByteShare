package com.byteshare.android.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Periodic background upload of today's usage so friends' leaderboards stay fresh. */
class UsageSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!AuthRepository.isSignedIn()) return Result.success()
        val latch = CountDownLatch(1)
        val syncResult = AtomicReference<Boolean?>()
        UserRepository.syncProfileAndUsage(applicationContext) { success ->
            syncResult.set(success)
            latch.countDown()
        }
        if (!latch.await(25, TimeUnit.SECONDS)) return Result.retry()
        return if (syncResult.get() == true) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "usage_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
