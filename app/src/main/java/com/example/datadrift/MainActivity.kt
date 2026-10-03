
package com.example.datadrift
// YSR
// ============================================================
// DataDrift - MainActivity
// ============================================================
//
// FEATURES
//
// 1. Monitoring ON / OFF
// 2. Today Usage
// 3. Custom Usage
// 4. Wi-Fi Usage
// 5. Live Wi-Fi Speed
// 6. Live Mobile Speed
// 7. Status Bar Speed ON / OFF
// 8. Status Bar X Position
// 9. Status Bar Y Position
// 10. Status Bar Text Size
// 11. Data Limit Alerts
// 12. Repeat Alerts
// 13. Daily Data Plan
// 14. Daily Alerts
// 15. Daily Start Time
// 16. Monthly Usage
// 17. Weekly Usage
// 18. Usage Access Permission
// 19. Overlay Permission
//
// ============================================================


// ============================================================
// IMPORTS
// ============================================================

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager

import android.content.Context
import android.content.Intent

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats

import android.os.Build
import android.os.Bundle

import android.provider.Settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.core.content.ContextCompat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.datadrift.data.DataAlertStorage
import com.example.datadrift.data.DataRepository
import com.example.datadrift.data.DataUsageManager
import com.example.datadrift.data.DailyDataPlanStorage

import com.example.datadrift.model.DataAlert
import com.example.datadrift.model.DailyDataAlert
import com.example.datadrift.model.DailyDataPlan

import com.example.datadrift.service.DataMonitorService
import com.example.datadrift.ui.theme.DataDriftTheme

import kotlinx.coroutines.delay

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        val dataUsageManager =
            DataUsageManager(this)

        val dataRepository =
            DataRepository(
                dataUsageManager
            )

        val alertStorage =
            DataAlertStorage(this)

        val dailyPlanStorage =
            DailyDataPlanStorage(this)

        setContent {

            DataDriftTheme {

                DataDriftScreen(

                    dataRepository =
                        dataRepository,

                    alertStorage =
                        alertStorage,

                    dailyPlanStorage =
                        dailyPlanStorage
                )
            }
        }
    }
}


