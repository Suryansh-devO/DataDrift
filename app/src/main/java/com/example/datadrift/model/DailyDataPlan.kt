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
// Start Time : 11:00 AM
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
// Har alert ek daily cycle mein sirf ek baar trigger hoga.
//
// Next day start time par cycle reset hogi.
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
    // Daily cycle ka start time
    //
    // Example:
    //
    // 11:00 AM
    //
    // Is value mein actual clock time ke
    // hour/minute ko represent kiya jayega.
    //
    // --------------------------------------------------------

    val startHour: Int,

    val startMinute: Int,

    // --------------------------------------------------------
    // Daily plan ON / OFF
    // --------------------------------------------------------

    val isEnabled: Boolean = true,

    // --------------------------------------------------------
    // Is daily cycle ke alerts
    // --------------------------------------------------------

    val alerts: List<DailyDataAlert> = emptyList(),

    // --------------------------------------------------------
    // Current cycle ka start timestamp
    //
    // Isse pata chalega ki current alerts
    // kis daily cycle ke hain.
    //
    // --------------------------------------------------------

    val currentCycleStartTime: Long = 0L
)