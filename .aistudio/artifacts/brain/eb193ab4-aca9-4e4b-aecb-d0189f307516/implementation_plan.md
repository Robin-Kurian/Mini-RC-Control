# Robin'z Mini Tank — BLE RC Controller (Revised Architecture)

Robin'z Mini Tank is a specialized native Android application engineered to control a Bluetooth Low Energy (BLE) miniature tank/RC car (PCB identification: `DL-HG-TSLRX / DL-HG-DJGQDB REV:05`) with fail-safe drive mechanics, multi-modal control schemes, real-time telemetry, and low-level protocol diagnostics.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Confirmed Features & PCB Hardware Alignment:**
> - **Hardware Discovered from PCB**: Dual H-bridge motor drivers for differential drive, front headlight output (`前灯`), audio speaker pads (`+SPK-`), pairing button (`K1`), and BLE antenna (`0000fff0` service).
> - **Four Complete Driving Modes** (available in both Portrait and Landscape):
>   1. **Classic Buttons**: Multi-touch Forward/Reverse and Left/Right directional buttons.
>   2. **Split Sliders**: Left vertical slider for progressive throttle (distance-based speed); right horizontal slider for proportional steering.
>   3. **Single Joystick**: 360° circular virtual thumbstick with 0.08 dead-zone, response curve, and spring-back centering.
>   4. **Gyroscope / Gravity Tilt**: Real-time device tilt driving via Android accelerometer/gyroscope with center-level calibration button and visual horizon bubble.
> - **Car Accessories**: Headlights toggle, Horn/Sound trigger, Engine Start, and fail-safe Emergency STOP.
> - **Route Mode**: Path drawing canvas with 20%–100% speed presets, path nodes, undo/clear, and protocol calibration indicator.
> - **Speed Regulation**: Accessible gear selector (20% default, 20%–100% in 10% steps) anchored prominently at center top in all orientations.
> - **Tactile Feedback**: Subtle haptic clicks on touch engagement, axis deflection, and emergency stop.
> - **Auto-reconnection**: Seamless background reconnection to previously paired tank MAC address on startup.
> - **Safety Protocol**: Dispatches fail-safe stop on finger lift, pointer cancel, tilt neutral, app pause, or BLE disconnect. Decoupled protocol layer with `UnknownTankProtocol` logging without sending unverified bytes.

---

## 1. Overview & Core Concept

- **What It Does**: Provides an ultra-responsive, fail-safe native controller interface for a mini BLE tank over platform GATT service `0000fff0-0000-1000-8000-00805f9b34fb` and characteristics `fff1`, `fff2`, `fff4`.
- **Hardware Integration**: Directly models the discovered hardware capabilities (AWD dual-motor differential drive, front lights, speaker output).
- **Control Modalities**: Effortlessly switches between Buttons, Sliders, Joystick, and Gyro tilt modes in both Portrait and Landscape screen orientations.
- **Safety**: Motor throttle is strictly clamped to the user-selected speed cap (default 20%). Immediate stop packets are prioritized whenever controls are released.

---

## 2. User Experience & Visual Design

- **Visual Direction**: Modern tactical dark cockpit aesthetic.
  - **Background**: Deep obsidian/navy (`#0B0F19`, `#111827`) with subtle telemetry grid accents.
  - **Accents**: High-visibility electric cyan (`#00E5FF`, `#00B4D8`) for live telemetry, sliders, and active states.
  - **Safety Indicators**: Vivid warning amber (`#F59E0B`) and emergency red (`#EF4444`, `#DC2626`) for the prominent STOP button.
  - **Typography**: Clean, readable monospace telemetry metrics (`THROTTLE: +0.65`, `STEER: -0.20`, `CAP: 20%`).

### Adaptive Layout Specifications