// ============================================================
// MAIN SCREEN
// ============================================================

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun DataDriftScreen(

    dataRepository:
    DataRepository,

    alertStorage:
    DataAlertStorage,

    dailyPlanStorage:
    DailyDataPlanStorage

) {

    val context =
        LocalContext.current


    // ========================================================
    // PREFERENCES
    // ========================================================

    val appPreferences =
        remember {

            context.getSharedPreferences(

                "datadrift_app_settings",

                Context.MODE_PRIVATE
            )
        }


    val frozenPreferences =
        remember {

            context.getSharedPreferences(

                "datadrift_custom_frozen",

                Context.MODE_PRIVATE
            )
        }


    // ========================================================
    // MONITORING
    // ========================================================

    var monitoringEnabled by remember {

        mutableStateOf(

            appPreferences.getBoolean(

                "monitoring_enabled",

                true
            )
        )
    }


    // ========================================================
    // USAGE
    // ========================================================

    var todayUsage by remember {

        mutableStateOf(
            emptyUsage()
        )
    }


    var customUsage by remember {

        mutableStateOf(
            emptyUsage()
        )
    }


    var monthlyUsage by remember {

        mutableStateOf(
            emptyUsage()
        )
    }


    // ========================================================
    // WI-FI USAGE
    // ========================================================

    var wifiUsage by remember {

        mutableStateOf(
            emptyUsage()
        )
    }


    // ========================================================
    // WEEKLY
    // ========================================================

    var weeklyUsage by remember {

        mutableStateOf(

            emptyList<
                    DataRepository.WeeklyDayUsage
                    >()
        )
    }


    var weeklyExpanded by remember {

        mutableStateOf(false)
    }


    // ========================================================
    // DAILY ALERTS EXPANDED / COLLAPSED
    // ========================================================

    var dailyAlertsExpanded by remember {

        mutableStateOf(false)
    }


    // ========================================================
    // MOBILE LIVE SPEED
    // ========================================================

    var downloadSpeed by remember {

        mutableStateOf(
            "0 KB/s"
        )
    }


    var uploadSpeed by remember {

        mutableStateOf(
            "0 KB/s"
        )
    }


    // ========================================================
    // WI-FI LIVE SPEED
    // ========================================================

    var wifiDownloadSpeed by remember {

        mutableStateOf(
            "0 KB/s"
        )
    }


    var wifiUploadSpeed by remember {

        mutableStateOf(
            "0 KB/s"
        )
    }


    // ========================================================
    // PERMISSIONS
    // ========================================================

    var hasUsageAccess by remember {

        mutableStateOf(

            dataRepository.hasUsageAccess()
        )
    }


    var hasOverlayPermission by remember {

        mutableStateOf(

            checkOverlayPermission(
                context
            )
        )
    }


    // ========================================================
    // STATUS BAR SPEED SETTINGS
    // ========================================================

    var statusBarSpeedEnabled by remember {

        mutableStateOf(

            appPreferences.getBoolean(

                DataMonitorService
                    .KEY_STATUS_BAR_SPEED_ENABLED,

                DataMonitorService
                    .DEFAULT_STATUS_BAR_SPEED_ENABLED
            )
        )
    }


    var showStatusBarSettings by remember {

        mutableStateOf(false)
    }


    var statusBarXText by remember {

        mutableStateOf(

            appPreferences
                .getInt(

                    DataMonitorService
                        .KEY_STATUS_BAR_SPEED_X,

                    DataMonitorService
                        .DEFAULT_STATUS_BAR_SPEED_X
                )
                .toString()
        )
    }


    var statusBarYText by remember {

        mutableStateOf(

            appPreferences
                .getInt(

                    DataMonitorService
                        .KEY_STATUS_BAR_SPEED_Y,

                    DataMonitorService
                        .DEFAULT_STATUS_BAR_SPEED_Y
                )
                .toString()
        )
    }


    var statusBarTextSizeText by remember {

        mutableStateOf(

            appPreferences
                .getFloat(

                    DataMonitorService
                        .KEY_STATUS_BAR_SPEED_TEXT_SIZE,

                    DataMonitorService
                        .DEFAULT_STATUS_BAR_SPEED_TEXT_SIZE
                )
                .toString()
        )
    }


    // ========================================================
    // DATA LIMIT ALERTS
    // ========================================================

    val alerts =
        remember {

            mutableStateListOf<DataAlert>()
        }


    var showAddAlertDialog by remember {

        mutableStateOf(false)
    }


    var customLimitText by remember {

        mutableStateOf("")
    }


    var customLimitUnit by remember {

        mutableStateOf("MB")
    }


    // ========================================================
    // DAILY PLAN
    // ========================================================

    var dailyPlanEnabled by remember {

        mutableStateOf(false)
    }


    var dailyPlanSaved by remember {

        mutableStateOf(false)
    }


    var dailyTotalText by remember {

        mutableStateOf("")
    }


    var dailyTotalUnit by remember {

        mutableStateOf("GB")
    }


    var dailyStartHour by remember {

        mutableStateOf(0)
    }


    var dailyStartMinute by remember {

        mutableStateOf(0)
    }


    val dailyAlerts =
        remember {

            mutableStateListOf<DailyDataAlert>()
        }


    // ========================================================
    // DAILY ALERT DIALOG
    // ========================================================

    var showDailyAlertDialog by remember {

        mutableStateOf(false)
    }


    var dailyAlertText by remember {

        mutableStateOf("")
    }


    var dailyAlertUnit by remember {

        mutableStateOf("MB")
    }


    var dailyAlertError by remember {

        mutableStateOf("")
    }


    // ========================================================
    // DAILY START TIME DIALOG
    // ========================================================

    var showDailyStartDialog by remember {

        mutableStateOf(false)
    }


    var dailyHourText by remember {

        mutableStateOf("12")
    }


    var dailyMinuteText by remember {

        mutableStateOf("00")
    }


    var dailyPeriod by remember {

        mutableStateOf("AM")
    }


    // ========================================================
    // LOAD SAVED DATA
    // ========================================================

    LaunchedEffect(Unit) {

        // ----------------------------------------------------
        // Data Limit Alerts
        // ----------------------------------------------------

        alerts.clear()

        alerts.addAll(
            alertStorage.loadAlerts()
        )


        // ----------------------------------------------------
        // Permissions
        // ----------------------------------------------------

        hasUsageAccess =
            dataRepository.hasUsageAccess()

        hasOverlayPermission =
            checkOverlayPermission(
                context
            )


        // ----------------------------------------------------
        // Daily Plan
        // ----------------------------------------------------

        val savedPlan =
            dailyPlanStorage.loadPlan()


        if (
            savedPlan != null
        ) {

            dailyPlanEnabled =
                savedPlan.isEnabled


            dailyPlanSaved =
                savedPlan.totalDataBytes > 0L


            dailyStartHour =
                savedPlan.startHour


            dailyStartMinute =
                savedPlan.startMinute


            dailyAlerts.clear()

            dailyAlerts.addAll(
                savedPlan.alerts
            )


            // ----------------------------------------------
            // Total amount
            // ----------------------------------------------

            if (
                savedPlan.totalDataBytes >=
                1024L *
                1024L *
                1024L
            ) {

                dailyTotalText =
                    (
                            savedPlan.totalDataBytes /
                                    (
                                            1024.0 *
                                                    1024.0 *
                                                    1024.0
                                            )
                            )
                        .toCleanNumber()


                dailyTotalUnit =
                    "GB"

            } else {

                dailyTotalText =
                    (
                            savedPlan.totalDataBytes /
                                    (
                                            1024.0 *
                                                    1024.0
                                            )
                            )
                        .toCleanNumber()


                dailyTotalUnit =
                    "MB"
            }


            // ----------------------------------------------
            // Start time
            // ----------------------------------------------

            val displayHour =

                when {

                    savedPlan.startHour == 0 ->
                        12

                    savedPlan.startHour > 12 ->
                        savedPlan.startHour - 12

                    else ->
                        savedPlan.startHour
                }


            dailyHourText =
                displayHour.toString()


            dailyMinuteText =
                savedPlan.startMinute
                    .toString()
                    .padStart(
                        2,
                        '0'
                    )


            dailyPeriod =

                if (
                    savedPlan.startHour >= 12
                ) {

                    "PM"

                } else {

                    "AM"
                }
        }


        // ----------------------------------------------------
        // Start service
        // ----------------------------------------------------

        if (
            monitoringEnabled
        ) {

            startDataMonitorService(
                context
            )
        }
    }


    // ========================================================
    // USAGE REFRESH
    // ========================================================

    LaunchedEffect(Unit) {

        while (true) {

            hasUsageAccess =
                dataRepository.hasUsageAccess()


            hasOverlayPermission =
                checkOverlayPermission(
                    context
                )


            if (
                hasUsageAccess
            ) {

                // ============================================
                // TODAY
                // ============================================

                todayUsage =
                    dataRepository.getTodayUsage()


                // ============================================
                // DAILY PLAN
                // ============================================

                val plan =
                    dailyPlanStorage.loadPlan()


                // ============================================
                // CUSTOM
                // ============================================

                customUsage =
                    getUiCustomUsage(

                        context =
                            context,

                        dataRepository =
                            dataRepository,

                        plan =
                            plan,

                        frozenPreferences =
                            frozenPreferences
                    )


                // ============================================
                // WI-FI USAGE
                // ============================================

                wifiUsage =
                    getTodayWifiUsage(
                        context
                    )


                // ============================================
                // MONTHLY
                // ============================================

                monthlyUsage =
                    dataRepository.getMonthlyUsage()


                // ============================================
                // WEEKLY
                // ============================================

                weeklyUsage =
                    dataRepository.getWeeklyUsage()
            }


            delay(
                3000L
            )
        }
    }


    // ========================================================
    // MOBILE LIVE SPEED
    // ========================================================

    LaunchedEffect(Unit) {

        var previousDownload =
            TrafficStats
                .getMobileRxBytes()


        var previousUpload =
            TrafficStats
                .getMobileTxBytes()


        var previousTime =
            System.currentTimeMillis()


        while (true) {

            delay(
                1000L
            )


            val currentDownload =
                TrafficStats
                    .getMobileRxBytes()


            val currentUpload =
                TrafficStats
                    .getMobileTxBytes()


            val currentTime =
                System.currentTimeMillis()


            val elapsed =
                currentTime -
                        previousTime


            if (
                elapsed > 0L &&
                currentDownload >= 0L &&
                currentUpload >= 0L &&
                previousDownload >= 0L &&
                previousUpload >= 0L
            ) {

                val seconds =
                    elapsed /
                            1000.0


                val downloadDifference =
                    (
                            currentDownload -
                                    previousDownload
                            )
                        .coerceAtLeast(0L)


                val uploadDifference =
                    (
                            currentUpload -
                                    previousUpload
                            )
                        .coerceAtLeast(0L)


                downloadSpeed =
                    formatSpeed(

                        (
                                downloadDifference /
                                        seconds
                                )
                            .toLong()
                    )


                uploadSpeed =
                    formatSpeed(

                        (
                                uploadDifference /
                                        seconds
                                )
                            .toLong()
                    )
            }


            previousDownload =
                currentDownload


            previousUpload =
                currentUpload


            previousTime =
                currentTime
        }
    }


    // ========================================================
    // WI-FI LIVE SPEED
    // ========================================================

    LaunchedEffect(Unit) {

        var previousRx =
            getWifiInterfaceBytes(
                context
            ).first


        var previousTx =
            getWifiInterfaceBytes(
                context
            ).second


        var previousTime =
            System.currentTimeMillis()


        while (true) {

            delay(
                1000L
            )


            val current =
                getWifiInterfaceBytes(
                    context
                )


            val currentTime =
                System.currentTimeMillis()


            val elapsed =
                currentTime -
                        previousTime


            if (
                elapsed > 0L &&
                current.first >= 0L &&
                current.second >= 0L &&
                previousRx >= 0L &&
                previousTx >= 0L
            ) {

                val seconds =
                    elapsed /
                            1000.0


                val rxDifference =
                    (
                            current.first -
                                    previousRx
                            )
                        .coerceAtLeast(0L)


                val txDifference =
                    (
                            current.second -
                                    previousTx
                            )
                        .coerceAtLeast(0L)


                wifiDownloadSpeed =
                    formatSpeed(

                        (
                                rxDifference /
                                        seconds
                                )
                            .toLong()
                    )


                wifiUploadSpeed =
                    formatSpeed(

                        (
                                txDifference /
                                        seconds
                                )
                            .toLong()
                    )
            }


            previousRx =
                current.first


            previousTx =
                current.second


            previousTime =
                currentTime
        }
    }


    // ========================================================
    // MAIN UI
    // ========================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Text(

                        text =
                            "DataDrift",

                        fontWeight =
                            FontWeight.Bold
                    )
                },


                actions = {

                    Row(

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(

                            if (
                                monitoringEnabled
                            ) {

                                "ON"

                            } else {

                                "OFF"
                            }
                        )


                        Switch(

                            checked =
                                monitoringEnabled,

                            onCheckedChange = {

                                    enabled ->

                                monitoringEnabled =
                                    enabled


                                appPreferences
                                    .edit()
                                    .putBoolean(

                                        "monitoring_enabled",

                                        enabled
                                    )
                                    .apply()


                                if (
                                    enabled
                                ) {

                                    startDataMonitorService(
                                        context
                                    )

                                } else {

                                    context.stopService(

                                        Intent(

                                            context,

                                            DataMonitorService::class.java
                                        )
                                    )
                                }
                            },

                            modifier =
                                Modifier.scale(
                                    0.75f
                                )
                        )
                    }
                }
            )
        }

    ) { innerPadding ->

        Column(

            modifier =

                Modifier
                    .fillMaxSize()
                    .padding(
                        innerPadding
                    )
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {


            // =================================================
            // USAGE ACCESS
            // =================================================

            if (
                !hasUsageAccess
            ) {

                PermissionCard(

                    title =
                        "Usage Access required",

                    description =
                        "DataDrift needs Usage Access permission to read mobile and Wi-Fi data usage.",

                    buttonText =
                        "Open Settings",

                    onClick = {

                        context.startActivity(

                            Intent(

                                Settings
                                    .ACTION_USAGE_ACCESS_SETTINGS
                            )
                        )
                    }
                )
            }


            // =================================================
            // OVERLAY PERMISSION
            // =================================================

            if (
                !hasOverlayPermission
            ) {

                PermissionCard(

                    title =
                        "Status Bar Speed Permission Required",

                    description =
                        "Allow DataDrift to show live download speed in the status bar.",

                    buttonText =
                        "Allow",

                    onClick = {

                        if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
                        ) {

                            context.startActivity(

                                Intent(

                                    Settings
                                        .ACTION_MANAGE_OVERLAY_PERMISSION,

                                    android.net.Uri.parse(

                                        "package:${context.packageName}"
                                    )
                                )
                            )
                        }
                    }
                )
            }


            // =================================================
            // TODAY + CUSTOM
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                UsageCard(

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    title =
                        "Today",

                    usage =
                        todayUsage,

                    formatBytes =
                        dataRepository::formatBytes
                )


                Spacer(
                    Modifier.width(
                        10.dp
                    )
                )


                UsageCard(

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    title =
                        "Custom",

                    usage =
                        customUsage,

                    formatBytes =
                        dataRepository::formatBytes
                )
            }


            // =================================================
            // WI-FI USAGE
            // =================================================

            WifiUsageCard(

                usage =
                    wifiUsage,

                wifiDownloadSpeed =
                    wifiDownloadSpeed,

                wifiUploadSpeed =
                    wifiUploadSpeed,

                formatBytes =
                    dataRepository::formatBytes
            )


            // =================================================
            // STATUS BAR SPEED CONTROL
            // =================================================

            StatusBarSpeedCard(

                enabled =
                    statusBarSpeedEnabled,

                onEnabledChanged = {

                        enabled ->

                    statusBarSpeedEnabled =
                        enabled


                    appPreferences
                        .edit()
                        .putBoolean(

                            DataMonitorService
                                .KEY_STATUS_BAR_SPEED_ENABLED,

                            enabled
                        )
                        .apply()


                    if (
                        monitoringEnabled
                    ) {

                        startDataMonitorService(
                            context
                        )
                    }
                },

                onSettingsClick = {

                    statusBarXText =
                        appPreferences
                            .getInt(

                                DataMonitorService
                                    .KEY_STATUS_BAR_SPEED_X,

                                DataMonitorService
                                    .DEFAULT_STATUS_BAR_SPEED_X
                            )
                            .toString()


                    statusBarYText =
                        appPreferences
                            .getInt(

                                DataMonitorService
                                    .KEY_STATUS_BAR_SPEED_Y,

                                DataMonitorService
                                    .DEFAULT_STATUS_BAR_SPEED_Y
                            )
                            .toString()


                    statusBarTextSizeText =
                        appPreferences
                            .getFloat(

                                DataMonitorService
                                    .KEY_STATUS_BAR_SPEED_TEXT_SIZE,

                                DataMonitorService
                                    .DEFAULT_STATUS_BAR_SPEED_TEXT_SIZE
                            )
                            .toString()


                    showStatusBarSettings =
                        true
                }
            )


            // =================================================
            // MOBILE REAL-TIME SPEED
            // =================================================

            RealTimeSpeedCard(

                downloadSpeed =
                    downloadSpeed,

                uploadSpeed =
                    uploadSpeed
            )


            // =================================================
            // DATA LIMIT ALERTS
            // =================================================

            Row(

                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        "Data Limit Alerts",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        22.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                IconButton(

                    onClick = {

                        customLimitText =
                            ""

                        customLimitUnit =
                            "MB"

                        showAddAlertDialog =
                            true
                    }
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Add,

                        contentDescription =
                            "Add Alert"
                    )
                }
            }


            if (
                alerts.isEmpty()
            ) {

                Card(
                    Modifier.fillMaxWidth()
                ) {

                    Text(

                        "No data limit alerts added.",

                        Modifier.padding(
                            16.dp
                        )
                    )
                }

            } else {

                for (
                alert in alerts
                ) {

                    DataAlertCard(

                        alert =
                            alert,

                        onEnabledChanged = {

                            val updated =
                                alert.copy(

                                    isEnabled =
                                        !alert.isEnabled
                                )


                            val index =
                                alerts.indexOfFirst {

                                    it.id ==
                                            alert.id
                                }


                            if (
                                index >= 0
                            ) {

                                alerts[index] =
                                    updated
                            }


                            alertStorage
                                .saveAlert(
                                    updated
                                )
                        },


                        onRepeatChanged = {

                            val updated =
                                alert.copy(

                                    isRepeating =
                                        !alert.isRepeating,

                                    lastTriggeredThreshold =
                                        0L
                                )


                            val index =
                                alerts.indexOfFirst {

                                    it.id ==
                                            alert.id
                                }


                            if (
                                index >= 0
                            ) {

                                alerts[index] =
                                    updated
                            }


                            alertStorage
                                .saveAlert(
                                    updated
                                )
                        },


                        onDelete = {

                            alerts.removeAll {

                                it.id ==
                                        alert.id
                            }


                            alertStorage.deleteAlert(
                                alert.id
                            )
                        }
                    )
                }
            }


            // =================================================
            // DAILY DATA PLAN
            // =================================================

            DailyDataPlanCard(

                enabled =
                    dailyPlanEnabled,

                saved =
                    dailyPlanSaved,

                totalText =
                    dailyTotalText,

                totalUnit =
                    dailyTotalUnit,

                startHour =
                    dailyStartHour,

                startMinute =
                    dailyStartMinute,

                alerts =
                    dailyAlerts,

                alertsExpanded =
                    dailyAlertsExpanded,

                onAlertsExpandedChanged = {

                    dailyAlertsExpanded =
                        !dailyAlertsExpanded
                },

                onToggle = {

                    dailyPlanEnabled =
                        !dailyPlanEnabled


                    if (
                        !dailyPlanEnabled
                    ) {

                        frozenPreferences
                            .edit()
                            .putLong(
                                "download_bytes",
                                customUsage.downloadBytes
                            )
                            .putLong(
                                "upload_bytes",
                                customUsage.uploadBytes
                            )
                            .putLong(
                                "total_bytes",
                                customUsage.totalBytes
                            )
                            .apply()
                    }


                    val totalBytes =
                        getDataBytes(

                            dailyTotalText,

                            dailyTotalUnit
                        )


                    dailyPlanStorage.savePlan(

                        DailyDataPlan(

                            totalDataBytes =
                                totalBytes,

                            startHour =
                                dailyStartHour,

                            startMinute =
                                dailyStartMinute,

                            isEnabled =
                                dailyPlanEnabled,

                            alerts =
                                dailyAlerts.toList(),

                            currentCycleStartTime =
                                0L
                        )
                    )


                    if (
                        monitoringEnabled
                    ) {

                        startDataMonitorService(
                            context
                        )
                    }
                },


                onEdit = {

                    dailyPlanSaved =
                        false
                },


                onTotalTextChanged = {

                        value ->

                    dailyTotalText =
                        value.filter {

                            it.isDigit() ||
                                    it == '.'
                        }
                },


                onTotalUnitChanged = {

                        value ->

                    dailyTotalUnit =
                        value
                },


                onAddAlert = {

                    dailyAlertText =
                        ""

                    dailyAlertUnit =
                        "MB"

                    dailyAlertError =
                        ""

                    showDailyAlertDialog =
                        true
                },


                onChangeTime = {

                    val displayHour =

                        when {

                            dailyStartHour == 0 ->
                                12

                            dailyStartHour > 12 ->
                                dailyStartHour - 12

                            else ->
                                dailyStartHour
                        }


                    dailyHourText =
                        displayHour.toString()


                    dailyMinuteText =
                        dailyStartMinute
                            .toString()
                            .padStart(
                                2,
                                '0'
                            )


                    dailyPeriod =

                        if (
                            dailyStartHour >= 12
                        ) {

                            "PM"

                        } else {

                            "AM"
                        }


                    showDailyStartDialog =
                        true
                },


                onSave = {

                    val totalBytes =
                        getDataBytes(

                            dailyTotalText,

                            dailyTotalUnit
                        )


                    if (
                        totalBytes > 0L
                    ) {

                        dailyPlanStorage.savePlan(

                            DailyDataPlan(

                                totalDataBytes =
                                    totalBytes,

                                startHour =
                                    dailyStartHour,

                                startMinute =
                                    dailyStartMinute,

                                isEnabled =
                                    dailyPlanEnabled,

                                alerts =
                                    dailyAlerts.toList(),

                                currentCycleStartTime =
                                    0L
                            )
                        )


                        dailyPlanSaved =
                            true


                        if (
                            monitoringEnabled
                        ) {

                            startDataMonitorService(
                                context
                            )
                        }
                    }
                },


                onAlertEnabledChanged = {

                        alertId ->

                    val index =
                        dailyAlerts.indexOfFirst {

                            it.id ==
                                    alertId
                        }


                    if (
                        index >= 0
                    ) {

                        val updated =
                            dailyAlerts[index].copy(

                                isEnabled =
                                    !dailyAlerts[index]
                                        .isEnabled
                            )


                        dailyAlerts[index] =
                            updated


                        dailyPlanStorage
                            .setAlertEnabled(

                                alertId,

                                updated.isEnabled
                            )
                    }
                },


                onAlertDelete = {

                        alertId ->

                    dailyAlerts.removeAll {

                        it.id ==
                                alertId
                    }


                    dailyPlanStorage
                        .deleteAlert(
                            alertId
                        )
                }
            )


            // =================================================
            // MONTHLY
            // =================================================

            Card(
                Modifier.fillMaxWidth()
            ) {

                Column(
                    Modifier.padding(
                        16.dp
                    )
                ) {

                    Text(

                        "Monthly Usage",

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    Text(

                        dataRepository.formatBytes(
                            monthlyUsage.totalBytes
                        ),

                        fontSize =
                            26.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    Row {

                        Text(

                            "↓ ${
                                dataRepository.formatBytes(
                                    monthlyUsage.downloadBytes
                                )
                            }"
                        )


                        Spacer(
                            Modifier.weight(
                                1f
                            )
                        )


                        Text(

                            "↑ ${
                                dataRepository.formatBytes(
                                    monthlyUsage.uploadBytes
                                )
                            }"
                        )
                    }


                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )


                    OutlinedButton(

                        onClick = {

                            weeklyExpanded =
                                !weeklyExpanded
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(

                            if (
                                weeklyExpanded
                            ) {

                                "Hide Weekly Usage"

                            } else {

                                "Show Weekly Usage"
                            }
                        )
                    }


                    if (
                        weeklyExpanded
                    ) {

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )


                        WeeklyUsageGrid(

                            weeklyUsage =
                                weeklyUsage,

                            formatBytes =
                                dataRepository::formatBytes
                        )
                    }
                }
            }
        }
    }


    // ========================================================
    // ADD DATA LIMIT ALERT DIALOG
    // ========================================================

    if (
        showAddAlertDialog
    ) {

        AlertDialog(

            onDismissRequest = {

                showAddAlertDialog =
                    false
            },


            title = {

                Text(
                    "Add Data Limit Alert"
                )
            },


            text = {

                Column {

                    OutlinedTextField(

                        value =
                            customLimitText,

                        onValueChange = {

                                value ->

                            customLimitText =
                                value.filter {

                                    it.isDigit() ||
                                            it == '.'
                                }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {

                            Text(
                                "Amount"
                            )
                        },

                        singleLine =
                            true
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    Row {

                        SmallButton(
                            "MB"
                        ) {

                            customLimitUnit =
                                "MB"
                        }


                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )


                        SmallButton(
                            "GB"
                        ) {

                            customLimitUnit =
                                "GB"
                        }
                    }
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        val bytes =
                            getDataBytes(

                                customLimitText,

                                customLimitUnit
                            )


                        if (
                            bytes > 0L
                        ) {

                            val newId =
                                (
                                        alerts
                                            .maxOfOrNull {
                                                it.id
                                            }
                                            ?: 0
                                        ) + 1


                            val newAlert =
                                DataAlert(

                                    id =
                                        newId,

                                    limitBytes =
                                        bytes,

                                    isEnabled =
                                        true,

                                    isRepeating =
                                        false
                                )


                            alerts.add(
                                newAlert
                            )


                            alerts.sortBy {
                                it.limitBytes
                            }


                            alertStorage.saveAlert(
                                newAlert
                            )


                            showAddAlertDialog =
                                false
                        }
                    }
                ) {

                    Text(
                        "Add"
                    )
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {

                        showAddAlertDialog =
                            false
                    }
                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }


    // ========================================================
    // DAILY ALERT DIALOG
    // ========================================================

    if (
        showDailyAlertDialog
    ) {

        AlertDialog(

            onDismissRequest = {

                showDailyAlertDialog =
                    false
            },


            title = {

                Text(
                    "Add Daily Alert"
                )
            },


            text = {

                Column {

                    OutlinedTextField(

                        value =
                            dailyAlertText,

                        onValueChange = {

                                value ->

                            dailyAlertText =
                                value.filter {

                                    it.isDigit() ||
                                            it == '.'
                                }


                            dailyAlertError =
                                ""
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {

                            Text(
                                "Amount"
                            )
                        },

                        singleLine =
                            true
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    Row {

                        SmallButton(
                            "MB"
                        ) {

                            dailyAlertUnit =
                                "MB"
                        }


                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )


                        SmallButton(
                            "GB"
                        ) {

                            dailyAlertUnit =
                                "GB"
                        }
                    }


                    if (
                        dailyAlertError.isNotEmpty()
                    ) {

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )


                        Text(
                            dailyAlertError
                        )
                    }
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        val limitBytes =
                            getDataBytes(

                                dailyAlertText,

                                dailyAlertUnit
                            )


                        val totalBytes =
                            getDataBytes(

                                dailyTotalText,

                                dailyTotalUnit
                            )


                        when {

                            limitBytes <= 0L -> {

                                dailyAlertError =
                                    "Enter a valid amount."
                            }


                            totalBytes <= 0L -> {

                                dailyAlertError =
                                    "Set the Daily Plan total first."
                            }


                            limitBytes >
                                    totalBytes -> {

                                dailyAlertError =
                                    "Alert cannot exceed Daily Plan total."
                            }


                            else -> {

                                val newId =

                                    (
                                            dailyAlerts
                                                .maxOfOrNull {
                                                    it.id
                                                }
                                                ?: 0
                                            ) + 1


                                val newAlert =
                                    DailyDataAlert(

                                        id =
                                            newId,

                                        limitBytes =
                                            limitBytes,

                                        isEnabled =
                                            true,

                                        isTriggered =
                                            false
                                    )


                                dailyAlerts.add(
                                    newAlert
                                )


                                dailyAlerts.sortBy {
                                    it.limitBytes
                                }


                                showDailyAlertDialog =
                                    false
                            }
                        }
                    }
                ) {

                    Text(
                        "Add"
                    )
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {

                        showDailyAlertDialog =
                            false
                    }
                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }


    // ========================================================
    // DAILY START TIME DIALOG
    // ========================================================

    if (
        showDailyStartDialog
    ) {

        AlertDialog(

            onDismissRequest = {

                showDailyStartDialog =
                    false
            },


            title = {

                Text(
                    "Daily Start Time"
                )
            },


            text = {

                Column {

                    Row {

                        OutlinedTextField(

                            value =
                                dailyHourText,

                            onValueChange = {

                                dailyHourText =
                                    it.filter {
                                            c ->
                                        c.isDigit()
                                    }
                            },

                            modifier =
                                Modifier.weight(
                                    1f
                                ),

                            label = {

                                Text(
                                    "Hour"
                                )
                            },

                            singleLine =
                                true
                        )


                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )


                        OutlinedTextField(

                            value =
                                dailyMinuteText,

                            onValueChange = {

                                dailyMinuteText =
                                    it.filter {
                                            c ->
                                        c.isDigit()
                                    }
                            },

                            modifier =
                                Modifier.weight(
                                    1f
                                ),

                            label = {

                                Text(
                                    "Minute"
                                )
                            },

                            singleLine =
                                true
                        )
                    }


                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )


                    Row {

                        SmallButton(
                            "AM"
                        ) {

                            dailyPeriod =
                                "AM"
                        }


                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )


                        SmallButton(
                            "PM"
                        ) {

                            dailyPeriod =
                                "PM"
                        }
                    }
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        val hour =
                            dailyHourText
                                .toIntOrNull()


                        val minute =
                            dailyMinuteText
                                .toIntOrNull()


                        if (
                            hour != null &&
                            minute != null &&
                            hour in 1..12 &&
                            minute in 0..59
                        ) {

                            dailyStartHour =
                                convertTo24Hour(

                                    hour,

                                    dailyPeriod
                                )


                            dailyStartMinute =
                                minute


                            dailyPlanSaved =
                                false


                            showDailyStartDialog =
                                false
                        }
                    }
                ) {

                    Text(
                        "Save"
                    )
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {

                        showDailyStartDialog =
                            false
                    }
                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }


    // ========================================================
    // STATUS BAR SETTINGS DIALOG
    // ========================================================

    if (
        showStatusBarSettings
    ) {

        AlertDialog(

            onDismissRequest = {

                showStatusBarSettings =
                    false
            },


            title = {

                Text(
                    "Status Bar Speed Settings"
                )
            },


            text = {

                Column {

                    OutlinedTextField(

                        value =
                            statusBarXText,

                        onValueChange = {

                            statusBarXText =
                                it.filter {
                                        c ->
                                    c.isDigit()
                                }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {

                            Text(
                                "X Position"
                            )
                        },

                        singleLine =
                            true
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    OutlinedTextField(

                        value =
                            statusBarYText,

                        onValueChange = {

                            statusBarYText =
                                it.filter {
                                        c ->
                                    c.isDigit()
                                }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {

                            Text(
                                "Y Position"
                            )
                        },

                        singleLine =
                            true
                    )


                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )


                    OutlinedTextField(

                        value =
                            statusBarTextSizeText,

                        onValueChange = {

                            statusBarTextSizeText =
                                it.filter {

                                        c ->

                                    c.isDigit() ||
                                            c == '.'
                                }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {

                            Text(
                                "Text Size"
                            )
                        },

                        singleLine =
                            true
                    )
                }
            },


            confirmButton = {

                TextButton(

                    onClick = {

                        val x =
                            statusBarXText
                                .toIntOrNull()


                        val y =
                            statusBarYText
                                .toIntOrNull()


                        val size =
                            statusBarTextSizeText
                                .toFloatOrNull()


                        if (
                            x != null &&
                            y != null &&
                            size != null &&
                            size > 0f
                        ) {

                            appPreferences
                                .edit()
                                .putInt(

                                    DataMonitorService
                                        .KEY_STATUS_BAR_SPEED_X,

                                    x
                                )
                                .putInt(

                                    DataMonitorService
                                        .KEY_STATUS_BAR_SPEED_Y,

                                    y
                                )
                                .putFloat(

                                    DataMonitorService
                                        .KEY_STATUS_BAR_SPEED_TEXT_SIZE,

                                    size
                                )
                                .apply()


                            showStatusBarSettings =
                                false


                            if (
                                monitoringEnabled
                            ) {

                                startDataMonitorService(
                                    context
                                )
                            }
                        }
                    }
                ) {

                    Text(
                        "Save"
                    )
                }
            },


            dismissButton = {

                TextButton(

                    onClick = {

                        showStatusBarSettings =
                            false
                    }
                ) {

                    Text(
                        "Cancel"
                    )
                }
            }
        )
    }
}


// ============================================================
// USAGE CARD
// ============================================================

@Composable
fun UsageCard(

    modifier:
    Modifier,

    title:
    String,

    usage:
    DataUsageManager.UsageResult,

    formatBytes:
        (Long) -> String

) {

    Card(
        modifier
    ) {

        Column(
            Modifier.padding(
                14.dp
            )
        ) {

            Text(

                title,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    6.dp
                )
            )


            Text(

                formatBytes(
                    usage.totalBytes
                ),

                fontSize =
                    24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    6.dp
                )
            )


            Text(

                "↓ ${
                    formatBytes(
                        usage.downloadBytes
                    )
                }"
            )


            Text(

                "↑ ${
                    formatBytes(
                        usage.uploadBytes
                    )
                }"
            )
        }
    }
}


// ============================================================
// WI-FI USAGE CARD
// ============================================================

@Composable
fun WifiUsageCard(

    usage:
    DataUsageManager.UsageResult,

    wifiDownloadSpeed:
    String,

    wifiUploadSpeed:
    String,

    formatBytes:
        (Long) -> String

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(
                14.dp
            )
        ) {

            // ------------------------------------------------
            // TITLE
            // ------------------------------------------------

            Text(

                "Wi-Fi Usage",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    6.dp
                )
            )


            // ------------------------------------------------
            // TOTAL
            // ------------------------------------------------

            Text(

                formatBytes(
                    usage.totalBytes
                ),

                fontSize =
                    24.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    6.dp
                )
            )


            // ------------------------------------------------
            // TOTAL DOWNLOAD / UPLOAD
            // ------------------------------------------------

            Row {

                Text(

                    "↓ ${
                        formatBytes(
                            usage.downloadBytes
                        )
                    }"
                )


                Spacer(
                    Modifier.weight(
                        1f
                    )
                )


                Text(

                    "↑ ${
                        formatBytes(
                            usage.uploadBytes
                        )
                    }"
                )
            }


            Spacer(
                Modifier.height(
                    10.dp
                )
            )


            // ------------------------------------------------
            // LIVE WI-FI SPEED
            // ------------------------------------------------

            Text(

                "Real-time Wi-Fi speed",

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    4.dp
                )
            )


            Row {

                Text(
                    "↓ $wifiDownloadSpeed"
                )


                Spacer(
                    Modifier.weight(
                        1f
                    )
                )


                Text(
                    "↑ $wifiUploadSpeed"
                )
            }
        }
    }
}


