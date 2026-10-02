package com.example.datadrift.data

import java.util.Calendar

// ============================================================
// DataDrift - DataRepository
// ============================================================
//
// Handles:
//
// 1. Today usage
// 2. Custom usage
// 3. Monthly usage
// 4. Weekly daily usage
//
// Weekly usage:
//
// Monday
// Tuesday
// Wednesday
// Thursday
// Friday
// Saturday
// Sunday
//
// Each day contains TOTAL usage only.
// Download and upload are not shown in the weekly section.
//
// ============================================================

class DataRepository(
    private val dataUsageManager: DataUsageManager
) {

    // ========================================================
    // WEEKLY DAY USAGE MODEL
    // ========================================================

    data class WeeklyDayUsage(
        val dayName: String,
        val startTime: Long,
        val endTime: Long,
        val totalBytes: Long
    )

    // ========================================================
    // TODAY START TIME
    // ========================================================

    fun getTodayStartTime(): Long {

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
    // MONTH START TIME
    // ========================================================

    fun getMonthStartTime(): Long {

        val calendar =
            Calendar.getInstance()

        calendar.set(
            Calendar.DAY_OF_MONTH,
            1
        )

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
    // CURRENT TIME
    // ========================================================

    fun getCurrentTime(): Long {

        return System.currentTimeMillis()
    }

    // ========================================================
    // TODAY USAGE
    // ========================================================

    fun getTodayUsage():
            DataUsageManager.UsageResult {

        val startTime =
            getTodayStartTime()

        val endTime =
            getCurrentTime()

        return dataUsageManager
            .getMobileDataUsage(
                startTime,
                endTime
            )
    }

    // ========================================================
    // CUSTOM USAGE
    // ========================================================

    fun getCustomUsage(
        startTime: Long
    ):
            DataUsageManager.UsageResult {

        val endTime =
            getCurrentTime()

        return dataUsageManager
            .getMobileDataUsage(
                startTime,
                endTime
            )
    }

    // ========================================================
    // MONTHLY USAGE
    // ========================================================

    fun getMonthlyUsage():
            DataUsageManager.UsageResult {

        val startTime =
            getMonthStartTime()

        val endTime =
            getCurrentTime()

        return dataUsageManager
            .getMobileDataUsage(
                startTime,
                endTime
            )
    }

    // ========================================================
    // WEEK START TIME
    // ========================================================
    //
    // Week starts on Monday.
    //
    // Example:
    //
    // Monday 00:00
    // Tuesday 00:00
    // ...
    // Sunday 00:00
    //
    // ========================================================

    fun getWeekStartTime(): Long {

        val calendar =
            Calendar.getInstance()

        // Monday = first day of our week.
        calendar.firstDayOfWeek =
            Calendar.MONDAY

        val currentDay =
            calendar.get(
                Calendar.DAY_OF_WEEK
            )

        val daysFromMonday =

            when (
                currentDay
            ) {

                Calendar.MONDAY ->
                    0

                Calendar.TUESDAY ->
                    1

                Calendar.WEDNESDAY ->
                    2

                Calendar.THURSDAY ->
                    3

                Calendar.FRIDAY ->
                    4

                Calendar.SATURDAY ->
                    5

                Calendar.SUNDAY ->
                    6

                else ->
                    0
            }

        calendar.add(
            Calendar.DAY_OF_MONTH,
            -daysFromMonday
        )

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
    // WEEKLY DAILY USAGE
    // ========================================================
    //
    // Returns:
    //
    // Monday
    // Tuesday
    // Wednesday
    // Thursday
    // Friday
    // Saturday
    // Sunday
    //
    // Only total data is returned.
    //
    // ========================================================

    fun getWeeklyUsage():
            List<WeeklyDayUsage> {

        val weekStart =
            getWeekStartTime()

        val currentTime =
            getCurrentTime()

        val result =
            mutableListOf<WeeklyDayUsage>()

        val dayNames =
            listOf(
                "Mon",
                "Tue",
                "Wed",
                "Thu",
                "Fri",
                "Sat",
                "Sun"
            )

        // ====================================================
        // CALCULATE EACH DAY
        // ====================================================

        for (
        dayIndex in 0..6
        ) {

            val dayStartCalendar =
                Calendar.getInstance()

            dayStartCalendar.timeInMillis =
                weekStart

            dayStartCalendar.add(
                Calendar.DAY_OF_MONTH,
                dayIndex
            )

            val dayStart =
                dayStartCalendar.timeInMillis

            // -----------------------------------------------
            // Next day start
            // -----------------------------------------------

            val dayEndCalendar =
                Calendar.getInstance()

            dayEndCalendar.timeInMillis =
                dayStart

            dayEndCalendar.add(
                Calendar.DAY_OF_MONTH,
                1
            )

            val nextDayStart =
                dayEndCalendar.timeInMillis

            // -----------------------------------------------
            // Do not read future time.
            //
            // If the day is today:
            // today -> current time
            //
            // If the day is future:
            // no usage -> 0
            // -----------------------------------------------

            val actualEndTime =

                when {

                    dayStart > currentTime -> {

                        dayStart
                    }

                    nextDayStart > currentTime -> {

                        currentTime
                    }

                    else -> {

                        nextDayStart
                    }
                }

            val totalBytes =

                if (
                    dayStart >= currentTime
                ) {

                    0L

                } else {

                    dataUsageManager
                        .getMobileDataUsage(
                            dayStart,
                            actualEndTime
                        )
                        .totalBytes
                }

            result.add(

                WeeklyDayUsage(

                    dayName =
                        dayNames[dayIndex],

                    startTime =
                        dayStart,

                    endTime =
                        actualEndTime,

                    totalBytes =
                        totalBytes
                )
            )
        }

        return result
    }

    // ========================================================
    // FORMAT BYTES
    // ========================================================

    fun formatBytes(
        bytes: Long
    ): String {

        return dataUsageManager
            .formatBytes(
                bytes
            )
    }

    // ========================================================
    // USAGE ACCESS
    // ========================================================

    fun hasUsageAccess(): Boolean {

        return dataUsageManager
            .hasUsageAccess()
    }
}