package com.example.datadrift.model

// ============================================================
// DataDrift - DailyDataPlan
// ============================================================
//
// DAILY DATA PLAN
//
// Example:
//
// Total Data : 1.5 GB
// Start Time : 10:00 AM
// End Time   : 11:00 AM
//
// Alerts:
//
// 100 MB
// 500 MB
// 800 MB
// 1 GB
// 1.2 GB
// 1.5 GB
//
// IMPORTANT:
//
// Daily Data Plan ke alerts repeat nahi honge.
//
// Har alert ek daily active period mein sirf ek baar
// trigger hoga.
//
// Start Time:
//     Is time se Custom usage counting start hogi.
//
// End Time:
//     Is time par Custom usage counting stop hogi.
//     End Time ke baad Daily Alerts bhi trigger nahi honge.
//
// Midnight:
//     12:00 AM par next calendar day ka cycle reset hoga.
//
// ============================================================


// ============================================================
// DAILY ALERT
// ============================================================

data class DailyDataAlert(

    // --------------------------------------------------------
    // Unique alert ID
    // --------------------------------------------------------

    val id: Int,

    // --------------------------------------------------------
    // Alert limit bytes mein
    //
    // Example:
    //
    // 100 MB
    // 500 MB
    // 1 GB
    //
    // --------------------------------------------------------

    val limitBytes: Long,

    // --------------------------------------------------------
    // Alert ON / OFF
    // --------------------------------------------------------

    val isEnabled: Boolean = true,

    // --------------------------------------------------------
    // Current daily cycle mein alert trigger hua ya nahi
    // --------------------------------------------------------

    val isTriggered: Boolean = false
)


// ============================================================
// DAILY DATA PLAN
// ============================================================

data class DailyDataPlan(

    // --------------------------------------------------------
    // Total daily data
    //
    // Example:
    //
    // 1.5 GB
    //
    // --------------------------------------------------------

    val totalDataBytes: Long,

    // --------------------------------------------------------
    // Daily active period ka START time
    //
    // Example:
    //
    // 10:00 AM
    //
    // --------------------------------------------------------

    val startHour: Int,

    val startMinute: Int,

    // --------------------------------------------------------
    // Daily active period ka END time
    //
    // Example:
    //
    // 11:00 AM
    //
    // Is time ke baad:
    //
    // Custom counting stop
    // Daily Alerts stop
    //
    // --------------------------------------------------------

    val endHour: Int = 23,

    val endMinute: Int = 59,

    // --------------------------------------------------------
    // Daily plan ON / OFF
    // --------------------------------------------------------

    val isEnabled: Boolean = true,

    // --------------------------------------------------------
    // Is daily cycle ke alerts
    // --------------------------------------------------------

    val alerts: List<DailyDataAlert> = emptyList(),

    // --------------------------------------------------------
    // Current calendar-day cycle ka start timestamp
    //
    // IMPORTANT:
    //
    // Daily cycle midnight 12:00 AM par reset hota hai.
    //
    // Ye selected Start Time nahi hai.
    //
    // --------------------------------------------------------

    val currentCycleStartTime: Long = 0L
)