// ============================================================
// STATUS BAR SPEED CARD
// ============================================================

@Composable
fun StatusBarSpeedCard(

    enabled:
    Boolean,

    onEnabledChanged:
        (Boolean) -> Unit,

    onSettingsClick:
        () -> Unit

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(
                14.dp
            )
        ) {

            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    "Status Bar Speed",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Text(

                    if (
                        enabled
                    ) {

                        "ON"

                    } else {

                        "OFF"
                    }
                )


                Switch(

                    checked =
                        enabled,

                    onCheckedChange =
                        onEnabledChanged,

                    modifier =
                        Modifier.scale(
                            0.7f
                        )
                )
            }


            Spacer(
                Modifier.height(
                    6.dp
                )
            )


            OutlinedButton(

                onClick =
                    onSettingsClick,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Icon(

                    imageVector =
                        Icons.Default.Settings,

                    contentDescription =
                        "Settings"
                )


                Spacer(
                    Modifier.width(
                        6.dp
                    )
                )


                Text(
                    "Position & Text Size"
                )
            }
        }
    }
}


// ============================================================
// REAL-TIME MOBILE SPEED CARD
// ============================================================

@Composable
fun RealTimeSpeedCard(

    downloadSpeed:
    String,

    uploadSpeed:
    String

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        14.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(

                "Real-time speed",

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.weight(
                    1f
                )
            )


            Text(
                "↓ $downloadSpeed"
            )


            Spacer(
                Modifier.width(
                    14.dp
                )
            )


            Text(
                "↑ $uploadSpeed"
            )
        }
    }
}