#### 1. Landscape Orientation (Standard RC Transmitter Layout)
```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Tank Info / BLE Status]        [ SPEED CAP: 20% ]     [Lights] [Sound] [⚙]  │
│                                 [ - ] [SLIDER] [ + ]                         │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   LEFT CONTROL CLUSTER                  CENTER               RIGHT CLUSTER   │
│   ┌────────────────────┐          ┌───────────────┐        ┌──────────────┐  │
│   │ • Sliders Mode:    │          │ [EMERGENCY    │        │ • Sliders:   │  │
│   │   Vertical Throttle│          │    STOP]      │        │   Horiz Turn │  │
│   │ • Buttons Mode:    │          │               │        │ • Buttons:   │  │
│   │   [FWD] / [REV]    │          │ Telemetry HUD │        │   [<] / [>]  │  │
│   │ • Gyro: Pitch info │          │ Mode Selector │        │ • Joystick:  │  │
│   │                    │          │ [Btn|Sld|Joy| │        │   360° Stick │  │
│   │                    │          │      Gyro]    │        │              │  │
│   └────────────────────┘          └───────────────┘        └──────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### 2. Portrait Orientation (Single-Hand or Vertical Two-Thumb Ergonomics)
```
┌──────────────────────────────────────────────────┐
│ [Title: Robin'z Mini Tank]   [BLE] [Lights] [⚙] │
├──────────────────────────────────────────────────┤
│           SPEED CAP: 20% (PROMINENT CENTER)      │
│                [ - ] [====|====] [ + ]           │
├──────────────────────────────────────────────────┤
│   Telemetry HUD & Mode Selector (Chips: 4 Modes) │
│       [Buttons]  [Sliders]  [Joystick]  [Gyro]   │
├──────────────────────────────────────────────────┤
│                                                  │
│          PRIMARY ACTIVE CONTROL ZONE             │
│   • Sliders: Left vertical bar, Right horiz bar  │
│   • Buttons: Left FWD/REV, Right LEFT/RIGHT      │
│   • Joystick: Large centered 360° circular stick │
│   • Gyro: Calibrate button + visual tilt bubble  │
│                                                  │
├──────────────────────────────────────────────────┤
│   [Sound Horn]       [EMERGENCY STOP]   [Route]  │
└──────────────────────────────────────────────────┘
```

---

## 3. Four Complete Control Modes

1. **Classic Buttons Mode**:
   - Multi-touch capable touch zones.
   - Left side: Large Forward and Reverse tactile buttons with visual glow on press.
   - Right side: Large Left and Right turn buttons.
   - Holding produces throttle up to the speed cap; releasing any button immediately zeroes that axis.
2. **Split Sliders Mode (Distance-Based Throttle)**:
   - Left thumb: Vertical slider bar for acceleration and braking/reverse. Speed is strictly proportional to drag distance from the center rest point.
   - Right thumb: Horizontal slider bar for proportional left/right steering deflection.
   - Releasing either thumb springs the slider back to center and resets the respective axis to 0.0.
3. **Single Joystick Mode**:
   - 360° virtual thumbstick occupying a spacious touch arena.
   - Circular vector clamping (magnitude $\le 1.0$), configurable dead-zone (0.08), and smooth exponential response curve.
   - Simultaneous throttle and steering with instant spring-back on pointer release.
4. **Gyroscope / Gravity Tilt Mode**:
   - Uses device `Sensor.TYPE_ROTATION_VECTOR` or `Sensor.TYPE_ACCELEROMETER` for real-time pitch (throttle) and roll (steering) control.
   - Features a one-tap **Calibrate Zero-Point** button so the user can hold their phone at any comfortable angle.
   - Visual artificial horizon / bubble level on screen indicating tilt deflection.
   - Touch-to-activate dead-man safety: movement is only transmitted while holding the "Engage Tilt" sensor pad; releasing immediately stops the tank.

---

## 4. Route Mode & Car Accessories

- **Car Accessories**:
  - **Headlights**: Toggle switch driving the `前灯` output on the PCB.
  - **Sound / Horn**: Momentary trigger driving the `+SPK-` speaker output.
  - **Engine Start**: Action trigger for sound/motor spin-up simulation.
  - **Emergency STOP**: High-priority crimson button with immediate motor cut and haptic pulse.
- **Route Mode Screen**:
  - Canvas for drawing freehand or waypoint driving paths.
  - Speed selector synchronized with the main speed cap (20%–100%).
  - Waypoint metrics (path distance, estimated duration).
  - Clear, Undo, Start, and Pause buttons.
  - Prominent "Protocol calibration required" status badge to guarantee zero unverified byte transmission.

---

## 5. Diagnostics Screen (Hardware Reverse-Engineering Lab)

Hidden under Settings → Developer Diagnostics:
1. Complete BLE service and characteristic tree with property badges (`READ`, `WRITE`, `WRITE_NO_RESP`, `NOTIFY`, `INDICATE`).
2. Real-time notification and indication hex payload logger with microsecond timestamps.
3. Outgoing write logger with transmission status.
4. Manual Hexadecimal Packet Sender:
   - Target characteristic dropdown (FFF1, FFF2, FFF4).
   - Validated hex input field (spaces allowed, regex verified).
   - Explicit "Arm Motors" safety confirmation popup before first write of the session.
   - Wheels-off-the-ground safety banner.
5. Plain text diagnostic session log exporter via Android system share sheet.

---

## 6. Technical Architecture & Component Hierarchy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │                  Adaptive Controller Scaffold                      │ │
│ │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────┐ │ │
│ │  │ButtonsControl│ │SlidersControl│ │JoystickContrl│ │ GyroControl│ │ │
│ │  └──────────────┘ └──────────────┘ └──────────────┘ └────────────┘ │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│    ┌──────────────────────────┐      ┌─────────────────────────────┐   │
│    │     DiagnosticsScreen    │      │         RouteScreen         │   │
│    └──────────────────────────┘      └─────────────────────────────┘   │
└───────────────────┬───────────────────────────────────┬────────────────┘
                    ▼                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                              TankViewModel                             │
│ - StateFlow<TankUiState> (Connected, Battery, SpeedCap, ControlMode)   │
│ - Haptic feedback dispatcher (Vibrator / VibrationEffect)             │
│ - SensorEventListener for Gyroscope / Gravity tilt tracking            │
└───────────────────┬───────────────────────────────────┬────────────────┘
                    ▼                                   ▼
┌───────────────────────────────┐       ┌────────────────────────────────┐
│        DriveController        │       │   TankPreferencesRepository    │
│ - Normalized throttle & steer │       │ - DataStore: Speed cap, mode,  │
│ - Clamping & dead-zone curves │       │   haptics, last paired MAC     │
│ - Zeroing & fail-safe stops   │       └────────────────────────────────┘
└───────────────────┬───────────┘
                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                          TankProtocol Layer                            │
│  - TankProtocol (interface for encodeDrive, encodeStop, encodeLights)  │
│  - UnknownTankProtocol (safe implementation logging without fake bytes)│
└───────────────────┬────────────────────────────────────────────────────┘
                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             BleTankClient                              │
│ - Platform BluetoothGatt & BluetoothLeScanner                          │
│ - FFF0 Service & FFF1, FFF2, FFF4 characteristic discovery             │
│ - Serialized GATT Queue + Update Coalescing (15–20 Hz)                 │
│ - High-priority burst Stop command dispatcher                          │
│ - Auto-reconnect worker for saved MAC address                          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Verification & Testing Strategy

- **Robolectric & Unit Tests**:
  - `DriveControllerTest`: Verify joystick vector clamping, split-slider distance proportional throttle, dead-zone thresholds, and zeroing on pointer release.
  - `GattOperationQueueTest`: Verify sequential execution of GATT operations, coalescing of high-frequency driving packets, and immediate prioritization of STOP commands.
  - `HexValidationTest`: Test hex string sanitization, odd-character handling, and byte array conversion for the diagnostics sender.
  - `GyroscopeDriveTest`: Test pitch/roll angle clamping and zero-point calibration offset math.
- **Compilation & Bundle Size**:
  - Verify clean compilation with `minSdk = 26` and `targetSdk = 36`.
  - Prune unnecessary Firebase/Gemini/Retrofit dependencies to guarantee release APK size is under 10 MB.
