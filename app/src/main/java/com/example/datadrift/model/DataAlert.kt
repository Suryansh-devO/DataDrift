package com.example.datadrift.model

// ============================================================
// DataDrift - DataAlert
// ============================================================
//
// DATA LIMIT ALERT
//
// Yeh Data Limit Alerts card ke alerts hain.
//
// Example:
//
// 10 MB   -> ON  -> Repeat ON
// 100 MB  -> ON  -> Repeat OFF
// 500 MB  -> OFF -> Repeat ON
//
// Repeat OFF:
// Alert limit reach hone par sirf ek baar notification.
//
// Repeat ON:
// Alert har next interval par dobara trigger hoga.
//
// Example:
//
// Limit = 10 MB
//
// 10 MB  -> Alert
// 20 MB  -> Alert
// 30 MB  -> Alert
// 40 MB  -> Alert
//
// IMPORTANT:
// Daily Data Plan ke alerts is model ka part nahi hain.
// Daily Data Plan ke liye alag model bana hua hai:
// DailyDataPlan.kt
//
// ============================================================

enum class AlertCycleType {
    CUSTOM
}

// ============================================================
// DATA ALERT MODEL
// ============================================================

data class DataAlert(

    // --------------------------------------------------------
    // Unique ID
    // --------------------------------------------------------

    val id: Int,

    // --------------------------------------------------------
    // Original alert limit
    //
    // Example:
    //
    // 10 MB
    // 100 MB
    // 500 MB
    //
    // --------------------------------------------------------

    val limitBytes: Long,

    // --------------------------------------------------------
    // Alert ON / OFF
    // --------------------------------------------------------

    val isEnabled: Boolean = true,

    // --------------------------------------------------------
    // Repeat ON / OFF
    //
    // false = alert only once
    //
    // true = alert every next interval
    //
    // Example:
    //
    // 10 MB -> 20 MB -> 30 MB -> 40 MB
    //
    // --------------------------------------------------------

    val isRepeating: Boolean = false,

    // --------------------------------------------------------
    // Last threshold at which this alert was triggered
    //
    // Example:
    //
    // limit = 10 MB
    //
    // first trigger:
    // lastTriggeredThreshold = 10 MB
    //
    // next:
    // lastTriggeredThreshold = 20 MB
    //
    // next:
    // lastTriggeredThreshold = 30 MB
    //
    // --------------------------------------------------------

    val lastTriggeredThreshold: Long = 0L,

    // --------------------------------------------------------
    // Current custom cycle start time
    //
    // Iska use custom usage cycle ke andar alert ko track
    // karne ke liye hoga.
    //
    // --------------------------------------------------------

    val lastTriggeredCycleStartTime: Long = 0L
)

// ============================================================
// COMMON DATA ALERT LIMITS
// ============================================================

object DataAlertLimits {

    // --------------------------------------------------------
    // MB LIMITS
    // --------------------------------------------------------

    const val MB_20: Long =
        20L * 1024L * 1024L

    const val MB_50: Long =
        50L * 1024L * 1024L

    const val MB_100: Long =
        100L * 1024L * 1024L

    const val MB_500: Long =
        500L * 1024L * 1024L

    // --------------------------------------------------------
    // GB LIMITS
    // --------------------------------------------------------

    const val GB_1: Long =
        1L * 1024L * 1024L * 1024L

    const val GB_2: Long =
        2L * 1024L * 1024L * 1024L

    const val GB_5: Long =
        5L * 1024L * 1024L * 1024L
}