// ============================================================
// DATA ALERT CARD
// ============================================================

@Composable
fun DataAlertCard(

    alert:
    DataAlert,

    onEnabledChanged:
        () -> Unit,

    onRepeatChanged:
        () -> Unit,

    onDelete:
        () -> Unit

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(
                14.dp
            )
        ) {

            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    formatDisplayBytes(
                        alert.limitBytes
                    ),

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Text(

                    if (
                        alert.isEnabled
                    ) {

                        "ON"

                    } else {

                        "OFF"
                    }
                )


                Switch(

                    checked =
                        alert.isEnabled,

                    onCheckedChange = {

                        onEnabledChanged()
                    },

                    modifier =
                        Modifier.scale(
                            0.7f
                        )
                )
            }


            Row {

                OutlinedButton(

                    onClick =
                        onRepeatChanged
                ) {

                    Text(

                        if (
                            alert.isRepeating
                        ) {

                            "Repeat ON"

                        } else {

                            "Repeat OFF"
                        }
                    )
                }


                Spacer(
                    Modifier.width(
                        8.dp
                    )
                )


                IconButton(

                    onClick =
                        onDelete
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Delete,

                        contentDescription =
                            "Delete"
                    )
                }
            }
        }
    }
}


// ============================================================
// DAILY DATA PLAN CARD
// ============================================================

