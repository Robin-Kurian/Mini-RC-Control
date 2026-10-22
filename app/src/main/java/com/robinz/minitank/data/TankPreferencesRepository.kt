package com.robinz.minitank.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.robinz.minitank.controller.ControlMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tank_preferences")

data class TankPreferences(
    val speedCapPercent: Int = 20,
    val controlMode: ControlMode = ControlMode.BUTTONS,
    val hapticFeedbackEnabled: Boolean = true,
    val autoReconnectEnabled: Boolean = true,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val initialSpeedBoostEnabled: Boolean = false
)

class TankPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val SPEED_CAP = intPreferencesKey("speed_cap_percent")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val INITIAL_SPEED_BOOST = booleanPreferencesKey("initial_speed_boost")
    }

    val preferencesFlow: Flow<TankPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val speedCap = preferences[PreferencesKeys.SPEED_CAP] ?: 20
            val modeStr = preferences[PreferencesKeys.CONTROL_MODE] ?: ControlMode.BUTTONS.name
            val mode = try {
                ControlMode.valueOf(modeStr)
            } catch (e: Exception) {
                ControlMode.BUTTONS
            }
            val haptic = preferences[PreferencesKeys.HAPTIC_ENABLED] ?: true
            val autoReconnect = preferences[PreferencesKeys.AUTO_RECONNECT] ?: true
            val lastAddress = preferences[PreferencesKeys.LAST_DEVICE_ADDRESS]
            val lastName = preferences[PreferencesKeys.LAST_DEVICE_NAME]
            val speedBoost = preferences[PreferencesKeys.INITIAL_SPEED_BOOST] ?: false

            TankPreferences(
                speedCapPercent = speedCap.coerceIn(20, 100),
                controlMode = mode,
                hapticFeedbackEnabled = haptic,
                autoReconnectEnabled = autoReconnect,
                lastDeviceAddress = lastAddress,
                lastDeviceName = lastName,
                initialSpeedBoostEnabled = speedBoost
            )
        }

    suspend fun updateSpeedCap(speedCapPercent: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPEED_CAP] = speedCapPercent.coerceIn(20, 100)
        }
    }

    suspend fun updateControlMode(mode: ControlMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTROL_MODE] = mode.name
        }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun updateAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun saveLastConnectedDevice(address: String, name: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_DEVICE_ADDRESS] = address
            if (name != null) {
                preferences[PreferencesKeys.LAST_DEVICE_NAME] = name
            }
        }
    }

    suspend fun clearLastConnectedDevice() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LAST_DEVICE_ADDRESS)
            preferences.remove(PreferencesKeys.LAST_DEVICE_NAME)
        }
    }

    suspend fun updateInitialSpeedBoost(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.INITIAL_SPEED_BOOST] = enabled
        }
    }
}
