package com.example.datadrift.service

// ============================================================
// DataDrift - DataMonitorService
// ============================================================
//
// Handles:
//
// 1. Background monitoring
// 2. Today / Total usage
// 3. Custom usage
// 4. Custom freeze when Daily Plan is OFF
// 5. Custom resume when Daily Plan is ON
// 6. Custom usage limit according to Daily Data Plan
// 7. Today Data Limit Alerts
// 8. Today Repeat Alerts
// 9. Daily Data Plan Alerts
// 10. Daily Plan reset at midnight
// 11. Persistent monitoring notification
// 12. Live mobile download/upload speed
// 13. Status-bar download speed overlay
// 14. Status-bar speed ON/OFF
// 15. Status-bar X/Y/Text Size settings
//
// DAILY PLAN CYCLE:
//
//     12:00 AM -> next 12:00 AM
//
// Daily Start Time only decides when Custom usage
// starts counting during the current calendar day.
//
// ============================================================


// ============================================================
// IMPORTS
// ============================================================

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service

import android.content.Context
import android.content.Intent

import android.graphics.Color
import android.graphics.PixelFormat

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats

import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

import android.provider.Settings

import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

import androidx.core.app.NotificationCompat

import com.example.datadrift.MainActivity

import com.example.datadrift.data.DataAlertStorage
import com.example.datadrift.data.DataRepository
import com.example.datadrift.data.DataUsageManager
import com.example.datadrift.data.DailyDataPlanStorage

import com.example.datadrift.model.DataAlert
import com.example.datadrift.model.DailyDataAlert
import com.example.datadrift.model.DailyDataPlan

import java.util.Calendar
import java.util.Locale


// ============================================================
// SERVICE
// ============================================================

class DataMonitorService : Service() {

    // ========================================================
    // MANAGERS
    // ========================================================

    private lateinit var dataUsageManager: DataUsageManager

    private lateinit var dataRepository: DataRepository

    private lateinit var alertStorage: DataAlertStorage

    private lateinit var dailyPlanStorage: DailyDataPlanStorage

    private lateinit var systemNotificationManager: NotificationManager


    // ========================================================
    // HANDLER
    // ========================================================

    private val handler =
        Handler(
            Looper.getMainLooper()
        )


    // ========================================================
    // MONITORING LOOP
    // ========================================================

    private val monitoringRunnable =
        object : Runnable {

            override fun run() {

                try {

                    performMonitoring()

                } catch (_: Exception) {

                    // Keep service alive even if
                    // one monitoring cycle fails.
                }

                handler.postDelayed(
                    this,
                    MONITOR_INTERVAL
                )
            }
        }


    // ========================================================
    // MOBILE SPEED STATE
    // ========================================================

    private var previousRxBytes = 0L

    private var previousTxBytes = 0L

    private var previousSpeedTime = 0L

    private var speedInitialized = false


    // ========================================================
    // STATUS BAR OVERLAY
    // ========================================================

    private var windowManager: WindowManager? = null

    private var speedTextView: TextView? = null


    // ========================================================
    // CUSTOM ALERT RUNTIME
    // ========================================================

    private var previousCustomUsage = 0L

    private var customUsageInitialized = false


    // ========================================================
    // TODAY ALERT RUNTIME
    // ========================================================

    private var previousTodayUsage = 0L

    private var todayAlertUsageInitialized = false


    // ========================================================
    // ON CREATE
    // ========================================================

    override fun onCreate() {

        super.onCreate()

        // ----------------------------------------------------
        // Managers
        // ----------------------------------------------------

        dataUsageManager =
            DataUsageManager(this)

        dataRepository =
            DataRepository(
                dataUsageManager
            )

        alertStorage =
            DataAlertStorage(this)

        dailyPlanStorage =
            DailyDataPlanStorage(this)

        systemNotificationManager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager


        // ----------------------------------------------------
        // Notification channels
        // ----------------------------------------------------

        createMonitorNotificationChannel()

        createAlertNotificationChannel()


        // ----------------------------------------------------
        // Start foreground service
        // ----------------------------------------------------

        startForeground(
            MONITOR_NOTIFICATION_ID,
            createInitialForegroundNotification()
        )


        // ----------------------------------------------------
        // Initialize speed
        // ----------------------------------------------------

        initializeSpeedCounters()


        // ----------------------------------------------------
        // Start monitoring
        // ----------------------------------------------------

        handler.post(
            monitoringRunnable
        )
    }


    // ========================================================
    // ON START COMMAND
    // ========================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        // ----------------------------------------------------
        // If service already exists and MainActivity changes
        // overlay settings, refresh monitoring immediately.
        // ----------------------------------------------------

        handler.removeCallbacks(
            monitoringRunnable
        )

        handler.post(
            monitoringRunnable
        )

