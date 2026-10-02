package com.example.datadrift.notification

// ============================================================
// DataDrift - DataNotificationManager
// ============================================================
//
// Handles:
//
// 1. Foreground monitoring notification
// 2. Custom / Total label
// 3. Live download speed
// 4. Live upload speed
//
// Notification behavior:
//
// Daily Data Plan OFF:
//
//     Total: 80 MB   ↓ 10 KB/s   ↑ 2 KB/s
//
// Daily Data Plan ON:
//
//     Custom: 184 MB   ↓ 10 KB/s   ↑ 2 KB/s
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
// NOTIFICATION MANAGER
// ============================================================

class DataNotificationManager(
    private val context: Context
) {

    // ========================================================
    // CONSTANTS
    // ========================================================

    companion object {

        const val CHANNEL_ID =
            "datadrift_monitor_channel"

        const val NOTIFICATION_ID =
            1001

        const val CHANNEL_NAME =
            "DataDrift Monitor"

        const val CHANNEL_DESCRIPTION =
            "Shows DataDrift data usage and live network speed"
    }

    // ========================================================
    // SYSTEM NOTIFICATION MANAGER
    // ========================================================

    private val notificationManager =
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

                    CHANNEL_ID,

                    CHANNEL_NAME,

                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                CHANNEL_DESCRIPTION

            channel.setSound(
                null,
                null
            )

            channel.enableVibration(
                false
            )

            notificationManager
                .createNotificationChannel(
                    channel
                )
        }
    }

    // ========================================================
    // CREATE MONITORING NOTIFICATION
    // ========================================================
    //
    // label can be:
    //
    // "Total"
    //
    // OR
    //
    // "Custom"
    //
    // ========================================================

    fun createMonitoringNotification(

        usageLabel:
        String,

        usage:
        String,

        downloadSpeed:
        String,

        uploadSpeed:
        String

    ): Notification {

        // ----------------------------------------------------
        // Open MainActivity when notification is tapped
        // ----------------------------------------------------

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

        // ----------------------------------------------------
        // One-line notification text
        // ----------------------------------------------------
        //
        // Example:
        //
        // Total: 80 MB   ↓ 10 KB/s   ↑ 2 KB/s
        //
        // OR
        //
        // Custom: 184 MB   ↓ 0 KB/s   ↑ 0 KB/s
        //
        // ----------------------------------------------------

        val oneLineText =
            "$usageLabel: $usage   " +
                    "$downloadSpeed   " +
                    "$uploadSpeed"

        // ----------------------------------------------------
        // Build notification
        // ----------------------------------------------------

        return NotificationCompat.Builder(

            context,

            CHANNEL_ID

        )

            .setSmallIcon(
                android.R.drawable.ic_menu_info_details
            )

            // ------------------------------------------------
            // Put complete information in title.
            // This keeps the notification one-line.
            // ------------------------------------------------

            .setContentTitle(
                oneLineText
            )

            .setContentText(
                null
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

            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )

            .setSilent(
                true
            )

            .setShowWhen(
                false
            )

            .build()
    }

    // ========================================================
    // SHOW MONITORING NOTIFICATION
    // ========================================================

    fun showMonitoringNotification(

        usageLabel:
        String,

        usage:
        String,

        downloadSpeed:
        String,

        uploadSpeed:
        String

    ) {

        // ----------------------------------------------------
        // Make sure channel exists
        // ----------------------------------------------------

        createNotificationChannel()

        // ----------------------------------------------------
        // Create notification
        // ----------------------------------------------------

        val notification =
            createMonitoringNotification(

                usageLabel =
                    usageLabel,

                usage =
                    usage,

                downloadSpeed =
                    downloadSpeed,

                uploadSpeed =
                    uploadSpeed
            )

        // ----------------------------------------------------
        // Update existing notification
        //
        // Same notification ID means Android updates the
        // existing DataDrift notification instead of creating
        // a new notification every 3 seconds.
        // ----------------------------------------------------

        notificationManager.notify(

            NOTIFICATION_ID,

            notification
        )
    }

    // ========================================================
    // REMOVE MONITORING NOTIFICATION
    // ========================================================

    fun removeMonitoringNotification() {

        notificationManager.cancel(

            NOTIFICATION_ID
        )
    }
}