@Composable
fun DailyDataPlanCard(

    enabled:
    Boolean,

    saved:
    Boolean,

    totalText:
    String,

    totalUnit:
    String,

    startHour:
    Int,

    startMinute:
    Int,

    alerts:
    List<DailyDataAlert>,

    alertsExpanded:
    Boolean,

    onAlertsExpandedChanged:
        () -> Unit,

    onToggle:
        () -> Unit,

    onEdit:
        () -> Unit,

    onTotalTextChanged:
        (String) -> Unit,

    onTotalUnitChanged:
        (String) -> Unit,

    onAddAlert:
        () -> Unit,

    onChangeTime:
        () -> Unit,

    onSave:
        () -> Unit,

    onAlertEnabledChanged:
        (Int) -> Unit,

    onAlertDelete:
        (Int) -> Unit

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(
                16.dp
            )
        ) {

            // ------------------------------------------------
            // HEADER
            // ------------------------------------------------

            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    "Daily Data Plan",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        22.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Text(

                    if (
                        enabled
                    ) {

                        "ON"

                    } else {

                        "OFF"
                    }
                )


                Switch(

                    checked =
                        enabled,

                    onCheckedChange = {

                        onToggle()
                    },

                    modifier =
                        Modifier.scale(
                            0.75f
                        )
                )
            }


            Spacer(
                Modifier.height(
                    14.dp
                )
            )


            // ------------------------------------------------
            // TOTAL DATA
            // ------------------------------------------------

            Text(

                "Total Daily Data",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    8.dp
                )
            )


            if (
                saved
            ) {

                Row(

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(

                        "$totalText $totalUnit",

                        modifier =
                            Modifier.weight(
                                1f
                            ),

                        fontSize =
                            24.sp,

                        fontWeight =
                            FontWeight.Bold
                    )


                    OutlinedButton(

                        onClick =
                            onEdit
                    ) {

                        Text(
                            "Edit"
                        )
                    }
                }

            } else {

                Row {

                    OutlinedTextField(

                        value =
                            totalText,

                        onValueChange =
                            onTotalTextChanged,

                        modifier =
                            Modifier.weight(
                                1f
                            ),

                        label = {

                            Text(
                                "Total amount"
                            )
                        },

                        singleLine =
                            true
                    )


                    Spacer(
                        Modifier.width(
                            8.dp
                        )
                    )


                    Column {

                        SmallButton(
                            "MB"
                        ) {

                            onTotalUnitChanged(
                                "MB"
                            )
                        }


                        Spacer(
                            Modifier.height(
                                4.dp
                            )
                        )


                        SmallButton(
                            "GB"
                        ) {

                            onTotalUnitChanged(
                                "GB"
                            )
                        }
                    }
                }
            }


            Spacer(
                Modifier.height(
                    14.dp
                )
            )


            // ------------------------------------------------
            // START TIME
            // ------------------------------------------------

            Text(

                "Daily Start Time",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    4.dp
                )
            )


            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    formatTime12Hour(

                        startHour,

                        startMinute
                    ),

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        18.sp
                )


                OutlinedButton(

                    onClick =
                        onChangeTime
                ) {

                    Text(
                        "Change"
                    )
                }
            }


            Spacer(
                Modifier.height(
                    14.dp
                )
            )


            // ------------------------------------------------
            // DAILY ALERTS
            // ------------------------------------------------

            Row(

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    "Daily Alerts",

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                IconButton(

                    onClick =
                        onAddAlert
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Add,

                        contentDescription =
                            "Add Daily Alert"
                    )
                }
            }


            // ------------------------------------------------
            // SHOW / HIDE DAILY ALERTS
            // ------------------------------------------------

            OutlinedButton(

                onClick =
                    onAlertsExpandedChanged,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(

                    if (
                        alertsExpanded
                    ) {

                        "Hide Daily Alerts"

                    } else {

                        "Show Daily Alerts"
                    }
                )
            }


            if (
                alertsExpanded
            ) {

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )


                if (
                    alerts.isEmpty()
                ) {

                    Text(
                        "No daily alerts added."
                    )

                } else {

                    for (
                    alert in alerts
                    ) {

                        Row(

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        vertical = 5.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(

                                formatDisplayBytes(
                                    alert.limitBytes
                                ),

                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            )


                            Text(

                                if (
                                    alert.isEnabled
                                ) {

                                    "ON"

                                } else {

                                    "OFF"

                                }
                            )


                            Switch(

                                checked =
                                    alert.isEnabled,

                                onCheckedChange = {

                                    onAlertEnabledChanged(
                                        alert.id
                                    )
                                },

                                modifier =
                                    Modifier.scale(
                                        0.65f
                                    )
                            )


                            IconButton(

                                onClick = {

                                    onAlertDelete(
                                        alert.id
                                    )
                                }
                            ) {

                                Icon(

                                    imageVector =
                                        Icons.Default.Delete,

                                    contentDescription =
                                        "Delete"
                                )
                            }
                        }
                    }
                }
            }


            // ------------------------------------------------
            // SAVE
            // ------------------------------------------------

            if (
                !saved
            ) {

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )


                Button(

                    onClick =
                        onSave,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "Save Daily Plan"
                    )
                }
            }
        }
    }
}


