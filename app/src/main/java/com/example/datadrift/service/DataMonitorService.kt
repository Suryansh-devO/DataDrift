package com.example.datadrift.service

// ============================================================
// DataDrift - DataMonitorService
// ============================================================
//
// Handles:
// 1. Background monitoring
// 2. Today / Total usage
// 3. Custom usage
// 4. Custom freeze when Daily Plan is OFF
// 5. Custom resume when Daily Plan is ON
// 6. Today Data Limit Alerts
// 7. Today Repeat Alerts
// 8. Daily Data Plan Alerts
// 9. Daily cycle reset
// 10. Persistent monitoring notification
// 11. Live download/upload speed
// 12. Status-bar download speed overlay
//
// IMPORTANT:
//
// Data Limit Alerts:
//
//     TODAY -> Data Limit Alerts
//
// Daily Data Plan:
//
//     CUSTOM -> Daily Data Plan
//
// These two systems are completely separate.
//
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

    private lateinit var dataUsageManager:
            DataUsageManager

    private lateinit var dataRepository:
            DataRepository

    private lateinit var alertStorage:
            DataAlertStorage

    private lateinit var dailyPlanStorage:
            DailyDataPlanStorage

    private lateinit var systemNotificationManager:
            NotificationManager


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

                    // Keep service alive if
                    // one monitoring cycle fails.

                }

                handler.postDelayed(
                    this,
                    MONITOR_INTERVAL
                )
            }
        }


    // ========================================================
    // SPEED STATE
    // ========================================================

    private var previousRxBytes =
        0L

    private var previousTxBytes =
        0L

    private var previousSpeedTime =
        0L

    private var speedInitialized =
        false


    // ========================================================
    // OVERLAY
    // ========================================================

    private var windowManager:
            WindowManager? = null

    private var speedTextView:
            TextView? = null


    // ========================================================
    // TODAY ALERT RUNTIME
    // ========================================================
    //
    // IMPORTANT:
    //
    // These values belong to TODAY Data Limit Alerts.
    //
    // They do NOT belong to Custom or Daily Plan.
    //
    // ========================================================

    private var previousTodayUsage =
        0L

    private var todayAlertUsageInitialized =
        false


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
        // Foreground service
        // ----------------------------------------------------

        startForeground(

            MONITOR_NOTIFICATION_ID,

            createInitialForegroundNotification()
        )


        // ----------------------------------------------------
        // Speed
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
        // Check monitoring switch
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
        // TODAY
        // ====================================================
        //
        // This is the source for:
        //
        // TODAY -> DATA LIMIT ALERTS
        //
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
        // CUSTOM
        // ====================================================
        //
        // Custom remains connected only to Daily Data Plan.
        //
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
        // SPEED
        // ====================================================

        val speed =
            calculateNetworkSpeed()


        // ====================================================
        // MONITORING NOTIFICATION
        // ====================================================

        // ----------------------------------------------------
        // Daily Plan OFF
        //     -> Total
        //
        // Daily Plan ON but start time not reached
        //     -> Total
        //
        // Daily Plan ON and start time reached
        //     -> Custom
        // ----------------------------------------------------

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
        //
        // VERY IMPORTANT:
        //
        // Data Limit Alerts use TODAY usage.
        //
        // They do NOT use Custom usage.
        //
        // They do NOT depend on Daily Data Plan.
        //
        // ====================================================

        checkTodayDataLimitAlerts(

            currentTodayBytes =
                todayUsage.totalBytes
        )


        // ====================================================
        // DAILY PLAN ALERTS
        // ====================================================
        //
        // Daily Data Plan uses Custom usage.
        //
        // This remains completely separate from
        // Today Data Limit Alerts.
        //
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
        // STATUS BAR OVERLAY
        // ====================================================

        if (
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
    // IMPORTANT:
    //
    // Custom is ONLY controlled by Daily Data Plan.
    //
    // Data Limit Alerts do NOT use this function.
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
        // TODAY'S SELECTED START TIME
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
        // BEFORE TODAY'S START
        // ====================================================

        if (
            now <
            todayStart
        ) {

            return CustomCalculation(

                usage =
                    emptyUsage(),

                cycleStart =
                    0L
            )
        }


        // ====================================================
        // CURRENT CYCLE
        // ====================================================

        val currentCycleStart =
            todayStart


        // ====================================================
        // ACTUAL NETWORKSTATS
        // ====================================================

        val actualUsage =
            dataRepository.getCustomUsage(
                currentCycleStart
            )


        // ====================================================
        // RUNTIME STORAGE
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
        // CASE 1
        // NEW DAILY CYCLE
        // ====================================================

        if (
            runtimeCycle !=
            currentCycleStart
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
        // CASE 2
        // OFF -> ON
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

            /*
             * Keep frozen Custom value exactly as it is.
             */

            val resumedUsage =
                if (
                    storedFrozenCycle ==
                    currentCycleStart
                ) {

                    storedFrozen

                } else {

                    emptyUsage()
                }

            saveFrozenCustomUsage(

                usage =
                    resumedUsage,

                cycleStart =
                    currentCycleStart
            )

            return CustomCalculation(

                usage =
                    resumedUsage,

                cycleStart =
                    currentCycleStart
            )
        }


        // ====================================================
        // CASE 3
        // DAILY PLAN ALREADY ON
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

        val totalDelta =
            downloadDelta +
                    uploadDelta


        // ====================================================
        // ADD ONLY NEW DATA
        // ====================================================

        val frozenTotal =
            if (
                storedFrozenCycle ==
                currentCycleStart
            ) {

                storedFrozen.totalBytes

            } else {

                0L
            }

        val finalTotal =
            frozenTotal +
                    totalDelta


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


        val finalDownload =
            frozenDownload +
                    downloadDelta

        val finalUpload =
            frozenUpload +
                    uploadDelta


        val finalUsage =
            DataUsageManager.UsageResult(

                downloadBytes =
                    finalDownload,

                uploadBytes =
                    finalUpload,

                totalBytes =
                    finalTotal
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
        // SAVE CURRENT CUSTOM
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
    // TODAY'S CONFIGURED START
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

            hour.coerceIn(
                0,
                23
            )
        )

        calendar.set(

            Calendar.MINUTE,

            minute.coerceIn(
                0,
                59
            )
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
    // MARK DAILY PLAN DISABLED
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

        /*
         * Do NOT delete frozen Custom.
         *
         * It is required when Daily Plan is enabled again.
         */
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
    // LOAD FROZEN CUSTOM
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
    // LOAD FROZEN CYCLE
    // ========================================================

    private fun getFrozenCustomCycle():
            Long {

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
    //
    // IMPORTANT:
    //
    // TODAY -> DATA LIMIT ALERTS
    //
    // This function NEVER uses Custom usage.
    //
    // Daily Plan ON/OFF has NO effect on this function.
    //
    // Cycle:
    //
    //     12:00 AM -> next 12:00 AM
    //
    // Repeat OFF:
    //
    //     Alert only once per day.
    //
    // Repeat ON:
    //
    //     2 MB
    //     4 MB
    //     6 MB
    //     8 MB
    //     ...
    //
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

            todayAlertUsageInitialized =
                false

            previousTodayUsage =
                currentTodayBytes

            return
        }


        // ====================================================
        // TODAY CYCLE
        // ====================================================

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


        // ====================================================
        // NEW DAY
        // ====================================================

        if (
            savedCycle !=
            todayCycleStart
        ) {

            runtimePreferences
                .edit()
                .putLong(

                    KEY_CUSTOM_ALERT_CYCLE,

                    todayCycleStart
                )
                .apply()


            // Start today's alert tracking
            // from zero.

            todayAlertUsageInitialized =
                true

            previousTodayUsage =
                0L


            /*
             * Reset all Data Limit Alert thresholds
             * for the new calendar day.
             */

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


        // ====================================================
        // FIRST READING
        // ====================================================
        //
        // Start from zero.
        //
        // This is important if:
        //
        // Today = 30 MB
        // Alert = 2 MB
        //
        // The system should not silently ignore
        // the already reached threshold.
        //
        // ====================================================

        if (
            !todayAlertUsageInitialized
        ) {

            previousTodayUsage =
                0L

            todayAlertUsageInitialized =
                true
        }


        // ====================================================
        // CHECK EVERY ALERT
        // ====================================================

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
            //
            // One alert per calendar day.
            //
            // =================================================

            if (
                !alert.isRepeating
            ) {

                val alreadyTriggered =
                    alert.lastTriggeredCycleStartTime ==
                            todayCycleStart &&
                            alert.lastTriggeredThreshold >=
                            alert.limitBytes


                val crossed =
                    previousTodayUsage <
                            alert.limitBytes &&
                            currentTodayBytes >=
                            alert.limitBytes


                /*
                 * If the alert was created after the
                 * threshold was already crossed, the
                 * first-reading logic still allows it.
                 */

                val reachedOnFirstCheck =
                    previousTodayUsage == 0L &&
                            currentTodayBytes >=
                            alert.limitBytes


                if (
                    (
                            crossed ||
                                    reachedOnFirstCheck
                            ) &&
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

                // =================================================
                // REPEAT ON
                // =================================================
                //
                // Example:
                //
                // Limit = 2 MB
                //
                // 2 MB  -> alert
                // 4 MB  -> alert
                // 6 MB  -> alert
                // 8 MB  -> alert
                //
                // =================================================

                val sameCycle =
                    alert.lastTriggeredCycleStartTime ==
                            todayCycleStart


                val nextThreshold =
                    if (
                        sameCycle &&
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

                    /*
                     * Find the latest threshold that has
                     * already been reached.
                     *
                     * Example:
                     *
                     * Current = 849 MB
                     * Limit = 2 MB
                     *
                     * Reached threshold = 848 MB
                     */

                    val reachedThreshold =
                        (
                                currentTodayBytes /
                                        alert.limitBytes
                                ) *
                                alert.limitBytes


                    /*
                     * Do not send the same threshold twice.
                     */

                    if (
                        reachedThreshold >
                        alert.lastTriggeredThreshold ||
                        !sameCycle
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


        // ====================================================
        // SAVE CURRENT TODAY USAGE
        // ====================================================

        previousTodayUsage =
            currentTodayBytes
    }


    // ========================================================
    // TODAY START TIME
    // ========================================================

    private fun getTodayStartTime():
            Long {

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
    // DAILY PLAN ALERTS
    // ========================================================
    //
    // IMPORTANT:
    //
    // CUSTOM -> DAILY DATA PLAN
    //
    // This function uses the Custom value calculated above.
    //
    // It does NOT use Today.
    //
    // ========================================================

    private fun checkDailyPlanAlerts(

        plan:
        DailyDataPlan,

        currentCustomBytes:
        Long,

        currentCycleStart:
        Long

    ) {

        // ====================================================
        // INVALID PLAN
        // ====================================================

        if (
            plan.totalDataBytes <= 0L
        ) {

            return
        }


        // ====================================================
        // RESET ALERTS FOR NEW CUSTOM CYCLE
        // ====================================================

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


        // ====================================================
        // CHECK ALERTS
        // ====================================================

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
                // Limit cannot exceed Daily Plan
                // --------------------------------------------

                if (
                    alert.limitBytes >
                    workingPlan.totalDataBytes
                ) {

                    return@map alert
                }


                // --------------------------------------------
                // CUSTOM VALUE
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


        // ====================================================
        // SAVE
        // ====================================================

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
    // MONITORING NOTIFICATION CHANNEL
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
    // TODAY DATA LIMIT ALERT
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
    // DAILY PLAN ALERT
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
    // NETWORK SPEED
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


        val rxDifference =
            (
                    currentRx -
                            previousRxBytes
                    )
                .coerceAtLeast(0L)


        val txDifference =
            (
                    currentTx -
                            previousTxBytes
                    )
                .coerceAtLeast(0L)


        val seconds =
            elapsed / 1000.0


        val downloadBytesPerSecond =
            (
                    rxDifference /
                            seconds
                    )
                .toLong()


        val uploadBytesPerSecond =
            (
                    txDifference /
                            seconds
                    )
                .toLong()


        previousRxBytes =
            currentRx

        previousTxBytes =
            currentTx

        previousSpeedTime =
            currentTime


        return Pair(

            formatSpeed(
                downloadBytesPerSecond
            ),

            formatSpeed(
                uploadBytesPerSecond
            )
        )
    }


    // ========================================================
    // SPEED FORMAT
    // ========================================================

    private fun formatSpeed(
        bytesPerSecond:
        Long
    ): String {

        if (
            bytesPerSecond <= 0L
        ) {

            return "0 KB/s"
        }


        val kb =
            1024.0

        val mb =
            kb * 1024.0

        val gb =
            mb * 1024.0


        return when {

            bytesPerSecond >= gb ->

                String.format(

                    Locale.US,

                    "%.2f GB/s",

                    bytesPerSecond /
                            gb
                )


            bytesPerSecond >= mb ->

                String.format(

                    Locale.US,

                    "%.2f MB/s",

                    bytesPerSecond /
                            mb
                )


            else ->

                String.format(

                    Locale.US,

                    "%.0f KB/s",

                    bytesPerSecond /
                            kb
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
        // Create overlay if required
        // ----------------------------------------------------

        if (
            speedTextView == null
        ) {

            createSpeedOverlay()
        }


        // ----------------------------------------------------
        // NO ARROW
        // ----------------------------------------------------

        speedTextView?.text =
            downloadSpeed
    }


    // ========================================================
    // CREATE SPEED OVERLAY
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


        windowManager =
            getSystemService(
                WINDOW_SERVICE
            ) as WindowManager


        val textView =
            TextView(this)


        // No arrow in initial value.
        textView.text =
            "0 KB/s"


        textView.textSize =
            12f


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


        // ----------------------------------------------------
        // Window type
        // ----------------------------------------------------

        val layoutType =

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                WindowManager.LayoutParams
                    .TYPE_APPLICATION_OVERLAY

            } else {

                @Suppress("DEPRECATION")

                WindowManager.LayoutParams
                    .TYPE_PHONE
            }


        // ----------------------------------------------------
        // Layout params
        // ----------------------------------------------------

        val layoutParams =
            WindowManager.LayoutParams(

                WindowManager.LayoutParams
                    .WRAP_CONTENT,

                WindowManager.LayoutParams
                    .WRAP_CONTENT,

                layoutType,

                WindowManager.LayoutParams
                    .FLAG_NOT_FOCUSABLE or

                        WindowManager.LayoutParams
                            .FLAG_NOT_TOUCHABLE or

                        WindowManager.LayoutParams
                            .FLAG_LAYOUT_IN_SCREEN or

                        WindowManager.LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS,

                PixelFormat.TRANSLUCENT
            )


        layoutParams.gravity =
            Gravity.TOP or
                    Gravity.START


        // ----------------------------------------------------
        // Position
        // ----------------------------------------------------

        layoutParams.x =
            420

        layoutParams.y =
            6


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
    // REMOVE SPEED OVERLAY
    // ========================================================

    private fun removeSpeedOverlay() {

        val view =
            speedTextView
                ?: return


        try {

            windowManager
                ?.removeView(view)

        } catch (_: Exception) {

            // Ignore removal errors.
        }


        speedTextView =
            null
    }


    // ========================================================
    // MOBILE DATA ACTIVE
    // ========================================================

    private fun isMobileDataActive():
            Boolean {

        val connectivityManager =
            getSystemService(
                CONNECTIVITY_SERVICE
            ) as ConnectivityManager


        val network =
            connectivityManager.activeNetwork
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

        val gb =
            1024L *
                    1024L *
                    1024L


        if (
            bytes >= gb
        ) {

            val value =
                bytes /
                        (
                                1024.0 *
                                        1024.0 *
                                        1024.0
                                )


            return String.format(

                Locale.US,

                "%.2f GB",

                value
            )
        }


        val mb =
            bytes /
                    (
                            1024L *
                                    1024L
                            )


        return "$mb MB"
    }


    // ========================================================
    // CONSTANTS
    // ========================================================

    companion object {

        private const val MONITOR_INTERVAL =
            3000L


        // ----------------------------------------------------
        // App settings
        // ----------------------------------------------------

        private const val APP_SETTINGS =
            "datadrift_app_settings"

        private const val KEY_MONITORING_ENABLED =
            "monitoring_enabled"


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


        /*
         * Existing ID name is kept so previously saved
         * alert IDs continue to work.
         */
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
        // Today Data Limit Alert runtime
        // ----------------------------------------------------
        //
        // Existing preference name is retained so we don't
        // unnecessarily create another storage system.
        //
        // The stored cycle is now TODAY's midnight cycle.
        //
        // ----------------------------------------------------

        private const val CUSTOM_ALERT_RUNTIME_PREFERENCES =
            "datadrift_custom_alert_runtime"

        private const val KEY_CUSTOM_ALERT_CYCLE =
            "custom_alert_cycle"
    }
}