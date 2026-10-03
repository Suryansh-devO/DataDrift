package com.example.datadrift.data

// ============================================================
// DataDrift - DailyDataPlanStorage
// ============================================================
//
// RESPONSIBILITY:
//
// 1. Daily Data Plan save karna
// 2. Daily Data Plan load karna
// 3. Daily Data Plan ON/OFF save karna
// 4. Daily total data save karna
// 5. Daily start time save karna
// 6. Daily end time save karna
// 7. Daily alerts save karna
// 8. Daily alerts ON/OFF save karna
// 9. Daily alert triggered state save karna
// 10. Daily alert delete karna
//
// STORAGE:
// SharedPreferences
//
// IMPORTANT:
//
// Ye Data Limit Alerts se alag storage system hai.
//
// ============================================================

import android.content.Context
import com.example.datadrift.model.DailyDataAlert
import com.example.datadrift.model.DailyDataPlan

class DailyDataPlanStorage(
    context: Context
) {

    // ========================================================
    // SHARED PREFERENCES
    // ========================================================

    private val preferences =
        context.getSharedPreferences(
            "datadrift_daily_plan",
            Context.MODE_PRIVATE
        )


    // ========================================================
    // KEYS
    // ========================================================

    private companion object {

        // ----------------------------------------------------
        // Plan enabled
        // ----------------------------------------------------

        const val KEY_PLAN_ENABLED =
            "plan_enabled"


        // ----------------------------------------------------
        // Total data
        // ----------------------------------------------------

        const val KEY_TOTAL_DATA =
            "total_data_bytes"


        // ----------------------------------------------------
        // Start hour
        // ----------------------------------------------------

        const val KEY_START_HOUR =
            "start_hour"


        // ----------------------------------------------------
        // Start minute
        // ----------------------------------------------------

        const val KEY_START_MINUTE =
            "start_minute"


        // ----------------------------------------------------
        // End hour
        // ----------------------------------------------------

        const val KEY_END_HOUR =
            "end_hour"


        // ----------------------------------------------------
        // End minute
        // ----------------------------------------------------

        const val KEY_END_MINUTE =
            "end_minute"


        // ----------------------------------------------------
        // Current cycle start
        // ----------------------------------------------------

        const val KEY_CYCLE_START =
            "current_cycle_start"


        // ----------------------------------------------------
        // Alert IDs
        // ----------------------------------------------------

        const val KEY_ALERT_IDS =
            "alert_ids"


        // ----------------------------------------------------
        // Alert limit prefix
        // ----------------------------------------------------

        const val KEY_ALERT_LIMIT_PREFIX =
            "alert_limit_"


        // ----------------------------------------------------
        // Alert enabled prefix
        // ----------------------------------------------------

        const val KEY_ALERT_ENABLED_PREFIX =
            "alert_enabled_"


        // ----------------------------------------------------
        // Alert triggered prefix
        // ----------------------------------------------------

        const val KEY_ALERT_TRIGGERED_PREFIX =
            "alert_triggered_"
    }


    // ========================================================
    // SAVE COMPLETE PLAN
    // ========================================================

    fun savePlan(
        plan: DailyDataPlan
    ) {

        // ----------------------------------------------------
        // Save basic plan information
        // ----------------------------------------------------

        preferences
            .edit()

            .putBoolean(
                KEY_PLAN_ENABLED,
                plan.isEnabled
            )

            .putLong(
                KEY_TOTAL_DATA,
                plan.totalDataBytes
            )

            .putInt(
                KEY_START_HOUR,
                plan.startHour
            )

            .putInt(
                KEY_START_MINUTE,
                plan.startMinute
            )

            .putInt(
                KEY_END_HOUR,
                plan.endHour
            )

            .putInt(
                KEY_END_MINUTE,
                plan.endMinute
            )

            .putLong(
                KEY_CYCLE_START,
                plan.currentCycleStartTime
            )

            .apply()


        // ----------------------------------------------------
        // Remove old alert list
        // ----------------------------------------------------

        clearSavedAlerts()


        // ----------------------------------------------------
        // Save new alerts
        // ----------------------------------------------------

        saveAlerts(
            plan.alerts
        )
    }


    // ========================================================
    // LOAD COMPLETE PLAN
    // ========================================================

    fun loadPlan():
            DailyDataPlan? {

        // ----------------------------------------------------
        // Check whether a plan exists
        // ----------------------------------------------------

        if (
            !preferences.contains(
                KEY_TOTAL_DATA
            )
        ) {

            return null
        }


        // ----------------------------------------------------
        // Read basic information
        // ----------------------------------------------------

        val enabled =
            preferences.getBoolean(
                KEY_PLAN_ENABLED,
                true
            )


        val totalDataBytes =
            preferences.getLong(
                KEY_TOTAL_DATA,
                0L
            )


        val startHour =
            preferences.getInt(
                KEY_START_HOUR,
                0
            )


        val startMinute =
            preferences.getInt(
                KEY_START_MINUTE,
                0
            )


        // ----------------------------------------------------
        // Read end time
        //
        // IMPORTANT:
        //
        // Old saved plans mein End Time nahi hoga.
        //
        // Isliye default:
        //
        // 11:59 PM
        //
        // diya gaya hai.
        //
        // ----------------------------------------------------

        val endHour =
            preferences.getInt(
                KEY_END_HOUR,
                23
            )


        val endMinute =
            preferences.getInt(
                KEY_END_MINUTE,
                59
            )


        val cycleStart =
            preferences.getLong(
                KEY_CYCLE_START,
                0L
            )


        // ----------------------------------------------------
        // Read alerts
        // ----------------------------------------------------

        val alerts =
            loadAlerts()


        // ----------------------------------------------------
        // Create plan
        // ----------------------------------------------------

        return DailyDataPlan(

            totalDataBytes =
                totalDataBytes,

            startHour =
                startHour,

            startMinute =
                startMinute,

            endHour =
                endHour,

            endMinute =
                endMinute,

            isEnabled =
                enabled,

            alerts =
                alerts,

            currentCycleStartTime =
                cycleStart
        )
    }


    // ========================================================
    // SAVE ALERT LIST
    // ========================================================

    private fun saveAlerts(
        alerts: List<DailyDataAlert>
    ) {

        // ----------------------------------------------------
        // Create ID list
        // ----------------------------------------------------

        val ids =
            alerts
                .map {
                    it.id
                }
                .joinToString(",")


        preferences
            .edit()
            .putString(
                KEY_ALERT_IDS,
                ids
            )
            .apply()


        // ====================================================
        // Save each alert
        // ====================================================

        for (
        alert in alerts
        ) {

            preferences
                .edit()

                .putLong(
                    KEY_ALERT_LIMIT_PREFIX +
                            alert.id,

                    alert.limitBytes
                )

                .putBoolean(
                    KEY_ALERT_ENABLED_PREFIX +
                            alert.id,

                    alert.isEnabled
                )

                .putBoolean(
                    KEY_ALERT_TRIGGERED_PREFIX +
                            alert.id,

                    alert.isTriggered
                )

                .apply()
        }
    }


    // ========================================================
    // LOAD ALERTS
    // ========================================================

    fun loadAlerts():
            List<DailyDataAlert> {

        val savedIds =
            preferences.getString(
                KEY_ALERT_IDS,
                ""
            ) ?: ""


        // ----------------------------------------------------
        // No alerts
        // ----------------------------------------------------

        if (
            savedIds.isBlank()
        ) {

            return emptyList()
        }


        // ----------------------------------------------------
        // Convert IDs
        // ----------------------------------------------------

        val ids =
            savedIds
                .split(",")
                .mapNotNull {
                    it.trim()
                        .toIntOrNull()
                }
                .distinct()


        val alerts =
            mutableListOf<DailyDataAlert>()


        // ====================================================
        // Load each alert
        // ====================================================

        for (
        id in ids
        ) {

            val limitBytes =
                preferences.getLong(

                    KEY_ALERT_LIMIT_PREFIX +
                            id,

                    0L
                )


            // ------------------------------------------------
            // Invalid alert
            // ------------------------------------------------

            if (
                limitBytes <= 0L
            ) {

                continue
            }


            val enabled =
                preferences.getBoolean(

                    KEY_ALERT_ENABLED_PREFIX +
                            id,

                    true
                )


            val triggered =
                preferences.getBoolean(

                    KEY_ALERT_TRIGGERED_PREFIX +
                            id,

                    false
                )


            alerts.add(

                DailyDataAlert(

                    id =
                        id,

                    limitBytes =
                        limitBytes,

                    isEnabled =
                        enabled,

                    isTriggered =
                        triggered
                )
            )
        }


        // ----------------------------------------------------
        // Smallest limit first
        // ----------------------------------------------------

        return alerts.sortedBy {
            it.limitBytes
        }
    }


    // ========================================================
    // UPDATE PLAN ENABLED
    // ========================================================

    fun setPlanEnabled(
        enabled: Boolean
    ) {

        preferences
            .edit()
            .putBoolean(
                KEY_PLAN_ENABLED,
                enabled
            )
            .apply()
    }


    // ========================================================
    // UPDATE TOTAL DATA
    // ========================================================

    fun setTotalData(
        totalDataBytes: Long
    ) {

        preferences
            .edit()
            .putLong(
                KEY_TOTAL_DATA,
                totalDataBytes
            )
            .apply()
    }


    // ========================================================
    // UPDATE START TIME
    // ========================================================

    fun setStartTime(

        hour: Int,

        minute: Int

    ) {

        preferences
            .edit()

            .putInt(
                KEY_START_HOUR,
                hour
            )

            .putInt(
                KEY_START_MINUTE,
                minute
            )

            .apply()
    }


    // ========================================================
    // UPDATE END TIME
    // ========================================================

    fun setEndTime(

        hour: Int,

        minute: Int

    ) {

        preferences
            .edit()

            .putInt(
                KEY_END_HOUR,
                hour
            )

            .putInt(
                KEY_END_MINUTE,
                minute
            )

            .apply()
    }


    // ========================================================
    // UPDATE CYCLE START
    // ========================================================

    fun setCycleStartTime(
        time: Long
    ) {

        preferences
            .edit()
            .putLong(
                KEY_CYCLE_START,
                time
            )
            .apply()
    }


    // ========================================================
    // UPDATE ALERT ENABLED
    // ========================================================

    fun setAlertEnabled(

        alertId: Int,

        enabled: Boolean

    ) {

        preferences
            .edit()
            .putBoolean(

                KEY_ALERT_ENABLED_PREFIX +
                        alertId,

                enabled
            )
            .apply()
    }


    // ========================================================
    // UPDATE ALERT TRIGGERED
    // ========================================================

    fun setAlertTriggered(

        alertId: Int,

        triggered: Boolean

    ) {

        preferences
            .edit()
            .putBoolean(

                KEY_ALERT_TRIGGERED_PREFIX +
                        alertId,

                triggered
            )
            .apply()
    }


    // ========================================================
    // DELETE ALERT
    // ========================================================

    fun deleteAlert(
        alertId: Int
    ) {

        // ----------------------------------------------------
        // Get current IDs
        // ----------------------------------------------------

        val currentIds =
            preferences
                .getString(
                    KEY_ALERT_IDS,
                    ""
                )
                ?: ""


        val updatedIds =
            currentIds
                .split(",")
                .mapNotNull {
                    it.trim()
                        .toIntOrNull()
                }
                .filter {
                    it != alertId
                }


        // ----------------------------------------------------
        // Remove alert data
        // ----------------------------------------------------

        preferences
            .edit()

            .remove(
                KEY_ALERT_LIMIT_PREFIX +
                        alertId
            )

            .remove(
                KEY_ALERT_ENABLED_PREFIX +
                        alertId
            )

            .remove(
                KEY_ALERT_TRIGGERED_PREFIX +
                        alertId
            )

            .putString(
                KEY_ALERT_IDS,
                updatedIds.joinToString(",")
            )

            .apply()
    }


    // ========================================================
    // CLEAR ALL ALERTS
    // ========================================================

    private fun clearSavedAlerts() {

        val savedIds =
            preferences.getString(
                KEY_ALERT_IDS,
                ""
            ) ?: ""


        if (
            savedIds.isBlank()
        ) {

            return
        }


        val ids =
            savedIds
                .split(",")
                .mapNotNull {
                    it.trim()
                        .toIntOrNull()
                }


        val editor =
            preferences.edit()


        // ----------------------------------------------------
        // Remove every alert
        // ----------------------------------------------------

        for (
        id in ids
        ) {

            editor.remove(
                KEY_ALERT_LIMIT_PREFIX +
                        id
            )

            editor.remove(
                KEY_ALERT_ENABLED_PREFIX +
                        id
            )

            editor.remove(
                KEY_ALERT_TRIGGERED_PREFIX +
                        id
            )
        }


        editor
            .remove(
                KEY_ALERT_IDS
            )
            .apply()
    }


    // ========================================================
    // DELETE COMPLETE PLAN
    // ========================================================

    fun deletePlan() {

        preferences
            .edit()
            .clear()
            .apply()
    }
}