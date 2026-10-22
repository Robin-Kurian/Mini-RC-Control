package com.robinz.minitank

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.robinz.minitank.ble.DiscoveredDevice
import com.robinz.minitank.ui.screens.ScanDeviceDialog
import com.robinz.minitank.controller.ControlMode
import com.robinz.minitank.ui.TankUiState
import com.robinz.minitank.ui.screens.MainControllerScreen
import com.robinz.minitank.ui.theme.RobinMiniTankTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
    fun bluetoothDevicesRemainReachableInLandscape() {
        assertBluetoothDialogScrolls()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-port")
    fun bluetoothDevicesRemainReachableInPortrait() {
        assertBluetoothDialogScrolls()
    }

    private fun assertBluetoothDialogScrolls() {
        var selected: String? = null
        val devices = (1..20).map {
            DiscoveredDevice("Tank $it", "AA:BB:CC:DD:EE:%02X".format(it), -60, true)
        } + DiscoveredDevice(null, "11:22:33:44:55:66", -80, false)
        composeTestRule.setContent {
            RobinMiniTankTheme {
                ScanDeviceDialog(
                    scannedDevices = devices,
                    isScanning = true,
                    onStartScan = {},
                    onStopScan = {},
                    onSelectDevice = { address, _ -> selected = address },
                    onDismiss = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Show All").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Tank 20").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(devices[19].address, selected)
    }

    @Test
    fun testMainControllerScreenDisplaysTitleAndCenteredSpeed() {
        var emergencyStopClicked = false
        val uiState = TankUiState(
            speedCapPercent = 20,
            controlMode = ControlMode.SLIDERS
        )

        composeTestRule.setContent {
            RobinMiniTankTheme {
                MainControllerScreen(
                    uiState = uiState,
                    onButtonsInput = { _, _, _, _ -> },
                    onSlidersInput = { _, _, _, _ -> },
                    onJoystickInput = { _, _, _ -> },
                    onGyroEngage = {},
                    onCalibrateGyroZero = {},
                    onDriveReleased = {},
                    onEmergencyStop = { emergencyStopClicked = true },
                    onSpeedCapChanged = {},
                    onIncrementSpeedCap = {},
                    onDecrementSpeedCap = {},
                    onSelectControlMode = {},
                    onBluetoothClick = {},
                    onLightsToggle = {},
                    onSoundToggle = {},
                    onRouteClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // Assert Title is displayed
        composeTestRule.onNodeWithText("Robin'z Mini Tank").assertIsDisplayed()

        // Assert Prominent Centered Speed Cap Display shows "20%"
        composeTestRule.onNodeWithTag("speed_cap_display").assertIsDisplayed()
        composeTestRule.onNodeWithText("20%").assertIsDisplayed()

        // Assert Emergency Stop Button is displayed and clickable
        val stopButton = composeTestRule.onNodeWithTag("emergency_stop_button")
        stopButton.assertIsDisplayed()
        stopButton.performClick()
        assertTrue(emergencyStopClicked)
    }

    @Test
    fun testModeSwitchingInteraction() {
        var selectedMode: ControlMode? = null
        val uiState = TankUiState(
            speedCapPercent = 40,
            controlMode = ControlMode.SLIDERS
        )

        composeTestRule.setContent {
            RobinMiniTankTheme {
                MainControllerScreen(
                    uiState = uiState,
                    onButtonsInput = { _, _, _, _ -> },
                    onSlidersInput = { _, _, _, _ -> },
                    onJoystickInput = { _, _, _ -> },
                    onGyroEngage = {},
                    onCalibrateGyroZero = {},
                    onDriveReleased = {},
                    onEmergencyStop = {},
                    onSpeedCapChanged = {},
                    onIncrementSpeedCap = {},
                    onDecrementSpeedCap = {},
                    onSelectControlMode = { selectedMode = it },
                    onBluetoothClick = {},
                    onLightsToggle = {},
                    onSoundToggle = {},
                    onRouteClick = {},
                    onSettingsClick = {}
                )
            }
        }

        // Tap "Buttons" mode chip
        composeTestRule.onNodeWithTag("mode_chip_BUTTONS").performClick()
        assertEquals(ControlMode.BUTTONS, selectedMode)
    }

    @Test
    fun testLightsBlinkButtonInteraction() {
        var blinkClicked = false
        val uiState = TankUiState(
            lightsBlinkEnabled = false
        )

        composeTestRule.setContent {
            RobinMiniTankTheme {
                MainControllerScreen(
                    uiState = uiState,
                    onButtonsInput = { _, _, _, _ -> },
                    onSlidersInput = { _, _, _, _ -> },
                    onJoystickInput = { _, _, _ -> },
                    onGyroEngage = {},
                    onCalibrateGyroZero = {},
                    onDriveReleased = {},
                    onEmergencyStop = {},
                    onSpeedCapChanged = {},
                    onIncrementSpeedCap = {},
                    onDecrementSpeedCap = {},
                    onSelectControlMode = {},
                    onBluetoothClick = {},
                    onLightsToggle = {},
                    onLightsBlinkToggle = { blinkClicked = true },
                    onSoundToggle = {},
                    onRouteClick = {},
                    onSettingsClick = {}
                )
            }
        }

        val blinkButton = composeTestRule.onNodeWithTag("lights_blink_button")
        blinkButton.assertIsDisplayed()
        blinkButton.performClick()
        assertTrue(blinkClicked)
    }
}
