package com.example.datadrift.model

// ============================================================
// DataDrift - DataCycle
// ============================================================
// RESPONSIBILITY:
// DataDrift mein data calculation ke different cycles ko
// represent karna.
//
// CURRENT CYCLES:
//
// 1. TODAY
//    12:00 AM → Current Time
//
// 2. CUSTOM
//    User ke selected start time → Current Time
//
// IMPORTANT:
// Ye sirf DATA MODEL hai.
// Actual calculation DataRepository karega.
// ============================================================


// ============================================================
// DATA CYCLE TYPE
// ============================================================

enum class DataCycleType {

    // ========================================================
    // NORMAL DAILY CYCLE
    // ========================================================
    // Har din 12:00 AM se start hota hai.
    // ========================================================

    TODAY,


    // ========================================================
    // CUSTOM CYCLE
    // ========================================================
    // User apna starting time choose karega.
    // ========================================================

    CUSTOM
}


// ============================================================
// DATA CYCLE MODEL
// ============================================================

data class DataCycle(

    // ========================================================
    // CYCLE TYPE
    // ========================================================
    //
    // TODAY ya CUSTOM.
    // ========================================================

    val type: DataCycleType,


    // ========================================================
    // START TIME
    // ========================================================
    //
    // Usage calculation kis timestamp se start hogi.
    //
    // TODAY:
    // 12:00 AM
    //
    // CUSTOM:
    // User-selected time.
    // ========================================================

    val startTime: Long,


    // ========================================================
    // DISPLAY NAME
    // ========================================================
    //
    // UI mein dikhane ke liye naam.
    //
    // Example:
    //
    // "Today"
    // "Custom"
    // ========================================================

    val displayName: String
)