// ============================================================
// WEEKLY USAGE
// ============================================================

@Composable
fun WeeklyUsageGrid(

    weeklyUsage:
    List<DataRepository.WeeklyDayUsage>,

    formatBytes:
        (Long) -> String

) {

    Column {

        var index =
            0


        while (
            index <
            weeklyUsage.size
        ) {

            Row(
                Modifier.fillMaxWidth()
            ) {

                repeat(3) {

                    if (
                        index <
                        weeklyUsage.size
                    ) {

                        val day =
                            weeklyUsage[index]


                        val date =
                            SimpleDateFormat(

                                "EEE d",

                                Locale.US
                            )
                                .format(

                                    Date(
                                        day.startTime
                                    )
                                )


                        Column(

                            modifier =
                                Modifier
                                    .weight(
                                        1f
                                    )
                                    .padding(
                                        4.dp
                                    )
                        ) {

                            Text(

                                date,

                                fontWeight =
                                    FontWeight.Bold
                            )


                            Text(

                                formatBytes(
                                    day.totalBytes
                                )
                            )
                        }


                        index++

                    } else {

                        Spacer(
                            Modifier.weight(
                                1f
                            )
                        )
                    }
                }
            }
        }
    }
}


// ============================================================
// PERMISSION CARD
// ============================================================

