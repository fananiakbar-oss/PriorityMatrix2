package com.prioritymatrix.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.prioritymatrix.app.MainActivity
import com.prioritymatrix.app.R
import com.prioritymatrix.app.i18n.AppStrings

object NotificationHelper {
    const val CH_REMINDERS = "reminders"
    const val CH_OVERDUE = "overdue"
    const val CH_PLANNING = "planning"
    const val CH_FOCUS = "focus"
    const val CH_FOCUS_ALERT = "focus_alert"

    const val ID_PLANNING = 900_001
    const val ID_REVIEW = 900_002
    const val ID_FOCUS_ONGOING = 900_003
    const val ID_FOCUS_DONE = 900_004

    const val EXTRA_ROUTE = "route"

    /** (Re)creates channels; names follow the current language. */
    fun createChannels(context: Context, s: AppStrings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, s.channelReminders, NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_OVERDUE, s.channelOverdue, NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_PLANNING, s.channelPlanning, NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_FOCUS, s.channelFocus, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
                NotificationChannel(CH_FOCUS_ALERT, s.channelFocus + " ✓", NotificationManager.IMPORTANCE_HIGH)
            )
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between check and post — ignore.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun openAppIntent(context: Context, route: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3B4A86.toInt())
            .setAutoCancel(true)

    fun taskNotificationId(taskId: Long): Int = (taskId % 800_000).toInt() + 1
}
