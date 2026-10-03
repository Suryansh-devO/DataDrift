package com.example.datadrift.notification

// ============================================================
// DataDrift - DataNotificationManager
// ============================================================
// RESPONSIBILITY:
//
// 1. Monitoring notification channel
// 2. Persistent monitoring notification
// 3. Remove monitoring notification
//
// IMPORTANT:
// DataMonitorService is NOT inside this file.
// DataMonitorService exists only in:
// service/DataMonitorService.kt
//
// ============================================================

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

import androidx.core.app.NotificationCompat

import com.example.datadrift.MainActivity


// ============================================================
// DATA NOTIFICATION MANAGER
// ============================================================

class DataNotificationManager(
    private val context: Context
) {

    // ========================================================
    // SYSTEM NOTIFICATION MANAGER
    // ========================================================

    private val systemNotificationManager =
        context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager


    // ========================================================
    // CREATE NOTIFICATION CHANNEL
    // ========================================================

    fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    MONITOR_CHANNEL_ID,

                    "DataDrift Monitor",

                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                "Shows DataDrift usage and live network speed"

            channel.setSound(
                null,
                null
            )

            channel.enableVibration(
                false
            )

            systemNotificationManager
                .createNotificationChannel(
                    channel
                )
        }
    }


    // ========================================================
    // INITIAL NOTIFICATION
    // ========================================================

    fun createInitialNotification():
            Notification {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(

                context,

                0,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(

            context,

            MONITOR_CHANNEL_ID

        )

            .setSmallIcon(
                android.R.drawable
                    .ic_menu_info_details
            )

            .setContentTitle(
                "DataDrift"
            )

            .setContentText(
                "Starting monitoring..."
            )

            .setContentIntent(
                pendingIntent
            )

            .setOngoing(
                true
            )

            .setAutoCancel(
                false
            )

            .setSilent(
                true
            )

            .setShowWhen(
                false
            )

            .setPriority(
                NotificationCompat
                    .PRIORITY_LOW
            )

            .build()
    }


    // ========================================================
    // OLD NAME COMPATIBILITY
    // ========================================================
    //
    // Kuch purane DataMonitorService versions
    // is method ko use karte the.
    //
    // ========================================================

    fun createMonitoringNotification(

        customUsage: String,

        downloadSpeed: String,

        uploadSpeed: String

    ): Notification {

        return buildMonitoringNotification(

            usageLabel =
                "Custom",

            usage =
                customUsage,

            downloadSpeed =
                downloadSpeed,

            uploadSpeed =
                uploadSpeed
        )
    }


    // ========================================================
    // SHOW MONITORING NOTIFICATION
    // ========================================================

    fun showMonitoringNotification(

        customUsage: String,

        downloadSpeed: String,

        uploadSpeed: String

    ) {

        val notification =
            buildMonitoringNotification(

                usageLabel =
                    "Custom",

                usage =
                    customUsage,

                downloadSpeed =
                    downloadSpeed,

                uploadSpeed =
                    uploadSpeed
            )

        systemNotificationManager.notify(

            NOTIFICATION_ID,

            notification
        )
    }


    // ========================================================
    // SHOW MONITORING NOTIFICATION
    // WITH TOTAL / CUSTOM LABEL
    // ========================================================

    fun showMonitoringNotification(

        usageLabel: String,

        usage: String,

        downloadSpeed: String,

        uploadSpeed: String

    ) {

        val notification =
            buildMonitoringNotification(

                usageLabel =
                    usageLabel,

                usage =
                    usage,

                downloadSpeed =
                    downloadSpeed,

                uploadSpeed =
                    uploadSpeed
            )

        systemNotificationManager.notify(

            NOTIFICATION_ID,

            notification
        )
    }


    // ========================================================
    // BUILD MONITORING NOTIFICATION
    // ========================================================

    private fun buildMonitoringNotification(

        usageLabel: String,

        usage: String,

        downloadSpeed: String,

        uploadSpeed: String

    ): Notification {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(

                context,

                0,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val title =
            "$usageLabel: $usage   " +
                    "$downloadSpeed   " +
                    "$uploadSpeed"

        return NotificationCompat.Builder(

            context,

            MONITOR_CHANNEL_ID

        )

            .setSmallIcon(
                android.R.drawable
                    .ic_menu_info_details
            )

            .setContentTitle(
                title
            )

            .setContentIntent(
                pendingIntent
            )

            .setOngoing(
                true
            )

            .setAutoCancel(
                false
            )

            .setSilent(
                true
            )

            .setShowWhen(
                false
            )

            .setPriority(
                NotificationCompat
                    .PRIORITY_LOW
            )

            .build()
    }


    // ========================================================
    // REMOVE MONITORING NOTIFICATION
    // ========================================================

    fun removeMonitoringNotification() {

        systemNotificationManager.cancel(

            NOTIFICATION_ID
        )
    }


    // ========================================================
    // CONSTANTS
    // ========================================================

    companion object {

        const val NOTIFICATION_ID =
            1001

        private const val
                MONITOR_CHANNEL_ID =
            "datadrift_monitor_channel"
    }
}