@Composable
fun PermissionCard(

    title:
    String,

    description:
    String,

    buttonText:
    String,

    onClick:
        () -> Unit

) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(
                14.dp
            )
        ) {

            Text(

                title,

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )


            Spacer(
                Modifier.height(
                    5.dp
                )
            )


            Text(
                description
            )


            Spacer(
                Modifier.height(
                    8.dp
                )
            )


            Button(
                onClick =
                    onClick
            ) {

                Text(
                    buttonText
                )
            }
        }
    }
}


// ============================================================
// SMALL BUTTON
// ============================================================

@Composable
fun SmallButton(

    text:
    String,

    onClick:
        () -> Unit

) {

    OutlinedButton(

        onClick =
            onClick
    ) {

        Text(
            text
        )
    }
}


// ============================================================
// EMPTY USAGE
// ============================================================

fun emptyUsage():
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


// ============================================================
// UI CUSTOM USAGE
// ============================================================
//
// IMPORTANT:
//
// Daily cycle resets at MIDNIGHT.
//
// Daily Start Time decides when Custom starts counting.
//
// Example:
//
// Start = 12 PM
//
// 8 AM  -> Custom = 0
// 11 AM -> Custom = 0
// 1 PM  -> Custom = 12 PM -> 1 PM
//
// ============================================================