        return START_STICKY
    }


    // ========================================================
    // ON DESTROY
    // ========================================================

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        removeSpeedOverlay()

        systemNotificationManager.cancel(
            MONITOR_NOTIFICATION_ID
        )

        super.onDestroy()
    }


    // ========================================================
    // ON BIND
    // ========================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }


    // ========================================================
    // MAIN MONITORING
    // ========================================================

    private fun performMonitoring() {

        // ----------------------------------------------------
        // App settings
        // ----------------------------------------------------

        val appPreferences =
            getSharedPreferences(
                APP_SETTINGS,
                Context.MODE_PRIVATE
            )


        val monitoringEnabled =
            appPreferences.getBoolean(
                KEY_MONITORING_ENABLED,
                true
            )


        // ----------------------------------------------------
        // Monitoring OFF
        // ----------------------------------------------------

        if (!monitoringEnabled) {

            removeSpeedOverlay()

            systemNotificationManager.cancel(
                MONITOR_NOTIFICATION_ID
            )

            return
        }


        // ----------------------------------------------------
        // Usage access
        // ----------------------------------------------------

        if (
            !dataRepository.hasUsageAccess()
        ) {

            removeSpeedOverlay()

            return
        }


        val now =
            System.currentTimeMillis()


        // ====================================================
        // TODAY USAGE
        // ====================================================

        val todayUsage =
            dataRepository.getTodayUsage()


        // ====================================================
        // DAILY PLAN
        // ====================================================

        val dailyPlan =
            dailyPlanStorage.loadPlan()


        val dailyPlanEnabled =
            dailyPlan?.isEnabled == true &&
                    dailyPlan.totalDataBytes > 0L


        // ====================================================
        // CUSTOM USAGE
        // ====================================================

        val customCalculation =
            calculateCustomUsage(

                plan =
                    dailyPlan,

                enabled =
                    dailyPlanEnabled,

                now =
                    now
            )


        val customUsage =
            customCalculation.usage


        val customCycleStart =
            customCalculation.cycleStart


        // ====================================================
        // MOBILE SPEED
        // ====================================================

        val speed =
            calculateNetworkSpeed()


        // ====================================================
        // MONITORING NOTIFICATION
        // ====================================================
        //
        // Daily Plan OFF
        //     -> Total
        //
        // Daily Plan ON but start time not reached
        //     -> Total
        //
        // Daily Plan ON and start time reached
        //     -> Custom
        //
        // ====================================================

        val customCycleActive =
            dailyPlanEnabled &&
                    customCycleStart > 0L


        val usageLabel =
            if (customCycleActive) {

                "Custom"

            } else {

                "Total"
            }


        val notificationUsage =
            if (customCycleActive) {

                dataRepository.formatBytes(
                    customUsage.totalBytes
                )

            } else {

                dataRepository.formatBytes(
                    todayUsage.totalBytes
                )
            }


        showMonitoringNotification(

            usageLabel =
                usageLabel,

            usage =
                notificationUsage,

            downloadSpeed =
                "↓ ${speed.first}",

            uploadSpeed =
                "↑ ${speed.second}"
        )


        // ====================================================
        // TODAY DATA LIMIT ALERTS
        // ====================================================

        checkTodayDataLimitAlerts(
            currentTodayBytes =
                todayUsage.totalBytes
        )


        // ====================================================
        // DAILY DATA PLAN ALERTS
        // ====================================================

        if (
            dailyPlanEnabled &&
            dailyPlan != null &&
            customCycleStart > 0L
        ) {

            checkDailyPlanAlerts(

                plan =
                    dailyPlan,

                currentCustomBytes =
                    customUsage.totalBytes,

                currentCycleStart =
                    customCycleStart
            )
        }


        // ====================================================
        // STATUS BAR SPEED OVERLAY
        // ====================================================

        val statusBarSpeedEnabled =
            appPreferences.getBoolean(
                KEY_STATUS_BAR_SPEED_ENABLED,
                DEFAULT_STATUS_BAR_SPEED_ENABLED
            )


        if (
            statusBarSpeedEnabled &&
            isMobileDataActive() &&
            isPortraitOrientation()
        ) {

            updateSpeedOverlay(
                speed.first
            )

        } else {

            removeSpeedOverlay()
        }
    }


    // ========================================================
    // CUSTOM CALCULATION RESULT
    // ========================================================

    private data class CustomCalculation(

        val usage:
        DataUsageManager.UsageResult,

        val cycleStart:
        Long
    )


    // ========================================================
    // CUSTOM USAGE
    // ========================================================
    //
    // RULES:
    //
    // 1. Daily Plan OFF -> keep frozen Custom
    // 2. Daily Plan ON -> calculate Custom
    // 3. Midnight -> new cycle
    // 4. Before selected start -> Custom = 0
    // 5. After selected start -> count mobile data
    // 6. Custom cannot exceed Daily Plan total
    //
    // ========================================================

    private fun calculateCustomUsage(

        plan:
        DailyDataPlan?,

        enabled:
        Boolean,

        now:
        Long

    ): CustomCalculation {


        // ====================================================
        // DAILY PLAN OFF
        // ====================================================

        if (
            !enabled ||
            plan == null
        ) {

            markDailyPlanDisabled()

            return CustomCalculation(

                usage =
                    getFrozenCustomUsage(),

                cycleStart =
                    getFrozenCustomCycle()
            )
        }


        // ====================================================
        // TODAY MIDNIGHT
        // ====================================================

        val todayMidnight =
            getTodayStartTime()


        // ====================================================
        // USER SELECTED DAILY START
        // ====================================================

        val todayStart =
            getTodayConfiguredStart(

                hour =
                    plan.startHour,

                minute =
                    plan.startMinute,

                now =
                    now
            )


        // ====================================================
        // BEFORE SELECTED START
        // ====================================================

        if (
            now < todayStart
        ) {

            saveRuntimeState(

                cycle =
                    todayMidnight,

                enabled =
                    true,

                baselineDownload =
                    0L,

                baselineUpload =
                    0L
            )


            saveFrozenCustomUsage(

                usage =
                    emptyUsage(),

                cycleStart =
                    todayMidnight
            )


            return CustomCalculation(

                usage =
                    emptyUsage(),

                cycleStart =
                    0L
            )
        }


        // ====================================================
        // CURRENT DAILY CYCLE
        // ====================================================

        val currentCycleStart =
            todayMidnight


        // ====================================================
        // ACTUAL MOBILE DATA FROM DAILY START
        // ====================================================

        val actualUsage =
            dataRepository.getCustomUsage(
                todayStart
            )


        // ====================================================
        // RUNTIME PREFERENCES
        // ====================================================

        val runtimePreferences =
            getSharedPreferences(
                CUSTOM_RUNTIME_PREFERENCES,
                Context.MODE_PRIVATE
            )


        val runtimeCycle =
            runtimePreferences.getLong(
                KEY_RUNTIME_CYCLE,
                0L
            )


        val runtimeWasEnabled =
            runtimePreferences.getBoolean(
                KEY_RUNTIME_ENABLED,
                false
            )


        val baselineDownload =
            runtimePreferences.getLong(
                KEY_BASELINE_DOWNLOAD,
                0L
            )


        val baselineUpload =
            runtimePreferences.getLong(
                KEY_BASELINE_UPLOAD,
                0L
            )


        val storedFrozen =
            getFrozenCustomUsage()


        val storedFrozenCycle =
            getFrozenCustomCycle()


        // ====================================================
        // NEW MIDNIGHT CYCLE
        // ====================================================

        if (
            runtimeCycle != currentCycleStart
        ) {

            saveRuntimeState(

                cycle =
                    currentCycleStart,

                enabled =
                    true,

                baselineDownload =
                    actualUsage.downloadBytes,

                baselineUpload =
                    actualUsage.uploadBytes
            )


            saveFrozenCustomUsage(

                usage =
                    emptyUsage(),

                cycleStart =
                    currentCycleStart
            )


            return CustomCalculation(

                usage =
                    emptyUsage(),

                cycleStart =
                    currentCycleStart
            )
        }


        // ====================================================
        // DAILY PLAN OFF -> ON
        // ====================================================

        if (
            !runtimeWasEnabled
        ) {

            saveRuntimeState(

                cycle =
                    currentCycleStart,

                enabled =
                    true,

                baselineDownload =
                    actualUsage.downloadBytes,

                baselineUpload =
                    actualUsage.uploadBytes
            )


            val resumedUsage =
                if (
                    storedFrozenCycle ==
                    currentCycleStart
                ) {

                    storedFrozen

                } else {

                    emptyUsage()
                }


            val cappedResumedUsage =
                capUsageToDailyPlan(

                    usage =
                        resumedUsage,

                    dailyLimitBytes =
                        plan.totalDataBytes
                )


            saveFrozenCustomUsage(

                usage =
                    cappedResumedUsage,

                cycleStart =
                    currentCycleStart
            )


            return CustomCalculation(

                usage =
                    cappedResumedUsage,

                cycleStart =
                    currentCycleStart
            )
        }


        // ====================================================
        // NEW DOWNLOAD/UPLOAD SINCE LAST CHECK
        // ====================================================

        val downloadDelta =
            (
                    actualUsage.downloadBytes -
                            baselineDownload
                    )
                .coerceAtLeast(0L)


        val uploadDelta =
            (
                    actualUsage.uploadBytes -
                            baselineUpload
                    )
                .coerceAtLeast(0L)


        // ====================================================
        // OLD FROZEN CUSTOM
        // ====================================================

        val frozenDownload =
            if (
                storedFrozenCycle ==
                currentCycleStart
            ) {

                storedFrozen.downloadBytes

            } else {

                0L
            }


        val frozenUpload =
            if (
                storedFrozenCycle ==
                currentCycleStart
            ) {

                storedFrozen.uploadBytes

            } else {

                0L
            }


        // ====================================================
        // ADD NEW DATA
        // ====================================================

        val finalDownload =
            frozenDownload +
                    downloadDelta


        val finalUpload =
            frozenUpload +
                    uploadDelta


        val rawUsage =
            DataUsageManager.UsageResult(

                downloadBytes =
                    finalDownload,

                uploadBytes =
                    finalUpload,

                totalBytes =
                    finalDownload +
                            finalUpload
            )


        // ====================================================
        // DAILY PLAN HARD LIMIT
        // ====================================================

        val finalUsage =
            capUsageToDailyPlan(

                usage =
                    rawUsage,

                dailyLimitBytes =
                    plan.totalDataBytes
            )


        // ====================================================
        // SAVE NEW BASELINE
        // ====================================================

        saveRuntimeState(

            cycle =
                currentCycleStart,

            enabled =
                true,

            baselineDownload =
                actualUsage.downloadBytes,

            baselineUpload =
                actualUsage.uploadBytes
        )


        // ====================================================
        // SAVE CUSTOM
        // ====================================================

        saveFrozenCustomUsage(

            usage =
                finalUsage,

            cycleStart =
                currentCycleStart
        )


        return CustomCalculation(

            usage =
                finalUsage,

            cycleStart =
                currentCycleStart
        )
    }


    // ========================================================
    // CAP CUSTOM TO DAILY PLAN
    // ========================================================

    private fun capUsageToDailyPlan(

        usage:
        DataUsageManager.UsageResult,

        dailyLimitBytes:
        Long

    ):
            DataUsageManager.UsageResult {


        if (
            dailyLimitBytes <= 0L
        ) {

            return usage
        }


        if (
            usage.totalBytes <=
            dailyLimitBytes
        ) {

            return usage
        }


        val cappedDownload =
            usage.downloadBytes
                .coerceAtMost(
                    dailyLimitBytes
                )


        val remainingBytes =
            (
                    dailyLimitBytes -
                            cappedDownload
                    )
                .coerceAtLeast(0L)


        val cappedUpload =
            usage.uploadBytes
                .coerceAtMost(
                    remainingBytes
                )


        return DataUsageManager.UsageResult(

            downloadBytes =
                cappedDownload,

            uploadBytes =
                cappedUpload,

            totalBytes =
                cappedDownload +
                        cappedUpload
        )
    }


    // ========================================================
    // EMPTY USAGE
    // ========================================================

    private fun emptyUsage():
            DataUsageManager.UsageResult {

        return DataUsageManager.UsageResult(

            downloadBytes =
                0L,

            uploadBytes =
                0L,

            totalBytes =
                0L
        )
    }


    // ========================================================
    // TODAY CONFIGURED START
    // ========================================================

    private fun getTodayConfiguredStart(

        hour:
        Int,

        minute:
        Int,

        now:
        Long

    ): Long {

        val calendar =
            Calendar.getInstance()


        calendar.timeInMillis =
            now


        calendar.set(
            Calendar.HOUR_OF_DAY,
            hour.coerceIn(0, 23)
        )


        calendar.set(
            Calendar.MINUTE,
            minute.coerceIn(0, 59)
        )


        calendar.set(
            Calendar.SECOND,
            0
        )


        calendar.set(
            Calendar.MILLISECOND,
            0
        )


        return calendar.timeInMillis
    }


    // ========================================================
    // TODAY MIDNIGHT
    // ========================================================

    private fun getTodayStartTime(): Long {

        val calendar =
            Calendar.getInstance()


        calendar.set(
            Calendar.HOUR_OF_DAY,
            0
        )


        calendar.set(
            Calendar.MINUTE,
            0
        )


        calendar.set(
            Calendar.SECOND,
            0
        )


        calendar.set(
            Calendar.MILLISECOND,
            0
        )


        return calendar.timeInMillis
    }


    // ========================================================
    // DAILY PLAN DISABLED
    // ========================================================

    private fun markDailyPlanDisabled() {

        getSharedPreferences(
            CUSTOM_RUNTIME_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                KEY_RUNTIME_ENABLED,
                false
            )
            .apply()


        customUsageInitialized =
            false

        previousCustomUsage =
            0L
    }


    // ========================================================
    // SAVE RUNTIME STATE
    // ========================================================

    private fun saveRuntimeState(

        cycle:
        Long,

        enabled:
        Boolean,

        baselineDownload:
        Long,

        baselineUpload:
        Long

    ) {

        getSharedPreferences(
            CUSTOM_RUNTIME_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .edit()
            .putLong(
                KEY_RUNTIME_CYCLE,
                cycle
            )
            .putBoolean(
                KEY_RUNTIME_ENABLED,
                enabled
            )
            .putLong(
                KEY_BASELINE_DOWNLOAD,
                baselineDownload
            )
            .putLong(
                KEY_BASELINE_UPLOAD,
                baselineUpload
            )
            .apply()
    }


    // ========================================================
    // SAVE FROZEN CUSTOM
    // ========================================================

    private fun saveFrozenCustomUsage(

        usage:
        DataUsageManager.UsageResult,

        cycleStart:
        Long

    ) {

        getSharedPreferences(
            FROZEN_CUSTOM_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .edit()
            .putLong(
                KEY_FROZEN_DOWNLOAD,
                usage.downloadBytes
            )
            .putLong(
                KEY_FROZEN_UPLOAD,
                usage.uploadBytes
            )
            .putLong(
                KEY_FROZEN_TOTAL,
                usage.totalBytes
            )
            .putLong(
                KEY_FROZEN_CYCLE,
                cycleStart
            )
            .apply()
    }


    // ========================================================
    // GET FROZEN CUSTOM
    // ========================================================

    private fun getFrozenCustomUsage():
            DataUsageManager.UsageResult {

        val preferences =
            getSharedPreferences(
                FROZEN_CUSTOM_PREFERENCES,
                Context.MODE_PRIVATE
            )


        return DataUsageManager.UsageResult(

            downloadBytes =
                preferences.getLong(
                    KEY_FROZEN_DOWNLOAD,
                    0L
                ),

            uploadBytes =
                preferences.getLong(
                    KEY_FROZEN_UPLOAD,
                    0L
                ),

            totalBytes =
                preferences.getLong(
                    KEY_FROZEN_TOTAL,
                    0L
                )
        )
    }


    // ========================================================
    // GET FROZEN CUSTOM CYCLE
    // ========================================================

    private fun getFrozenCustomCycle(): Long {

        return getSharedPreferences(
            FROZEN_CUSTOM_PREFERENCES,
            Context.MODE_PRIVATE
        )
            .getLong(
                KEY_FROZEN_CYCLE,
                0L
            )
    }


    // ========================================================
    // TODAY DATA LIMIT ALERTS
    // ========================================================

    private fun checkTodayDataLimitAlerts(

        currentTodayBytes:
        Long

    ) {

        val alerts =
            alertStorage.loadAlerts()


        if (
            alerts.isEmpty()
        ) {

            previousTodayUsage =
                currentTodayBytes

            todayAlertUsageInitialized =
                true

            return
        }


        val todayCycleStart =
            getTodayStartTime()


        val runtimePreferences =
            getSharedPreferences(
                CUSTOM_ALERT_RUNTIME_PREFERENCES,
                Context.MODE_PRIVATE
            )


        val savedCycle =
            runtimePreferences.getLong(
                KEY_CUSTOM_ALERT_CYCLE,
                0L
            )


        // ----------------------------------------------------
        // New calendar day
        // ----------------------------------------------------

        if (
            savedCycle != todayCycleStart
        ) {

            runtimePreferences
                .edit()
                .putLong(
                    KEY_CUSTOM_ALERT_CYCLE,
                    todayCycleStart
                )
                .apply()


            previousTodayUsage =
                0L

            todayAlertUsageInitialized =
                true


            for (
            alert in alerts
            ) {

                alertStorage.saveAlert(

                    alert.copy(

                        lastTriggeredThreshold =
                            0L,

                        lastTriggeredCycleStartTime =
                            todayCycleStart
                    )
                )
            }
        }


        // ----------------------------------------------------
        // First reading
        // ----------------------------------------------------

        if (
            !todayAlertUsageInitialized
        ) {

            previousTodayUsage =
                currentTodayBytes

            todayAlertUsageInitialized =
                true

            return
        }


        // ----------------------------------------------------
        // Check alerts
        // ----------------------------------------------------

        for (
        alert in alerts
        ) {

            if (
                !alert.isEnabled ||
                alert.limitBytes <= 0L
            ) {

                continue
            }


            // =================================================
            // REPEAT OFF
            // =================================================

            if (
                !alert.isRepeating
            ) {

                val alreadyTriggered =
                    alert.lastTriggeredCycleStartTime ==
                            todayCycleStart &&
                            alert.lastTriggeredThreshold >=
                            alert.limitBytes


                if (
                    currentTodayBytes >=
                    alert.limitBytes &&
                    !alreadyTriggered
                ) {

                    sendTodayDataLimitAlertNotification(

                        alert =
                            alert,

                        threshold =
                            alert.limitBytes
                    )


                    alertStorage.saveAlert(

                        alert.copy(

                            lastTriggeredThreshold =
                                alert.limitBytes,

                            lastTriggeredCycleStartTime =
                                todayCycleStart
                        )
                    )
                }


            } else {

                // =============================================
                // REPEAT ON
                // =============================================

                val nextThreshold =

                    if (
                        alert.lastTriggeredCycleStartTime ==
                        todayCycleStart &&
                        alert.lastTriggeredThreshold >=
                        alert.limitBytes
                    ) {

                        alert.lastTriggeredThreshold +
                                alert.limitBytes

                    } else {

                        alert.limitBytes
                    }


                if (
                    currentTodayBytes >=
                    nextThreshold
                ) {

                    val reachedThreshold =

                        (
                                currentTodayBytes /
                                        alert.limitBytes
                                ) *
                                alert.limitBytes


                    if (
                        reachedThreshold >
                        alert.lastTriggeredThreshold
                    ) {

                        sendTodayDataLimitAlertNotification(

                            alert =
                                alert,

                            threshold =
                                reachedThreshold
                        )


                        alertStorage.saveAlert(

                            alert.copy(

                                lastTriggeredThreshold =
                                    reachedThreshold,

                                lastTriggeredCycleStartTime =
                                    todayCycleStart
                            )
                        )
                    }
                }
            }
        }


        previousTodayUsage =
            currentTodayBytes
    }


    // ========================================================
    // DAILY PLAN ALERTS
    // ========================================================

    private fun checkDailyPlanAlerts(

        plan:
        DailyDataPlan,

        currentCustomBytes:
        Long,

        currentCycleStart:
        Long

    ) {

        if (
            plan.totalDataBytes <= 0L
        ) {

            return
        }


        // ----------------------------------------------------
        // Reset alerts on new midnight cycle
        // ----------------------------------------------------

        var workingPlan =
            plan


        if (
            plan.currentCycleStartTime !=
            currentCycleStart
        ) {

            val resetAlerts =
                plan.alerts.map {

                    it.copy(
                        isTriggered =
                            false
                    )
                }


            workingPlan =
                plan.copy(

                    alerts =
                        resetAlerts,

                    currentCycleStartTime =
                        currentCycleStart
                )


            dailyPlanStorage.savePlan(
                workingPlan
            )
        }


        // ----------------------------------------------------
        // Check every Daily Alert
        // ----------------------------------------------------

        var changed =
            false


        val updatedAlerts =
            workingPlan.alerts.map {

                    alert ->

                // --------------------------------------------
                // Disabled
                // --------------------------------------------

                if (
                    !alert.isEnabled
                ) {

                    return@map alert
                }


                // --------------------------------------------
                // Already triggered
                // --------------------------------------------

                if (
                    alert.isTriggered
                ) {

                    return@map alert
                }


                // --------------------------------------------
                // Invalid limit
                // --------------------------------------------

                if (
                    alert.limitBytes <= 0L
                ) {

                    return@map alert
                }


                // --------------------------------------------
                // Limit cannot exceed total plan
                // --------------------------------------------

                if (
                    alert.limitBytes >
                    workingPlan.totalDataBytes
                ) {

                    return@map alert
                }


                // --------------------------------------------
                // CURRENT CUSTOM REACHED LIMIT
                // --------------------------------------------

                if (
                    currentCustomBytes >=
                    alert.limitBytes
                ) {

                    sendDailyAlertNotification(
                        alert
                    )


                    changed =
                        true


                    alert.copy(
                        isTriggered =
                            true
                    )

                } else {

                    alert
                }
            }


        // ----------------------------------------------------
        // Save
        // ----------------------------------------------------

        if (
            changed ||
            workingPlan.currentCycleStartTime !=
            plan.currentCycleStartTime
        ) {

            dailyPlanStorage.savePlan(

                workingPlan.copy(

                    alerts =
                        updatedAlerts,

                    currentCycleStartTime =
                        currentCycleStart
                )
            )
        }
    }


    // ========================================================
    // MONITOR NOTIFICATION CHANNEL
    // ========================================================

    private fun createMonitorNotificationChannel() {

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
    // INITIAL FOREGROUND NOTIFICATION
    // ========================================================

    private fun createInitialForegroundNotification():
            Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )


        val pendingIntent =
            PendingIntent.getActivity(

                this,

                0,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        return NotificationCompat.Builder(

            this,

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
    // MONITORING NOTIFICATION
    // ========================================================

    private fun showMonitoringNotification(

        usageLabel:
        String,

        usage:
        String,

        downloadSpeed:
        String,

        uploadSpeed:
        String

    ) {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )


        val pendingIntent =
            PendingIntent.getActivity(

                this,

                0,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        val title =
            "$usageLabel: $usage   " +
                    "$downloadSpeed   " +
                    "$uploadSpeed"


        val notification =
            NotificationCompat.Builder(

                this,

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


        systemNotificationManager.notify(

            MONITOR_NOTIFICATION_ID,

            notification
        )
    }


    // ========================================================
    // ALERT CHANNEL
    // ========================================================

    private fun createAlertNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(

                    ALERT_CHANNEL_ID,

                    "DataDrift Alerts",

                    NotificationManager
                        .IMPORTANCE_HIGH
                )


            channel.description =
                "DataDrift data limit alerts"


            channel.enableVibration(
                true
            )


            systemNotificationManager
                .createNotificationChannel(
                    channel
                )
        }
    }


    // ========================================================
    // TODAY DATA ALERT NOTIFICATION
    // ========================================================

    private fun sendTodayDataLimitAlertNotification(

        alert:
        DataAlert,

        threshold:
        Long

    ) {

        val notificationId =
            CUSTOM_ALERT_NOTIFICATION_BASE +
                    alert.id


        sendAlertNotification(

            notificationId =
                notificationId,

            title =
                "Data limit reached",

            message =
                "Today data reached " +
                        formatAlertAmount(
                            threshold
                        )
        )
    }


    // ========================================================
    // DAILY PLAN ALERT NOTIFICATION
    // ========================================================

    private fun sendDailyAlertNotification(

        alert:
        DailyDataAlert

    ) {

        val notificationId =
            DAILY_ALERT_NOTIFICATION_BASE +
                    alert.id


        sendAlertNotification(

            notificationId =
                notificationId,

            title =
                "Daily Data Plan alert",

            message =
                "Daily data reached " +
                        formatAlertAmount(
                            alert.limitBytes
                        )
        )
    }


    // ========================================================
    // GENERIC ALERT NOTIFICATION
    // ========================================================

    private fun sendAlertNotification(

        notificationId:
        Int,

        title:
        String,

        message:
        String

    ) {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )


        val pendingIntent =
            PendingIntent.getActivity(

                this,

                notificationId,

                intent,

                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )


        val notification =
            NotificationCompat.Builder(

                this,

                ALERT_CHANNEL_ID

            )

                .setSmallIcon(

                    android.R.drawable
                        .ic_dialog_alert
                )

                .setContentTitle(
                    title
                )

                .setContentText(
                    message
                )

                .setContentIntent(
                    pendingIntent
                )

                .setAutoCancel(
                    true
                )

                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )

                .setCategory(
                    NotificationCompat
                        .CATEGORY_ALARM
                )

                .build()


        systemNotificationManager.notify(

            notificationId,

            notification
        )
    }


    // ========================================================
    // SPEED INITIALIZATION
    // ========================================================

    private fun initializeSpeedCounters() {

        previousRxBytes =
            TrafficStats
                .getMobileRxBytes()


        previousTxBytes =
            TrafficStats
                .getMobileTxBytes()


        previousSpeedTime =
            System.currentTimeMillis()


        speedInitialized =
            true
    }


    // ========================================================
    // MOBILE NETWORK SPEED
    // ========================================================

    private fun calculateNetworkSpeed():
            Pair<String, String> {

        val currentRx =
            TrafficStats
                .getMobileRxBytes()


        val currentTx =
            TrafficStats
                .getMobileTxBytes()


        val currentTime =
            System.currentTimeMillis()


        if (
            !speedInitialized ||
            currentRx < 0L ||
            currentTx < 0L ||
            previousSpeedTime <= 0L
        ) {

            previousRxBytes =
                currentRx

            previousTxBytes =
                currentTx

            previousSpeedTime =
                currentTime

            speedInitialized =
                true


            return Pair(
                "0 KB/s",
                "0 KB/s"
            )
        }


        val elapsed =
            currentTime -
                    previousSpeedTime


        if (
            elapsed <= 0L
        ) {

            return Pair(
                "0 KB/s",
                "0 KB/s"
            )
        }


        val seconds =
            elapsed /
                    1000.0


        val downloadBytes =
            (
                    currentRx -
                            previousRxBytes
                    )
                .coerceAtLeast(0L)


        val uploadBytes =
            (
                    currentTx -
                            previousTxBytes
                    )
                .coerceAtLeast(0L)


        val downloadSpeed =
            formatSpeed(
                downloadBytes /
                        seconds
            )


        val uploadSpeed =
            formatSpeed(
                uploadBytes /
                        seconds
            )


        previousRxBytes =
            currentRx


        previousTxBytes =
            currentTx


        previousSpeedTime =
            currentTime


        return Pair(
            downloadSpeed,
            uploadSpeed
        )
    }


    // ========================================================
    // FORMAT SPEED
    // ========================================================

    private fun formatSpeed(
        bytesPerSecond:
        Double
    ): String {

        val kb =
            1024.0

        val mb =
            1024.0 *
                    1024.0

        val gb =
            1024.0 *
                    1024.0 *
                    1024.0


        return when {

            bytesPerSecond >= gb ->

                String.format(
                    Locale.US,
                    "%.2f GB/s",
                    bytesPerSecond / gb
                )


            bytesPerSecond >= mb ->

                String.format(
                    Locale.US,
                    "%.2f MB/s",
                    bytesPerSecond / mb
                )


            else ->

                String.format(
                    Locale.US,
                    "%.0f KB/s",
                    bytesPerSecond / kb
                )
        }
    }


    // ========================================================
    // STATUS BAR SPEED OVERLAY
    // ========================================================

    private fun updateSpeedOverlay(

        downloadSpeed:
        String

    ) {

        // ----------------------------------------------------
        // Mobile data only
        // ----------------------------------------------------

        if (
            !isMobileDataActive()
        ) {

            removeSpeedOverlay()

            return
        }


        // ----------------------------------------------------
        // Portrait only
        // ----------------------------------------------------

        if (
            !isPortraitOrientation()
        ) {

            removeSpeedOverlay()

            return
        }


        // ----------------------------------------------------
        // Overlay permission
        // ----------------------------------------------------

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(
                this
            )
        ) {

            removeSpeedOverlay()

            return
        }


        // ----------------------------------------------------
        // User setting
        // ----------------------------------------------------

        val preferences =
            getSharedPreferences(
                APP_SETTINGS,
                Context.MODE_PRIVATE
            )


        val enabled =
            preferences.getBoolean(
                KEY_STATUS_BAR_SPEED_ENABLED,
                DEFAULT_STATUS_BAR_SPEED_ENABLED
            )


        if (!enabled) {

            removeSpeedOverlay()

            return
        }


        // ----------------------------------------------------
        // Create if required
        // ----------------------------------------------------

        if (
            speedTextView == null
        ) {

            createSpeedOverlay()
        }


        // ----------------------------------------------------
        // Apply current settings
        // ----------------------------------------------------

        val x =
            preferences.getInt(
                KEY_STATUS_BAR_SPEED_X,
                DEFAULT_STATUS_BAR_SPEED_X
            )


        val y =
            preferences.getInt(
                KEY_STATUS_BAR_SPEED_Y,
                DEFAULT_STATUS_BAR_SPEED_Y
            )


        val textSize =
            preferences.getFloat(
                KEY_STATUS_BAR_SPEED_TEXT_SIZE,
                DEFAULT_STATUS_BAR_SPEED_TEXT_SIZE
            )


        speedTextView?.text =
            downloadSpeed


        speedTextView?.textSize =
            textSize


        // ----------------------------------------------------
        // Update X/Y without recreating overlay
        // ----------------------------------------------------

        val params =
            speedTextView?.layoutParams
                    as? WindowManager.LayoutParams


        if (
            params != null
        ) {

            params.x =
                x

            params.y =
                y


            try {

                windowManager?.updateViewLayout(
                    speedTextView,
                    params
                )

            } catch (_: Exception) {

                // Ignore layout update errors.
            }
        }
    }


    // ========================================================
    // CREATE STATUS BAR OVERLAY
    // ========================================================

    private fun createSpeedOverlay() {

        if (
            speedTextView != null
        ) {

            return
        }


        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(
                this
            )
        ) {

            return
        }


        val preferences =
            getSharedPreferences(
                APP_SETTINGS,
                Context.MODE_PRIVATE
            )


        val x =
            preferences.getInt(
                KEY_STATUS_BAR_SPEED_X,
                DEFAULT_STATUS_BAR_SPEED_X
            )


        val y =
            preferences.getInt(
                KEY_STATUS_BAR_SPEED_Y,
                DEFAULT_STATUS_BAR_SPEED_Y
            )


        val textSize =
            preferences.getFloat(
                KEY_STATUS_BAR_SPEED_TEXT_SIZE,
                DEFAULT_STATUS_BAR_SPEED_TEXT_SIZE
            )


        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        val textView =
            TextView(this)


        // ----------------------------------------------------
        // No arrow
        // ----------------------------------------------------

        textView.text =
            "0 KB/s"


        // ----------------------------------------------------
        // User selected text size
        // ----------------------------------------------------

        textView.textSize =
            textSize


        textView.setTextColor(
            Color.WHITE
        )


        textView.setBackgroundColor(
            Color.TRANSPARENT
        )


        textView.setPadding(
            0,
            0,
            0,
            0
        )


        // ====================================================
        // WINDOW TYPE
        // ====================================================

        val layoutType =

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager
                    .LayoutParams
                    .TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")

                WindowManager
                    .LayoutParams
                    .TYPE_PHONE
            }


        // ====================================================
        // LAYOUT PARAMS
        // ====================================================

        val layoutParams =
            WindowManager.LayoutParams(

                WindowManager
                    .LayoutParams
                    .WRAP_CONTENT,

                WindowManager
                    .LayoutParams
                    .WRAP_CONTENT,

                layoutType,

                WindowManager
                    .LayoutParams
                    .FLAG_NOT_FOCUSABLE or

                        WindowManager
                            .LayoutParams
                            .FLAG_NOT_TOUCHABLE or

                        WindowManager
                            .LayoutParams
                            .FLAG_LAYOUT_IN_SCREEN or

                        WindowManager
                            .LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS,

                PixelFormat.TRANSLUCENT
            )


        layoutParams.gravity =
            Gravity.TOP or
                    Gravity.START


        // ====================================================
        // USER POSITION
        // ====================================================

        layoutParams.x =
            x

        layoutParams.y =
            y


        // ====================================================
        // ADD OVERLAY
        // ====================================================

        try {

            windowManager?.addView(

                textView,

                layoutParams
            )


            speedTextView =
                textView

        } catch (_: Exception) {

            speedTextView =
                null
        }
    }


    // ========================================================
    // REMOVE STATUS BAR OVERLAY
    // ========================================================

    private fun removeSpeedOverlay() {

        val view =
            speedTextView
                ?: return


        try {

            windowManager
                ?.removeView(
                    view
                )

        } catch (_: Exception) {

            // Ignore removal errors.
        }


        speedTextView =
            null
    }


    // ========================================================
    // MOBILE DATA ACTIVE CHECK
    // ========================================================

    private fun isMobileDataActive():
            Boolean {

        val connectivityManager =
            getSystemService(
                CONNECTIVITY_SERVICE
            ) as ConnectivityManager


        val network =
            connectivityManager
                .activeNetwork
                ?: return false


        val capabilities =
            connectivityManager
                .getNetworkCapabilities(
                    network
                )
                ?: return false


        return capabilities.hasTransport(
            NetworkCapabilities
                .TRANSPORT_CELLULAR
        )
    }


    // ========================================================
    // PORTRAIT CHECK
    // ========================================================

    private fun isPortraitOrientation():
            Boolean {

        return resources
            .configuration
            .orientation ==
                android.content.res.Configuration
                    .ORIENTATION_PORTRAIT
    }


    // ========================================================
    // FORMAT ALERT AMOUNT
    // ========================================================

    private fun formatAlertAmount(
        bytes:
        Long
    ): String {

        val mb =
            1024L *
                    1024L


        val gb =
            1024L *
                    1024L *
                    1024L


        return if (
            bytes >= gb
        ) {

            String.format(

                Locale.US,

                "%.2f GB",

                bytes /
                        (
                                1024.0 *
                                        1024.0 *
                                        1024.0
                                )
            )

        } else {

            "${bytes / mb} MB"
        }
    }


    // ========================================================
    // CONSTANTS
    // ========================================================

    companion object {

        // ----------------------------------------------------
        // Monitoring
        // ----------------------------------------------------

        private const val MONITOR_INTERVAL =
            3000L


        private const val APP_SETTINGS =
            "datadrift_app_settings"


        private const val KEY_MONITORING_ENABLED =
            "monitoring_enabled"


        // ----------------------------------------------------
        // STATUS BAR SPEED SETTINGS
        // ----------------------------------------------------
        //
        // These four constants are PUBLIC because
        // MainActivity will use the same keys.
        //
        // ----------------------------------------------------

        const val KEY_STATUS_BAR_SPEED_ENABLED =
            "status_bar_speed_enabled"


        const val KEY_STATUS_BAR_SPEED_X =
            "status_bar_speed_x"


        const val KEY_STATUS_BAR_SPEED_Y =
            "status_bar_speed_y"


        const val KEY_STATUS_BAR_SPEED_TEXT_SIZE =
            "status_bar_speed_text_size"


        // ----------------------------------------------------
        // Default overlay settings
        // ----------------------------------------------------

        const val DEFAULT_STATUS_BAR_SPEED_ENABLED =
            true


        const val DEFAULT_STATUS_BAR_SPEED_X =
            420


        const val DEFAULT_STATUS_BAR_SPEED_Y =
            6


        const val DEFAULT_STATUS_BAR_SPEED_TEXT_SIZE =
            12f


        // ----------------------------------------------------
        // Monitor notification
        // ----------------------------------------------------

        private const val MONITOR_CHANNEL_ID =
            "datadrift_monitor_channel"


        private const val MONITOR_NOTIFICATION_ID =
            1001


        // ----------------------------------------------------
        // Alert notification
        // ----------------------------------------------------

        private const val ALERT_CHANNEL_ID =
            "datadrift_alerts"


        private const val CUSTOM_ALERT_NOTIFICATION_BASE =
            3000


        private const val DAILY_ALERT_NOTIFICATION_BASE =
            5000


        // ----------------------------------------------------
        // Custom runtime
        // ----------------------------------------------------

        private const val CUSTOM_RUNTIME_PREFERENCES =
            "datadrift_custom_runtime"


        private const val KEY_RUNTIME_CYCLE =
            "custom_runtime_cycle"


        private const val KEY_RUNTIME_ENABLED =
            "custom_runtime_enabled"


        private const val KEY_BASELINE_DOWNLOAD =
            "custom_baseline_download"


        private const val KEY_BASELINE_UPLOAD =
            "custom_baseline_upload"


        // ----------------------------------------------------
        // Frozen Custom
        // ----------------------------------------------------

        private const val FROZEN_CUSTOM_PREFERENCES =
            "datadrift_custom_frozen"


        private const val KEY_FROZEN_DOWNLOAD =
            "download_bytes"


        private const val KEY_FROZEN_UPLOAD =
            "upload_bytes"


        private const val KEY_FROZEN_TOTAL =
            "total_bytes"


        private const val KEY_FROZEN_CYCLE =
            "cycle_start"


        // ----------------------------------------------------
        // Alert runtime
        // ----------------------------------------------------

        private const val CUSTOM_ALERT_RUNTIME_PREFERENCES =
            "datadrift_custom_alert_runtime"


        private const val KEY_CUSTOM_ALERT_CYCLE =
            "custom_alert_cycle"
    }
}