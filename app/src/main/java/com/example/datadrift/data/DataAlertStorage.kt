package com.example.datadrift.data

// ============================================================
// DataDrift - DataAlertStorage
// ============================================================
//
// DATA LIMIT ALERT STORAGE
//
// Is file ka kaam:
// 1. Alerts save karna
// 2. Alerts load karna
// 3. ON/OFF save karna
// 4. Repeat ON/OFF save karna
// 5. Last triggered threshold save karna
// 6. Custom cycle save karna
// 7. Alert delete karna
//
// SharedPreferences ka use kiya gaya hai.
//
// ============================================================

import android.content.Context

import com.example.datadrift.model.DataAlert

class DataAlertStorage(
    context: Context
) {

    // ========================================================
    // SHARED PREFERENCES
    // ========================================================

    private val preferences =
        context.getSharedPreferences(
            "datadrift_preferences",
            Context.MODE_PRIVATE
        )

    // ========================================================
    // PREFERENCE KEYS
    // ========================================================

    private companion object {

        // ----------------------------------------------------
        // All alert IDs
        // ----------------------------------------------------

        const val KEY_ALERT_IDS =
            "custom_alert_ids"

        // ----------------------------------------------------
        // Alert limit
        // ----------------------------------------------------

        const val KEY_ALERT_LIMIT_PREFIX =
            "custom_alert_limit_"

        // ----------------------------------------------------
        // Alert ON/OFF
        // ----------------------------------------------------

        const val KEY_ALERT_ENABLED_PREFIX =
            "custom_alert_enabled_"

        // ----------------------------------------------------
        // Repeat ON/OFF
        // ----------------------------------------------------

        const val KEY_ALERT_REPEATING_PREFIX =
            "custom_alert_repeating_"

        // ----------------------------------------------------
        // Last triggered threshold
        // ----------------------------------------------------

        const val KEY_ALERT_THRESHOLD_PREFIX =
            "custom_alert_threshold_"

        // ----------------------------------------------------
        // Last triggered custom cycle
        // ----------------------------------------------------

        const val KEY_ALERT_CYCLE_PREFIX =
            "custom_alert_triggered_cycle_"

        // ----------------------------------------------------
        // Alert initialization state
        //
        // Service use karta hai taaki naya alert add karne par
        // current usage ko baseline maana ja sake.
        // ----------------------------------------------------

        const val KEY_ALERT_INITIALIZED_PREFIX =
            "custom_alert_initialized_"
    }

    // ========================================================
    // SAVE ALL ALERTS
    // ========================================================

    fun saveAlerts(
        alerts: List<DataAlert>
    ) {

        // ----------------------------------------------------
        // Purane saved alert data ko clear karna
        // ----------------------------------------------------

        clearSavedAlerts()

        // ----------------------------------------------------
        // Alert IDs ko save karna
        //
        // Example:
        //
        // 1,2,3
        //
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
        // HAR ALERT SAVE
        // ====================================================

        for (
        alert in alerts
        ) {

            preferences
                .edit()

                // ------------------------------------------------
                // Limit
                // ------------------------------------------------

                .putLong(
                    KEY_ALERT_LIMIT_PREFIX +
                            alert.id,
                    alert.limitBytes
                )

                // ------------------------------------------------
                // ON/OFF
                // ------------------------------------------------

                .putBoolean(
                    KEY_ALERT_ENABLED_PREFIX +
                            alert.id,
                    alert.isEnabled
                )

                // ------------------------------------------------
                // Repeat ON/OFF
                // ------------------------------------------------

                .putBoolean(
                    KEY_ALERT_REPEATING_PREFIX +
                            alert.id,
                    alert.isRepeating
                )

                // ------------------------------------------------
                // Last triggered threshold
                // ------------------------------------------------

                .putLong(
                    KEY_ALERT_THRESHOLD_PREFIX +
                            alert.id,
                    alert.lastTriggeredThreshold
                )

                // ------------------------------------------------
                // Last triggered custom cycle
                // ------------------------------------------------

                .putLong(
                    KEY_ALERT_CYCLE_PREFIX +
                            alert.id,
                    alert.lastTriggeredCycleStartTime
                )

                .apply()
        }
    }

    // ========================================================
    // LOAD ALL ALERTS
    // ========================================================

    fun loadAlerts():
            List<DataAlert> {

        // ----------------------------------------------------
        // Saved IDs
        // ----------------------------------------------------

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
        // String IDs -> Int IDs
        //
        // "1,2,3"
        //       ↓
        // [1, 2, 3]
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
            mutableListOf<DataAlert>()

        // ====================================================
        // LOAD EACH ALERT
        // ====================================================

        for (
        id in ids
        ) {

            // ------------------------------------------------
            // Limit
            // ------------------------------------------------

            val limitBytes =
                preferences.getLong(
                    KEY_ALERT_LIMIT_PREFIX +
                            id,
                    0L
                )

            // ------------------------------------------------
            // Invalid alert skip
            // ------------------------------------------------

            if (
                limitBytes <= 0L
            ) {

                continue
            }

            // ------------------------------------------------
            // ON/OFF
            // ------------------------------------------------

            val enabled =
                preferences.getBoolean(
                    KEY_ALERT_ENABLED_PREFIX +
                            id,
                    true
                )

            // ------------------------------------------------
            // Repeat ON/OFF
            // ------------------------------------------------

            val repeating =
                preferences.getBoolean(
                    KEY_ALERT_REPEATING_PREFIX +
                            id,
                    false
                )

            // ------------------------------------------------
            // Last triggered threshold
            // ------------------------------------------------

            val lastTriggeredThreshold =
                preferences.getLong(
                    KEY_ALERT_THRESHOLD_PREFIX +
                            id,
                    0L
                )

            // ------------------------------------------------
            // Last triggered cycle
            // ------------------------------------------------

            val lastTriggeredCycleStartTime =
                preferences.getLong(
                    KEY_ALERT_CYCLE_PREFIX +
                            id,
                    0L
                )

            // =================================================
            // DATA ALERT OBJECT
            // =================================================

            alerts.add(

                DataAlert(

                    id =
                        id,

                    limitBytes =
                        limitBytes,

                    isEnabled =
                        enabled,

                    isRepeating =
                        repeating,

                    lastTriggeredThreshold =
                        lastTriggeredThreshold,

                    lastTriggeredCycleStartTime =
                        lastTriggeredCycleStartTime
                )
            )
        }

        // ----------------------------------------------------
        // Small limit first
        // ----------------------------------------------------

        return alerts.sortedBy {
            it.limitBytes
        }
    }

    // ========================================================
    // SAVE / UPDATE SINGLE ALERT
    // ========================================================

    fun saveAlert(
        alert: DataAlert
    ) {

        val currentAlerts =
            loadAlerts()
                .toMutableList()

        // ----------------------------------------------------
        // Check whether alert already exists
        // ----------------------------------------------------

        val existingIndex =
            currentAlerts.indexOfFirst {

                it.id ==
                        alert.id
            }

        if (
            existingIndex >= 0
        ) {

            // ------------------------------------------------
            // Existing alert update
            // ------------------------------------------------

            currentAlerts[
                existingIndex
            ] =
                alert

        } else {

            // ------------------------------------------------
            // New alert
            // ------------------------------------------------

            currentAlerts.add(
                alert
            )
        }

        // ----------------------------------------------------
        // Save complete list
        // ----------------------------------------------------

        saveAlerts(
            currentAlerts
        )
    }

    // ========================================================
    // SET ALERT ENABLED
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
    // SET REPEAT ON / OFF
    // ========================================================

    fun setAlertRepeating(
        alertId: Int,
        repeating: Boolean
    ) {

        preferences
            .edit()
            .putBoolean(
                KEY_ALERT_REPEATING_PREFIX +
                        alertId,
                repeating
            )
            .apply()
    }

    // ========================================================
    // SET LAST TRIGGERED THRESHOLD
    // ========================================================

    fun setLastTriggeredThreshold(
        alertId: Int,
        threshold: Long
    ) {

        preferences
            .edit()
            .putLong(
                KEY_ALERT_THRESHOLD_PREFIX +
                        alertId,
                threshold
            )
            .apply()
    }

    // ========================================================
    // SET LAST TRIGGERED CYCLE
    // ========================================================

    fun setLastTriggeredCycleStartTime(
        alertId: Int,
        cycleStartTime: Long
    ) {

        preferences
            .edit()
            .putLong(
                KEY_ALERT_CYCLE_PREFIX +
                        alertId,
                cycleStartTime
            )
            .apply()
    }

    // ========================================================
    // DELETE SINGLE ALERT
    // ========================================================

    fun deleteAlert(
        alertId: Int
    ) {

        // ----------------------------------------------------
        // Existing IDs
        // ----------------------------------------------------

        val currentIds =
            preferences
                .getString(
                    KEY_ALERT_IDS,
                    ""
                )
                ?: ""

        // ----------------------------------------------------
        // Remove selected ID
        // ----------------------------------------------------

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

        // ====================================================
        // REMOVE ALL DATA OF THIS ALERT
        // ====================================================

        preferences
            .edit()

            // ------------------------------------------------
            // Limit
            // ------------------------------------------------

            .remove(
                KEY_ALERT_LIMIT_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // Enabled
            // ------------------------------------------------

            .remove(
                KEY_ALERT_ENABLED_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // Repeat
            // ------------------------------------------------

            .remove(
                KEY_ALERT_REPEATING_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // Threshold
            // ------------------------------------------------

            .remove(
                KEY_ALERT_THRESHOLD_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // Cycle
            // ------------------------------------------------

            .remove(
                KEY_ALERT_CYCLE_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // IMPORTANT:
            // Service initialization state bhi remove.
            // ------------------------------------------------

            .remove(
                KEY_ALERT_INITIALIZED_PREFIX +
                        alertId
            )

            // ------------------------------------------------
            // Updated ID list
            // ------------------------------------------------

            .putString(
                KEY_ALERT_IDS,
                updatedIds.joinToString(",")
            )

            .apply()
    }

    // ========================================================
    // CLEAR ALL SAVED ALERTS
    // ========================================================

    private fun clearSavedAlerts() {

        val savedIds =
            preferences.getString(
                KEY_ALERT_IDS,
                ""
            ) ?: ""

        // ----------------------------------------------------
        // Nothing to clear
        // ----------------------------------------------------

        if (
            savedIds.isBlank()
        ) {

            return
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

        val editor =
            preferences.edit()

        // ====================================================
        // REMOVE DATA OF EVERY ALERT
        // ====================================================

        for (
        id in ids
        ) {

            // -----------------------------------------------
            // Limit
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_LIMIT_PREFIX +
                        id
            )

            // -----------------------------------------------
            // Enabled
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_ENABLED_PREFIX +
                        id
            )

            // -----------------------------------------------
            // Repeat
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_REPEATING_PREFIX +
                        id
            )

            // -----------------------------------------------
            // Threshold
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_THRESHOLD_PREFIX +
                        id
            )

            // -----------------------------------------------
            // Cycle
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_CYCLE_PREFIX +
                        id
            )

            // -----------------------------------------------
            // Initialization state
            // -----------------------------------------------

            editor.remove(
                KEY_ALERT_INITIALIZED_PREFIX +
                        id
            )
        }

        // ----------------------------------------------------
        // Remove ID list
        // ----------------------------------------------------

        editor
            .remove(
                KEY_ALERT_IDS
            )
            .apply()
    }
}