fun getUiCustomUsage(

    context:
    Context,

    dataRepository:
    DataRepository,

    plan:
    DailyDataPlan?,

    frozenPreferences:
    android.content.SharedPreferences

):
        DataUsageManager.UsageResult {

    if (
        plan == null ||
        !plan.isEnabled ||
        plan.totalDataBytes <= 0L
    ) {

        return DataUsageManager.UsageResult(

            frozenPreferences.getLong(
                "download_bytes",
                0L
            ),

            frozenPreferences.getLong(
                "upload_bytes",
                0L
            ),

            frozenPreferences.getLong(
                "total_bytes",
                0L
            )
        )
    }


    val now =
        System.currentTimeMillis()


    val start =
        getTodayConfiguredStart(

            plan.startHour,

            plan.startMinute,

            now
        )


    // --------------------------------------------------------
    // Before Daily Start Time
    // --------------------------------------------------------

    if (
        now < start
    ) {

        return emptyUsage()
    }


    val raw =
        dataRepository.getCustomUsage(
            start
        )


    // --------------------------------------------------------
    // Cap Custom to Daily Plan
    // --------------------------------------------------------

    if (
        raw.totalBytes <=
        plan.totalDataBytes
    ) {

        return raw
    }


    val download =
        raw.downloadBytes
            .coerceAtMost(
                plan.totalDataBytes
            )


    val remaining =
        (
                plan.totalDataBytes -
                        download
                )
            .coerceAtLeast(
                0L
            )


    val upload =
        raw.uploadBytes
            .coerceAtMost(
                remaining
            )


    return DataUsageManager.UsageResult(

        downloadBytes =
            download,

        uploadBytes =
            upload,

        totalBytes =
            download +
                    upload
    )
}


// ============================================================
// TODAY CONFIGURED START
// ============================================================

fun getTodayConfiguredStart(

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


// ============================================================
// TODAY WI-FI USAGE
// ============================================================

fun getTodayWifiUsage(
    context:
    Context
):
        DataUsageManager.UsageResult {

    val startTime =
        getTodayMidnight()


    val endTime =
        System.currentTimeMillis()


    return try {

        val manager =

            context.getSystemService(
                Context.NETWORK_STATS_SERVICE
            ) as NetworkStatsManager


        val stats =
            manager.querySummary(

                ConnectivityManager.TYPE_WIFI,

                null,

                startTime,

                endTime
            )


        val bucket =
            NetworkStats.Bucket()


        var download =
            0L


        var upload =
            0L


        while (
            stats.hasNextBucket()
        ) {

            stats.getNextBucket(
                bucket
            )


            download +=
                bucket.rxBytes


            upload +=
                bucket.txBytes
        }


        stats.close()


        DataUsageManager.UsageResult(

            downloadBytes =
                download,

            uploadBytes =
                upload,

            totalBytes =
                download +
                        upload
        )

    } catch (
        _: Exception
    ) {

        emptyUsage()
    }
}


// ============================================================
// WI-FI INTERFACE LIVE BYTES
// ============================================================
//
// Finds the actual Wi-Fi network interface instead of
// hardcoding wlan0.
//
// ============================================================

fun getWifiInterfaceBytes(
    context:
    Context
):
        Pair<Long, Long> {

    return try {

        val connectivityManager =

            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager


        for (
        network in
        connectivityManager.allNetworks
        ) {

            val capabilities =
                connectivityManager
                    .getNetworkCapabilities(
                        network
                    )
                    ?: continue


            if (
                capabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
            ) {

                val linkProperties =
                    connectivityManager
                        .getLinkProperties(
                            network
                        )


                val interfaceName =
                    linkProperties
                        ?.interfaceName


                if (
                    !interfaceName.isNullOrBlank()
                ) {

                    val rx =
                        TrafficStats
                            .getRxBytes(
                                interfaceName
                            )


                    val tx =
                        TrafficStats
                            .getTxBytes(
                                interfaceName
                            )


                    if (
                        rx >= 0L &&
                        tx >= 0L
                    ) {

                        return Pair(
                            rx,
                            tx
                        )
                    }
                }
            }
        }


        Pair(
            0L,
            0L
        )

    } catch (
        _: Exception
    ) {

        Pair(
            0L,
            0L
        )
    }
}


// ============================================================
// TODAY MIDNIGHT
// ============================================================

fun getTodayMidnight(): Long {

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


// ============================================================
// DATA -> BYTES
// ============================================================

fun getDataBytes(

    text:
    String,

    unit:
    String

): Long {

    val amount =
        text.toDoubleOrNull()
            ?: return 0L


    if (
        amount <= 0.0
    ) {

        return 0L
    }


    return convertToBytes(
        amount,
        unit
    )
}


// ============================================================
// CONVERT TO BYTES
// ============================================================

fun convertToBytes(

    amount:
    Double,

    unit:
    String

): Long {

    val multiplier =

        if (
            unit == "GB"
        ) {

            1024.0 *
                    1024.0 *
                    1024.0

        } else {

            1024.0 *
                    1024.0
        }


    return (
            amount *
                    multiplier
            ).toLong()
}


// ============================================================
// CONVERT 12H -> 24H
// ============================================================

fun convertTo24Hour(

    hour:
    Int,

    period:
    String

): Int {

    return when {

        period == "AM" &&
                hour == 12 -> {

            0
        }


        period == "PM" &&
                hour != 12 -> {

            hour + 12
        }


        else -> {

            hour
        }
    }
}


// ============================================================
// FORMAT TIME
// ============================================================

fun formatTime12Hour(

    hour:
    Int,

    minute:
    Int

): String {

    val displayHour =

        when {

            hour == 0 ->
                12

            hour > 12 ->
                hour - 12

            else ->
                hour
        }


    val period =

        if (
            hour >= 12
        ) {

            "PM"

        } else {

            "AM"
        }


    return String.format(

        Locale.US,

        "%d:%02d %s",

        displayHour,

        minute,

        period
    )
}


// ============================================================
// FORMAT SPEED
// ============================================================

fun formatSpeed(

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
        kb *
                1024.0


    val gb =
        mb *
                1024.0


    return when {

        bytesPerSecond >= gb -> {

            String.format(

                Locale.US,

                "%.2f GB/s",

                bytesPerSecond /
                        gb
            )
        }


        bytesPerSecond >= mb -> {

            String.format(

                Locale.US,

                "%.2f MB/s",

                bytesPerSecond /
                        mb
            )
        }


        else -> {

            String.format(

                Locale.US,

                "%.0f KB/s",

                bytesPerSecond /
                        kb
            )
        }
    }
}


// ============================================================
// FORMAT DISPLAY BYTES
// ============================================================

fun formatDisplayBytes(

    bytes:
    Long

): String {

    val mb =
        1024.0 *
                1024.0


    val gb =
        mb *
                1024.0


    return if (
        bytes >= gb
    ) {

        String.format(

            Locale.US,

            "%.2f GB",

            bytes /
                    gb
        )

    } else {

        String.format(

            Locale.US,

            "%.0f MB",

            bytes /
                    mb
        )
    }
}


// ============================================================
// CHECK OVERLAY PERMISSION
// ============================================================

fun checkOverlayPermission(
    context:
    Context
): Boolean {

    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.M
    ) {

        Settings.canDrawOverlays(
            context
        )

    } else {

        true
    }
}


// ============================================================
// START MONITORING SERVICE
// ============================================================

fun startDataMonitorService(
    context:
    Context
) {

    val intent =
        Intent(

            context,

            DataMonitorService::class.java
        )


    if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.O
    ) {

        ContextCompat
            .startForegroundService(

                context,

                intent
            )

    } else {

        context.startService(
            intent
        )
    }
}


// ============================================================
// DOUBLE -> CLEAN NUMBER
// ============================================================

fun Double.toCleanNumber():
        String {

    return if (
        this % 1.0 == 0.0
    ) {

        this
            .toLong()
            .toString()

    } else {

        String.format(
            Locale.US,
            "%.2f",
            this
        )
    }
}
