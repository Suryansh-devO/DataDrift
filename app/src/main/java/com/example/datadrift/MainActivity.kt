package com.example.datadrift

// ============================================================
// IMPORTS
// ============================================================

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.Uri
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

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val dataUsageManager =
            DataUsageManager(this)

        val dataRepository =
            DataRepository(dataUsageManager)

        val alertStorage =
            DataAlertStorage(this)

        val dailyPlanStorage =
            DailyDataPlanStorage(this)

        setContent {

            DataDriftTheme {

                DataDriftScreen(
                    dataRepository = dataRepository,
                    alertStorage = alertStorage,
                    dailyPlanStorage = dailyPlanStorage
                )
            }
        }
    }
}

// ============================================================
// MAIN SCREEN
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataDriftScreen(
    dataRepository: DataRepository,
    alertStorage: DataAlertStorage,
    dailyPlanStorage: DailyDataPlanStorage
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

    /*
     * IMPORTANT:
     *
     * This preference is also used by DataMonitorService.
     *
     * It contains the service's latest calculated Custom value.
     */
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
        mutableStateOf(emptyUsage())
    }

    var customUsage by remember {
        mutableStateOf(emptyUsage())
    }

    var monthlyUsage by remember {
        mutableStateOf(emptyUsage())
    }

    // ========================================================
    // WEEKLY
    // ========================================================

    var weeklyUsage by remember {
        mutableStateOf(
            emptyList<DataRepository.WeeklyDayUsage>()
        )
    }

    var weeklyExpanded by remember {
        mutableStateOf(false)
    }

    // ========================================================
    // SPEED
    // ========================================================

    var downloadSpeed by remember {
        mutableStateOf("0 KB/s")
    }

    var uploadSpeed by remember {
        mutableStateOf("0 KB/s")
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
            checkOverlayPermission(context)
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
    // DAILY DATA PLAN
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
    // START TIME DIALOG
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
        // DATA LIMIT ALERTS
        // ----------------------------------------------------

        alerts.clear()
        alerts.addAll(
            alertStorage.loadAlerts()
        )

        // ----------------------------------------------------
        // PERMISSIONS
        // ----------------------------------------------------

        hasUsageAccess =
            dataRepository.hasUsageAccess()

        hasOverlayPermission =
            checkOverlayPermission(context)

        // ----------------------------------------------------
        // DAILY PLAN
        // ----------------------------------------------------

        val savedPlan =
            dailyPlanStorage.loadPlan()

        if (savedPlan != null) {

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

            // ------------------------------------------------
            // TOTAL DISPLAY
            // ------------------------------------------------

            val total =
                bytesToDisplayValue(
                    savedPlan.totalDataBytes
                )

            dailyTotalText =
                total.first

            dailyTotalUnit =
                total.second

            // ------------------------------------------------
            // START TIME DISPLAY
            // ------------------------------------------------

            dailyHourText =
                displayHour(
                    savedPlan.startHour
                ).toString()

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

            // ------------------------------------------------
            // CUSTOM
            // ------------------------------------------------

            customUsage =
                if (
                    savedPlan.isEnabled
                ) {

                    getUiCustomUsage(
                        plan = savedPlan,
                        frozenPreferences = frozenPreferences
                    )

                } else {

                    getFrozenCustomUsageForUi(
                        frozenPreferences
                    )
                }

        } else {

            customUsage =
                getFrozenCustomUsageForUi(
                    frozenPreferences
                )
        }

        // ====================================================
        // START SERVICE
        // ====================================================

        if (monitoringEnabled) {

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

            // ------------------------------------------------
            // PERMISSIONS
            // ------------------------------------------------

            hasUsageAccess =
                dataRepository.hasUsageAccess()

            hasOverlayPermission =
                checkOverlayPermission(context)

            // ------------------------------------------------
            // USAGE
            // ------------------------------------------------

            if (hasUsageAccess) {

                // ============================================
                // TODAY
                // ============================================

                todayUsage =
                    dataRepository.getTodayUsage()

                // ============================================
                // DAILY PLAN
                // ============================================

                val savedPlan =
                    dailyPlanStorage.loadPlan()

                // ============================================
                // CUSTOM
                // ============================================

                customUsage =
                    if (
                        savedPlan != null &&
                        savedPlan.isEnabled &&
                        savedPlan.totalDataBytes > 0L
                    ) {

                        /*
                         * IMPORTANT:
                         *
                         * Custom is now read ONLY from the
                         * DataMonitorService saved value.
                         *
                         * MainActivity no longer performs a
                         * separate NetworkStats calculation.
                         */

                        getUiCustomUsage(
                            plan = savedPlan,
                            frozenPreferences = frozenPreferences
                        )

                    } else {

                        getFrozenCustomUsageForUi(
                            frozenPreferences
                        )
                    }

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

            kotlinx.coroutines.delay(3000L)
        }
    }

    // ========================================================
    // REAL-TIME SPEED
    // ========================================================

    LaunchedEffect(Unit) {

        var previousDownload =
            TrafficStats.getMobileRxBytes()

        var previousUpload =
            TrafficStats.getMobileTxBytes()

        var previousTime =
            System.currentTimeMillis()

        while (true) {

            kotlinx.coroutines.delay(1000L)

            val currentDownload =
                TrafficStats.getMobileRxBytes()

            val currentUpload =
                TrafficStats.getMobileTxBytes()

            val currentTime =
                System.currentTimeMillis()

            val elapsed =
                currentTime - previousTime

            if (
                elapsed > 0L &&
                currentDownload >= 0L &&
                currentUpload >= 0L &&
                previousDownload >= 0L &&
                previousUpload >= 0L
            ) {

                val seconds =
                    elapsed / 1000.0

                val downloadDifference =
                    (
                            currentDownload -
                                    previousDownload
                            ).coerceAtLeast(0L)

                val uploadDifference =
                    (
                            currentUpload -
                                    previousUpload
                            ).coerceAtLeast(0L)

                downloadSpeed =
                    formatSpeed(
                        (
                                downloadDifference /
                                        seconds
                                ).toLong()
                    )

                uploadSpeed =
                    formatSpeed(
                        (
                                uploadDifference /
                                        seconds
                                ).toLong()
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
    // MAIN UI
    // ========================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {

                    Text(
                        text = "DataDrift",
                        fontWeight = FontWeight.Bold
                    )
                },

                actions = {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            if (monitoringEnabled) {
                                "ON"
                            } else {
                                "OFF"
                            }
                        )

                        Switch(

                            checked =
                                monitoringEnabled,

                            onCheckedChange = { enabled ->

                                monitoringEnabled =
                                    enabled

                                appPreferences
                                    .edit()
                                    .putBoolean(
                                        "monitoring_enabled",
                                        enabled
                                    )
                                    .apply()

                                if (enabled) {

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
                                Modifier.scale(0.75f)
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
                    .padding(innerPadding)
                    .verticalScroll(
                        rememberScrollState()
                    )
                    .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            // =================================================
            // USAGE ACCESS
            // =================================================

            if (!hasUsageAccess) {

                PermissionCard(

                    title =
                        "Usage Access required",

                    description =
                        "DataDrift needs Usage Access permission to read mobile data usage.",

                    buttonText =
                        "Open Settings",

                    onClick = {

                        context.startActivity(
                            Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS
                            )
                        )
                    }
                )
            }

            // =================================================
            // OVERLAY PERMISSION
            // =================================================

            if (!hasOverlayPermission) {

                PermissionCard(

                    title =
                        "Status Bar Speed Permission Required",

                    description =
                        "DataDrift needs permission to show live download speed in the status bar.",

                    buttonText =
                        "Allow",

                    onClick = {

                        if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
                        ) {

                            context.startActivity(

                                Intent(

                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,

                                    Uri.parse(
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
                        Modifier.weight(1f),

                    title =
                        "Today",

                    usage =
                        todayUsage,

                    formatBytes =
                        dataRepository::formatBytes
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                UsageCard(

                    modifier =
                        Modifier.weight(1f),

                    title =
                        "Custom",

                    usage =
                        customUsage,

                    formatBytes =
                        dataRepository::formatBytes
                )
            }

            // =================================================
            // REAL-TIME SPEED
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
                        Modifier.weight(1f),

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

            if (alerts.isEmpty()) {

                Card(
                    Modifier.fillMaxWidth()
                ) {

                    Text(

                        text =
                            "No data limit alerts added.",

                        modifier =
                            Modifier.padding(16.dp)
                    )
                }

            } else {

                for (alert in alerts) {

                    DataAlertCard(

                        alert =
                            alert,

                        onEnabledChanged = {

                            val index =
                                alerts.indexOfFirst {
                                    it.id == alert.id
                                }

                            if (index >= 0) {

                                val updated =
                                    alert.copy(
                                        isEnabled =
                                            !alert.isEnabled
                                    )

                                alerts[index] =
                                    updated

                                alertStorage
                                    .setAlertEnabled(
                                        alert.id,
                                        updated.isEnabled
                                    )
                            }
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
                                    it.id == alert.id
                                }

                            if (index >= 0) {

                                alerts[index] =
                                    updated
                            }

                            alertStorage.saveAlert(
                                updated
                            )
                        },

                        onDelete = {

                            alerts.removeAll {
                                it.id == alert.id
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

                onToggle = {

                    // -----------------------------------------
                    // TURNING OFF
                    // -----------------------------------------

                    if (dailyPlanEnabled) {

                        /*
                         * IMPORTANT FIX:
                         *
                         * Do NOT use customUsage here.
                         *
                         * customUsage is a UI snapshot that can
                         * be up to 3 seconds old.
                         *
                         * Instead, use the exact value saved by
                         * DataMonitorService.
                         */

                        val serviceCustom =
                            getFrozenCustomUsageForUi(
                                frozenPreferences
                            )

                        val serviceCycle =
                            frozenPreferences.getLong(
                                "cycle_start",
                                0L
                            )

                        frozenPreferences
                            .edit()
                            .putLong(
                                "download_bytes",
                                serviceCustom.downloadBytes
                            )
                            .putLong(
                                "upload_bytes",
                                serviceCustom.uploadBytes
                            )
                            .putLong(
                                "total_bytes",
                                serviceCustom.totalBytes
                            )
                            .putLong(
                                "cycle_start",
                                serviceCycle
                            )
                            .apply()

                        dailyPlanEnabled =
                            false

                    } else {

                        // -------------------------------------
                        // TURNING ON
                        // -------------------------------------

                        dailyPlanEnabled =
                            true
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

                    dailyPlanSaved =
                        totalBytes > 0L

                    startDataMonitorService(
                        context
                    )
                },

                onEdit = {

                    dailyPlanSaved =
                        false
                },

                onTotalTextChanged = { value ->

                    dailyTotalText =
                        value
                },

                onTotalUnitChanged = { value ->

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

                    dailyHourText =
                        displayHour(
                            dailyStartHour
                        ).toString()

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

                    if (totalBytes > 0L) {

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

                        startDataMonitorService(
                            context
                        )
                    }
                },

                onAlertEnabledChanged = { alertId ->

                    val index =
                        dailyAlerts.indexOfFirst {
                            it.id == alertId
                        }

                    if (index >= 0) {

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

                onAlertDelete = { alertId ->

                    dailyAlerts.removeAll {
                        it.id == alertId
                    }

                    dailyPlanStorage.deleteAlert(
                        alertId
                    )

                    dailyPlanSaved =
                        false
                }
            )

            // =================================================
            // MONTHLY + WEEKLY
            // =================================================

            Card(
                Modifier.fillMaxWidth()
            ) {

                Column(
                    Modifier.padding(16.dp)
                ) {

                    Text(

                        text =
                            "Monthly Usage",

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(

                        text =
                            dataRepository.formatBytes(
                                monthlyUsage.totalBytes
                            ),

                        fontSize =
                            26.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
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
                            Modifier.weight(1f)
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
                        Modifier.height(10.dp)
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

                            if (weeklyExpanded) {
                                "Hide Weekly Usage"
                            } else {
                                "Show Weekly Usage"
                            }
                        )
                    }

                    if (weeklyExpanded) {

                        Spacer(
                            Modifier.height(10.dp)
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
    // DATA LIMIT ALERT DIALOG
    // ========================================================

    if (showAddAlertDialog) {

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

                        onValueChange = { value ->

                            customLimitText =
                                value.filter {
                                    it.isDigit() ||
                                            it == '.'
                                }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text("Amount")
                        },

                        singleLine =
                            true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row {

                        SmallButton("MB") {

                            customLimitUnit =
                                "MB"
                        }

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        SmallButton("GB") {

                            customLimitUnit =
                                "GB"
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(

                    onClick = {

                        val amount =
                            customLimitText
                                .toDoubleOrNull()

                        if (
                            amount != null &&
                            amount > 0.0
                        ) {

                            val bytes =
                                convertToBytes(
                                    amount,
                                    customLimitUnit
                                )

                            val newId =
                                (
                                        alerts.maxOfOrNull {
                                            it.id
                                        } ?: 0
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

                    Text("Add")
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showAddAlertDialog =
                            false
                    }
                ) {

                    Text("Cancel")
                }
            }
        )
    }

    // ========================================================
    // DAILY ALERT DIALOG
    // ========================================================

    if (showDailyAlertDialog) {

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

                        onValueChange = { value ->

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
                            Text("Amount")
                        },

                        singleLine =
                            true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row {

                        SmallButton("MB") {

                            dailyAlertUnit =
                                "MB"
                        }

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        SmallButton("GB") {

                            dailyAlertUnit =
                                "GB"
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Daily alert cannot exceed the total Daily Data Plan."
                    )

                    if (
                        dailyAlertError.isNotBlank()
                    ) {

                        Spacer(
                            Modifier.height(6.dp)
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

                        val alertBytes =
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

                            totalBytes <= 0L -> {

                                dailyAlertError =
                                    "Enter the Daily Data Plan total first."
                            }

                            alertBytes <= 0L -> {

                                dailyAlertError =
                                    "Enter a valid alert amount."
                            }

                            alertBytes > totalBytes -> {

                                dailyAlertError =
                                    "Alert cannot exceed Daily Data Plan."
                            }

                            else -> {

                                val newId =
                                    (
                                            dailyAlerts.maxOfOrNull {
                                                it.id
                                            } ?: 0
                                            ) + 1

                                dailyAlerts.add(

                                    DailyDataAlert(

                                        id =
                                            newId,

                                        limitBytes =
                                            alertBytes,

                                        isEnabled =
                                            true,

                                        isTriggered =
                                            false
                                    )
                                )

                                dailyAlerts.sortBy {
                                    it.limitBytes
                                }

                                dailyPlanSaved =
                                    false

                                showDailyAlertDialog =
                                    false
                            }
                        }
                    }
                ) {

                    Text("Add")
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showDailyAlertDialog =
                            false
                    }
                ) {

                    Text("Cancel")
                }
            }
        )
    }

    // ========================================================
    // DAILY START TIME DIALOG
    // ========================================================

    if (showDailyStartDialog) {

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

                            onValueChange = { value ->

                                dailyHourText =
                                    value.filter {
                                        it.isDigit()
                                    }
                            },

                            modifier =
                                Modifier.weight(1f),

                            label = {
                                Text("Hour")
                            },

                            singleLine =
                                true
                        )

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        OutlinedTextField(

                            value =
                                dailyMinuteText,

                            onValueChange = { value ->

                                dailyMinuteText =
                                    value.filter {
                                        it.isDigit()
                                    }
                            },

                            modifier =
                                Modifier.weight(1f),

                            label = {
                                Text("Minute")
                            },

                            singleLine =
                                true
                        )
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Row {

                        SmallButton("AM") {

                            dailyPeriod =
                                "AM"
                        }

                        Spacer(
                            Modifier.width(8.dp)
                        )

                        SmallButton("PM") {

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
                            dailyHourText.toIntOrNull()

                        val minute =
                            dailyMinuteText.toIntOrNull()

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

                    Text("Save")
                }
            },

            dismissButton = {

                TextButton(

                    onClick = {

                        showDailyStartDialog =
                            false
                    }
                ) {

                    Text("Cancel")
                }
            }
        )
    }
}

// ============================================================
// PERMISSION CARD
// ============================================================

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(20.dp)
        ) {

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                description
            )

            Spacer(
                Modifier.height(12.dp)
            )

            Button(
                onClick = onClick
            ) {

                Text(
                    buttonText
                )
            }
        }
    }
}

// ============================================================
// USAGE CARD
// ============================================================

@Composable
fun UsageCard(
    modifier: Modifier,
    title: String,
    usage: DataUsageManager.UsageResult,
    formatBytes: (Long) -> String
) {

    Card(
        modifier
    ) {

        Column(
            Modifier.padding(16.dp)
        ) {

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                text =
                    formatBytes(
                        usage.totalBytes
                    ),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(10.dp)
            )

            Row {

                Text(
                    "↓ ${
                        formatBytes(
                            usage.downloadBytes
                        )
                    }"
                )

                Spacer(
                    Modifier.weight(1f)
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
}

// ============================================================
// REAL-TIME SPEED CARD
// ============================================================

@Composable
fun RealTimeSpeedCard(
    downloadSpeed: String,
    uploadSpeed: String
) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "Real-time speed",
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.weight(1f)
            )

            Text(
                "↓ $downloadSpeed"
            )

            Spacer(
                Modifier.width(14.dp)
            )

            Text(
                "↑ $uploadSpeed"
            )
        }
    }
}

// ============================================================
// DATA LIMIT ALERT CARD
// ============================================================

@Composable
fun DataAlertCard(
    alert: DataAlert,
    onEnabledChanged: () -> Unit,
    onRepeatChanged: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        formatDisplayBytes(
                            alert.limitBytes
                        ),

                    modifier =
                        Modifier.weight(1f),

                    fontSize =
                        18.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    if (alert.isEnabled) {
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
                        Modifier.scale(0.7f)
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
                    Modifier.width(8.dp)
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
    enabled: Boolean,
    saved: Boolean,
    totalText: String,
    totalUnit: String,
    startHour: Int,
    startMinute: Int,
    alerts: List<DailyDataAlert>,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTotalTextChanged: (String) -> Unit,
    onTotalUnitChanged: (String) -> Unit,
    onAddAlert: () -> Unit,
    onChangeTime: () -> Unit,
    onSave: () -> Unit,
    onAlertEnabledChanged: (Int) -> Unit,
    onAlertDelete: (Int) -> Unit
) {

    Card(
        Modifier.fillMaxWidth()
    ) {

        Column(
            Modifier.padding(16.dp)
        ) {

            // ------------------------------------------------
            // HEADER
            // ------------------------------------------------

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        "Daily Data Plan",

                    modifier =
                        Modifier.weight(1f),

                    fontSize =
                        22.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    if (enabled) {
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
                        Modifier.scale(0.75f)
                )
            }

            Spacer(
                Modifier.height(14.dp)
            )

            // ------------------------------------------------
            // TOTAL DAILY DATA
            // ------------------------------------------------

            Text(

                text =
                    "Total Daily Data",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(8.dp)
            )

            if (saved) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(

                        text =
                            "$totalText $totalUnit",

                        modifier =
                            Modifier.weight(1f),

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
                            Modifier.weight(1f),

                        label = {
                            Text("Total amount")
                        },

                        singleLine =
                            true
                    )

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Column {

                        SmallButton("MB") {

                            onTotalUnitChanged(
                                "MB"
                            )
                        }

                        Spacer(
                            Modifier.height(4.dp)
                        )

                        SmallButton("GB") {

                            onTotalUnitChanged(
                                "GB"
                            )
                        }
                    }
                }
            }

            Spacer(
                Modifier.height(14.dp)
            )

            // ------------------------------------------------
            // DAILY START TIME
            // ------------------------------------------------

            Text(

                text =
                    "Daily Start Time",

                fontSize =
                    18.sp,

                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        formatTime12Hour(
                            startHour,
                            startMinute
                        ),

                    modifier =
                        Modifier.weight(1f),

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
                Modifier.height(14.dp)
            )

            // ------------------------------------------------
            // DAILY ALERTS HEADER
            // ------------------------------------------------

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(

                    text =
                        "Daily Alerts",

                    modifier =
                        Modifier.weight(1f),

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
            // DAILY ALERTS
            // ------------------------------------------------

            if (alerts.isEmpty()) {

                Text(
                    "No daily alerts added."
                )

            } else {

                for (alert in alerts) {

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

                            text =
                                formatDisplayBytes(
                                    alert.limitBytes
                                ),

                            modifier =
                                Modifier.weight(1f)
                        )

                        Text(
                            if (alert.isEnabled) {
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
                                Modifier.scale(0.65f)
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

            // ------------------------------------------------
            // SAVE DAILY PLAN
            // ------------------------------------------------

            Spacer(
                Modifier.height(12.dp)
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
                            ).format(
                                Date(
                                    day.startTime
                                )
                            )

                        Column(

                            modifier =
                                Modifier
                                    .weight(1f)
                                    .padding(4.dp)
                        ) {

                            Text(

                                text =
                                    date,

                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(

                                text =
                                    formatBytes(
                                        day.totalBytes
                                    )
                            )
                        }

                        index++

                    } else {

                        Spacer(
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// SMALL BUTTON
// ============================================================

@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit
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
// UI CUSTOM USAGE
// ============================================================
//
// IMPORTANT FIX:
//
// MainActivity no longer calculates Custom using:
//
//     dataRepository.getCustomUsage()
//
// Instead it reads the value saved by DataMonitorService.
//
// Therefore:
//
// DataMonitorService Custom
//          ↓
// datadrift_custom_frozen
//          ↓
// MainActivity Custom card
//
// This keeps the Custom card and Daily Data Plan alert
// on the same Custom calculation.
//
// ============================================================

fun getUiCustomUsage(
    plan: DailyDataPlan,
    frozenPreferences:
    android.content.SharedPreferences
): DataUsageManager.UsageResult {

    /*
     * Calculate the CURRENT cycle according to the user's
     * selected Daily Data Plan start time.
     *
     * Example:
     *
     * Start = 12:00 PM
     *
     * At 11:00 AM:
     * cycle = yesterday 12:00 PM
     *
     * At 12:00 PM:
     * cycle = today 12:00 PM
     */

    val cycleStart =
        calculateDailyCycleStart(
            plan.startHour,
            plan.startMinute
        )

    val savedCycle =
        frozenPreferences.getLong(
            "cycle_start",
            0L
        )

    /*
     * Only use the service value if it belongs to
     * the current Daily Plan cycle.
     */

    return if (
        savedCycle ==
        cycleStart
    ) {

        getFrozenCustomUsageForUi(
            frozenPreferences
        )

    } else {

        /*
         * Service has not initialized the new cycle yet.
         *
         * IMPORTANT:
         * Do not calculate NetworkStats here.
         *
         * Otherwise MainActivity and DataMonitorService
         * can again show different values.
         */

        emptyUsage()
    }
}

// ============================================================
// FROZEN CUSTOM FOR UI
// ============================================================

fun getFrozenCustomUsageForUi(
    frozenPreferences:
    android.content.SharedPreferences
): DataUsageManager.UsageResult {

    return DataUsageManager.UsageResult(

        downloadBytes =
            frozenPreferences.getLong(
                "download_bytes",
                0L
            ),

        uploadBytes =
            frozenPreferences.getLong(
                "upload_bytes",
                0L
            ),

        totalBytes =
            frozenPreferences.getLong(
                "total_bytes",
                0L
            )
    )
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
// DAILY CYCLE START
// ============================================================
//
// Example:
//
// Start = 12:00 PM
//
// At 10:00 AM:
// yesterday 12:00 PM
//
// At 1:00 PM:
// today 12:00 PM
//
// ============================================================

fun calculateDailyCycleStart(
    startHour: Int,
    startMinute: Int
): Long {

    val now =
        Calendar.getInstance()

    val cycle =
        Calendar.getInstance()

    cycle.set(
        Calendar.HOUR_OF_DAY,
        startHour
    )

    cycle.set(
        Calendar.MINUTE,
        startMinute
    )

    cycle.set(
        Calendar.SECOND,
        0
    )

    cycle.set(
        Calendar.MILLISECOND,
        0
    )

    if (
        now.timeInMillis <
        cycle.timeInMillis
    ) {

        cycle.add(
            Calendar.DAY_OF_MONTH,
            -1
        )
    }

    return cycle.timeInMillis
}

// ============================================================
// START MONITORING SERVICE
// ============================================================

fun startDataMonitorService(
    context: Context
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
// DATA -> BYTES
// ============================================================

fun getDataBytes(
    text: String,
    unit: String
): Long {

    val amount =
        text
            .toDoubleOrNull()
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
// CONVERT DATA
// ============================================================

fun convertToBytes(
    amount: Double,
    unit: String
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
// 12 HOUR -> 24 HOUR
// ============================================================

fun convertTo24Hour(
    hour: Int,
    period: String
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
// DISPLAY HOUR
// ============================================================

fun displayHour(
    hour: Int
): Int {

    return when {

        hour == 0 -> {
            12
        }

        hour > 12 -> {
            hour - 12
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
    hour: Int,
    minute: Int
): String {

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

        displayHour(
            hour
        ),

        minute,

        period
    )
}

// ============================================================
// FORMAT SPEED
// ============================================================

fun formatSpeed(
    bytesPerSecond: Long
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

        bytesPerSecond >= gb -> {

            String.format(
                Locale.US,
                "%.2f GB/s",
                bytesPerSecond / gb
            )
        }

        bytesPerSecond >= mb -> {

            String.format(
                Locale.US,
                "%.2f MB/s",
                bytesPerSecond / mb
            )
        }

        else -> {

            String.format(
                Locale.US,
                "%.0f KB/s",
                bytesPerSecond / kb
            )
        }
    }
}

// ============================================================
// FORMAT DISPLAY BYTES
// ============================================================

fun formatDisplayBytes(
    bytes: Long
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
            bytes / gb
        )

    } else {

        String.format(
            Locale.US,
            "%.0f MB",
            bytes / mb
        )
    }
}

// ============================================================
// BYTES -> DISPLAY VALUE
// ============================================================

fun bytesToDisplayValue(
    bytes: Long
): Pair<String, String> {

    val gb =
        1024.0 *
                1024.0 *
                1024.0

    val mb =
        1024.0 *
                1024.0

    return if (
        bytes >= gb
    ) {

        val value =
            bytes / gb

        Pair(

            if (
                value % 1.0 == 0.0
            ) {

                value
                    .toLong()
                    .toString()

            } else {

                String.format(
                    Locale.US,
                    "%.2f",
                    value
                )
            },

            "GB"
        )

    } else {

        val value =
            bytes / mb

        Pair(

            if (
                value % 1.0 == 0.0
            ) {

                value
                    .toLong()
                    .toString()

            } else {

                String.format(
                    Locale.US,
                    "%.2f",
                    value
                )
            },

            "MB"
        )
    }
}

// ============================================================
// OVERLAY PERMISSION CHECK
// ============================================================

fun checkOverlayPermission(
    context: Context
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