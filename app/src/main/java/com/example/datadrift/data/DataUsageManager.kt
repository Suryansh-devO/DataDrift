package com.example.datadrift.data

// ============================================================
// DataDrift - DataUsageManager
// ============================================================
// RESPONSIBILITY:
//
// 1. Mobile data usage calculate karna
// 2. Wi-Fi data usage calculate karna
// 3. Downloaded data (RX) calculate karna
// 4. Uploaded data (TX) calculate karna
// 5. Total data calculate karna
// 6. Usage Access permission check karna
// 7. Total usage ko MB / GB / TB mein format karna
//
// IMPORTANT:
//
// Usage aur Internet Speed ke formatter alag rahenge.
//
// USAGE:
// 125 MB
// 1.25 GB
// 18.72 GB
// 1.20 TB
//
// Speed formatting DataMonitorService mein hoga.
// ============================================================

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process

import java.util.Locale


class DataUsageManager(
    private val context: Context
) {

    // ========================================================
    // NETWORK STATS MANAGER
    // ========================================================

    private val networkStatsManager =
        context.getSystemService(
            Context.NETWORK_STATS_SERVICE
        ) as NetworkStatsManager


    // ========================================================
    // USAGE RESULT
    // ========================================================

    data class UsageResult(

        // Downloaded bytes
        val downloadBytes: Long,

        // Uploaded bytes
        val uploadBytes: Long,

        // Download + Upload
        val totalBytes: Long
    )


    // ========================================================
    // USAGE ACCESS PERMISSION CHECK
    // ========================================================

    fun hasUsageAccess(): Boolean {

        val appOpsManager =
            context.getSystemService(
                Context.APP_OPS_SERVICE
            ) as AppOpsManager

        val mode =
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )

        return mode ==
                AppOpsManager.MODE_ALLOWED
    }


    // ========================================================
    // MOBILE DATA USAGE
    // ========================================================

    fun getMobileDataUsage(
        startTime: Long,
        endTime: Long
    ): UsageResult {

        // ----------------------------------------------------
        // Usage Access nahi hai
        // ----------------------------------------------------

        if (!hasUsageAccess()) {

            return UsageResult(
                downloadBytes = 0L,
                uploadBytes = 0L,
                totalBytes = 0L
            )
        }


        // ----------------------------------------------------
        // Variables
        // ----------------------------------------------------

        var downloadBytes = 0L
        var uploadBytes = 0L

        var networkStats:
                NetworkStats? = null


        try {

            // ------------------------------------------------
            // Mobile network statistics
            // ------------------------------------------------

            networkStats =
                networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_MOBILE,
                    null,
                    startTime,
                    endTime
                )


            // ------------------------------------------------
            // Read every network bucket
            // ------------------------------------------------

            val bucket =
                NetworkStats.Bucket()


            while (
                networkStats.hasNextBucket()
            ) {

                networkStats.getNextBucket(
                    bucket
                )


                // Download
                downloadBytes +=
                    bucket.rxBytes


                // Upload
                uploadBytes +=
                    bucket.txBytes
            }


        } catch (
            e: SecurityException
        ) {

            downloadBytes = 0L
            uploadBytes = 0L


        } catch (
            e: Exception
        ) {

            downloadBytes = 0L
            uploadBytes = 0L


        } finally {

            // ------------------------------------------------
            // NetworkStats close
            // ------------------------------------------------

            networkStats?.close()
        }


        // ====================================================
        // TOTAL
        // ====================================================

        val totalBytes =
            downloadBytes + uploadBytes


        return UsageResult(

            downloadBytes =
                downloadBytes,

            uploadBytes =
                uploadBytes,

            totalBytes =
                totalBytes
        )
    }


    // ========================================================
    // WI-FI DATA USAGE
    // ========================================================
    //
    // Ye selected time period mein device ka total Wi-Fi
    // usage calculate karega.
    //
    // RX = Download
    // TX = Upload
    // Total = Download + Upload
    //
    // Example:
    //
    // Wi-Fi Download = 500 MB
    // Wi-Fi Upload   = 20 MB
    // Wi-Fi Total    = 520 MB
    //
    // IMPORTANT:
    //
    // TYPE_WIFI device ke aggregate Wi-Fi usage ko read karta
    // hai. Ye kisi ek particular Wi-Fi router / SSID ko
    // separately identify nahi karta.
    // ========================================================

    fun getWifiDataUsage(
        startTime: Long,
        endTime: Long
    ): UsageResult {

        // ----------------------------------------------------
        // Usage Access nahi hai
        // ----------------------------------------------------

        if (!hasUsageAccess()) {

            return UsageResult(
                downloadBytes = 0L,
                uploadBytes = 0L,
                totalBytes = 0L
            )
        }


        // ----------------------------------------------------
        // Variables
        // ----------------------------------------------------

        var downloadBytes = 0L
        var uploadBytes = 0L

        var networkStats:
                NetworkStats? = null


        try {

            // ------------------------------------------------
            // Wi-Fi network statistics
            // ------------------------------------------------

            networkStats =
                networkStatsManager.querySummary(
                    ConnectivityManager.TYPE_WIFI,
                    null,
                    startTime,
                    endTime
                )


            // ------------------------------------------------
            // Read every Wi-Fi network bucket
            // ------------------------------------------------

            val bucket =
                NetworkStats.Bucket()


            while (
                networkStats.hasNextBucket()
            ) {

                networkStats.getNextBucket(
                    bucket
                )


                // Download
                downloadBytes +=
                    bucket.rxBytes


                // Upload
                uploadBytes +=
                    bucket.txBytes
            }


        } catch (
            e: SecurityException
        ) {

            downloadBytes = 0L
            uploadBytes = 0L


        } catch (
            e: Exception
        ) {

            downloadBytes = 0L
            uploadBytes = 0L


        } finally {

            // ------------------------------------------------
            // NetworkStats close
            // ------------------------------------------------

            networkStats?.close()
        }


        // ====================================================
        // TOTAL
        // ====================================================

        val totalBytes =
            downloadBytes + uploadBytes


        return UsageResult(

            downloadBytes =
                downloadBytes,

            uploadBytes =
                uploadBytes,

            totalBytes =
                totalBytes
        )
    }


    // ========================================================
    // FORMAT DATA USAGE
    // ========================================================
    //
    // Usage ko normal data-monitoring apps jaisa dikhayega.
    //
    // 0 - 1 MB       -> 0 MB
    // 1 MB - 1 GB    -> MB
    // 1 GB - 1 TB    -> GB
    // 1 TB+          -> TB
    //
    // B / KB usage mein intentionally nahi dikhayenge.
    // ========================================================

    fun formatBytes(
        bytes: Long
    ): String {

        // ----------------------------------------------------
        // Zero / very small usage
        // ----------------------------------------------------

        if (bytes < 1024L * 1024L) {

            return "0 MB"
        }


        // ----------------------------------------------------
        // Units
        // ----------------------------------------------------

        val megabyte =
            1024.0 * 1024.0

        val gigabyte =
            megabyte * 1024.0

        val terabyte =
            gigabyte * 1024.0


        // ====================================================
        // TB
        // ====================================================

        if (bytes >= terabyte) {

            return String.format(
                Locale.US,
                "%.2f TB",
                bytes / terabyte
            )
        }


        // ====================================================
        // GB
        // ====================================================

        if (bytes >= gigabyte) {

            return String.format(
                Locale.US,
                "%.2f GB",
                bytes / gigabyte
            )
        }


        // ====================================================
        // MB
        // ====================================================

        return String.format(
            Locale.US,
            "%.0f MB",
            bytes / megabyte
        )
    }


    // ========================================================
    // CURRENT TIME
    // ========================================================

    fun getCurrentTime(): Long {

        return System.currentTimeMillis()
    }
}