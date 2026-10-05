package com.lumen.player.feature.recap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumen.player.MainActivity
import com.lumen.player.R
import com.lumen.player.data.PulseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

const val EXTRA_RECAP_YEAR = "recap_year"
const val EXTRA_RECAP_MONTH = "recap_month"
private const val CHANNEL = "recaps"

// once a day: in the first week of a month, tell the user last month's recap is ready (once, if ≥ 60 min)
@HiltWorker
class RecapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pulse: PulseRepository,
    private val store: DataStore<Preferences>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        if (today.dayOfMonth > 7) return Result.success()
        val month = today.minusMonths(1)
        val tag = "%d-%02d".format(month.year, month.monthValue)
        val key = stringPreferencesKey("recap_notified")
        if (store.data.first()[key] == tag) return Result.success()
        if (pulse.monthTotal(month.year, month.monthValue) < 60 * 60_000L) return Result.success()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Monthly recaps", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(EXTRA_RECAP_YEAR, month.year)
            .putExtra(EXTRA_RECAP_MONTH, month.monthValue)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val name = month.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your $name recap is ready")
            .setContentText("See your top songs, artists and minutes.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(3001, notification) }
        store.edit { it[key] = tag }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecapWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("recap-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
