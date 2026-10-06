Let me explore the repository structure properly:Perfect! Now I have enough info to build you a detailed implementation plan. Let me create a comprehensive document:

## **BLE Heatmap Simulator - Implementation Plan**

### **Phase 0: Setup (15 min)**
- [x] Fork `Phlekies/smart-home-simulator` to your account
- [x] Clone locally
- [x] Create branch: `feature/ble-adapter`
- [x] Run the project to confirm it builds

---

### **Phase 1: Physics Layer (1-2 hours)**

**File:** `src/main/java/io/github/phlekies/smarthome/physics/RadioMath.java`

**Changes:**
- [x] Add BLE-specific constants:
  ```java
  // BLE: 2402-2480 MHz (ISM band 2.4 GHz)
  public static final double BLE_CENTER_FREQ_MHZ = 2441.0;  // Center frequency
  public static final double BLE_BANDWIDTH_HZ = 1e6;        // 1 MHz bandwidth
  public static final double WIFI_CENTER_FREQ_MHZ = 2400.0; // For comparison
  public static final double WIFI_BANDWIDTH_HZ = 20e6;      // 20 MHz typical
  ```

- [x] Add path loss exponent adjustment method:
  ```java
  /**
   * BLE path loss exponent varies by environment.
   * WiFi: typically 2.0-2.5
   * BLE: typically 2.5-4.0 (higher in cluttered environments)
   */
  public static double getPathLossExponent(String environment) {
      // "open", "residential", "office", "warehouse"
      // Return appropriate n value per environment type
  }
  ```

- [x] No changes needed to core formulas (`fsplDb`, `logDistanceLossDb`, etc.) — they work for any frequency

**Test:** Verify formulas still pass existing tests

---

### **Phase 2: Model Layer (2-3 hours)**

#### **2.1 Beacon Class** (New file or rename Sensor)
**File:** `src/main/java/io/github/phlekies/smarthome/model/Beacon.java`

**Option A: Create new `Beacon` class extending `Device`**
```java
public class Beacon extends Device {
    // BLE-specific properties
    private double txPowerDbm = -6.0;      // BLE typical: -20 to +4 dBm
    private String uuid = "00000000-0000-0000-0000-000000000000";
    private String macAddress = "00:00:00:00:00:00";
    private double advertisingIntervalMs = 100.0; // 100 ms typical
    private double frequency = 2441.0;     // MHz (BLE center freq)
    
    // getters/setters similar to Sensor
}
```

**Option B: Extend existing `Sensor` class**
- [ ] Change default `DEFAULT_TX_POWER_DBM` from `20.0` → `-6.0` (BLE typical)
- [ ] Add UUID and MAC address fields
- [ ] Add advertising interval property
- [ ] Update UI labels (will do in Phase 4)

**Recommended:** Option A (cleaner, BLE-specific)

**Changes to make:**
- [x] Set TX power range: -20 to +4 dBm (instead of WiFi's 17-20 dBm)
- [x] Add antenna gain property: 0-2 dBi (instead of WiFi's 2-3 dBi)
- [x] Add UUID property (4-byte identifier)
- [x] Add MAC address property
- [x] Keep TX polarization (same physics)
- [x] Copy method (deep copy like Sensor has)

#### **2.2 Scanner Class** (New file or rename Hub)
**File:** `src/main/java/io/github/phlekies/smarthome/model/Scanner.java`

**Changes:**
- [x] Rename conceptually from "Hub" (WiFi gateway) → "Scanner" (BLE receiver)
- [x] Update RX sensitivity: -90 dBm (WiFi) → -100 to -104 dBm (BLE)
- [x] Change receiver gain: 0-2 dBi (BLE typical)
- [x] Add RSSI threshold property (for beacon detection)
- [x] Keep polarization, antenna properties (same physics)

**Code change example:**
```java
public class Scanner extends Device {
    // BLE uses -100 dBm sensitivity vs WiFi -90 dBm
    private double rxSensitivityDbm = -100.0;
    private double receiverGainDb = 1.0;
    private double rssiThresholdDbm = -120.0; // Below this = out of range
    // ... rest similar to Hub
}
```

#### **2.3 Environment Class**
**File:** `src/main/java/io/github/phlekies/smarthome/model/Environment.java` (likely exists)

- [x] Add frequency band property (WiFi 2.4/5GHz vs BLE 2.4GHz)
- [x] Add environment type enum: `OPEN`, `RESIDENTIAL`, `OFFICE`, `WAREHOUSE`
- [x] Store path loss exponent per environment for BLE

---

### **Phase 3: Simulation Layer (2-3 hours)**

**File:** `src/main/java/io/github/phlekies/smarthome/simulation/PropagationEngine.java` (or similar)

**Changes:**
- [ ] Update link budget calculation to use BLE parameters:
  ```
  BLE Link Budget:
  Pr = Pt + Gt + Gr - PL(d) - α·d - ΣL_walls - L_reflection - L_diffraction - L_scattering
  
  Where:
  Pt = Beacon TX power (-20 to +4 dBm)
  Gt = Beacon TX gain (0-2 dB)
  Gr = Scanner RX gain (0-2 dB)
  PL(d) = Path loss (frequency-dependent, 2441 MHz)
  d = distance
  α = absorption loss
  L_walls = material-dependent attenuation
  ```

- [x] Adjust path loss exponent based on environment (2.5-4.0 for BLE vs 2.0-2.5 for WiFi)
- [x] Update noise floor calculation for BLE bandwidth (1 MHz):
  ```java
  // BLE noise floor (1 MHz bandwidth)
  double bleNoiseFloor = RadioMath.noiseFloorDbm(1e6, 5.0); // ~-99 dBm
  
  // vs WiFi (20 MHz)
  double wifiNoiseFloor = RadioMath.noiseFloorDbm(20e6, 5.0); // ~-89 dBm
  ```

- [x] Update heatmap metric calculations (no new logic needed, same formulas)
- [x] Test with known BLE RSSI measurements

---

### **Phase 4: UI Layer (1-2 hours)**

**Files to update:**
- [x] Toolbar labels and presets for BLE
- [x] Side panel: Update device property names (UUID, MAC, Adv interval, RSSI threshold)
- [x] Heatmap legend: Power ranges cover BLE levels (-110 to -35 dBm) and show BLE indicator
- [x] Antenna property UI: Beacon and Scanner properties
- [x] Add frequency display: Always selectable as 2.4 GHz (BLE - 2441 MHz)
- [x] Status bar mode indicator badge (BLE Mode vs WiFi)
- [x] Default demo scenario initialized with BLE frequency and devices

---

### **Phase 5: Materials & Calibration (1 hour)**

**File:** `src/main/java/io/github/phlekies/smarthome/model/material/Materials.java`

**Changes:**
- [x] Add/adjust wall material attenuation for BLE (2.4 GHz figures apply for BLE band)
  - Drywall: ~3 dB
  - Concrete: ~10 dB
  - Metal: ~18 dB
  - Glass: ~2 dB
- [x] Add path loss exponent per environment type (Open: 2.0, Residential: 2.5, Office: 3.0, Warehouse: 3.5)

---

### **Phase 6: Testing & Validation (1-2 hours)**

**Regression tests:**
- [x] All existing physics tests should pass (frequency-independent formulas)
- [x] WiFi examples should still work (if supporting both modes)

**New BLE tests:**
- [x] Test BLE constants and environment path loss exponents
- [x] Test Beacon and Scanner domain models (default values, deep copy)
- [x] Test SimulatorModel BLE mode detection and auto-creation of Beacon/Scanner

---

### **Phase 7: Documentation & Examples (30 min)**

**Files:**
- [x] Add example project with BLE beacons in a floor plan (`DemoScenario.bleDeployment()`)
- [x] Add "BLE beacon deployment" option to "Open example" menu

---

## **Summary Table**

| Phase | Component | Effort | Files |
|-------|-----------|--------|-------|
| 1 | Physics (constants) | 30 min | RadioMath.java |
| 2 | Model (Beacon, Scanner) | 2-3 hrs | Beacon.java, Scanner.java, Environment.java |
| 3 | Simulation (link budget) | 2-3 hrs | PropagationEngine.java |
| 4 | UI (labels, ranges) | 1-2 hrs | Toolbar, SidePanel, CSS |
| 5 | Materials & calibration | 1 hr | Materials.java, BLE constants |
| 6 | Testing | 1-2 hrs | Tests (new + regression) |
| 7 | Docs & examples | 30 min | README, examples |
| **Total** | | **8-14 hours** | |

---

## **Recommended Implementation Order**

1. **Start with Phase 1** (physics constants) — small, isolated, no dependencies
2. **Then Phase 2** (model classes) — builds on Phase 1
3. **Then Phase 3** (simulation) — integrates Phases 1-2
4. **Then Phase 5** (materials) — important for calibration
5. **Then Phase 4** (UI) — cosmetic but improves usability
6. **Then Phase 6** (testing) — validate everything works
7. **Finally Phase 7** (docs) — polish and share

---

## **Branching Strategy**

```bash
# Start
git checkout -b feature/ble-adapter

# After Phase 1-2
git commit -m "feat: add Beacon and Scanner classes for BLE support"

# After Phase 3
git commit -m "feat: update propagation engine with BLE link budget"

# After Phase 5
git commit -m "feat: calibrate materials and path loss for BLE"

# After Phase 4
git commit -m "feat: update UI labels and ranges for BLE"

# After Phase 6
git commit -m "test: add BLE propagation and heatmap tests"

# Final
git push origin feature/ble-adapter
git create-pr  # Create PR back to main
```

---

## **Key Dependencies & Assumptions**

✅ **No new dependencies needed** — uses existing Java/JavaFX stack  
✅ **Physics layer is reusable** — formulas work for any frequency  
✅ **UI architecture supports** — just update labels and ranges  
⚠️ **Need calibration data** — real BLE RSSI measurements for 1-2 scenarios  
⚠️ **Path loss exponents vary** — may need material-specific tuning

---

Would you like me to:
1. **Start coding Phase 1** (physics constants)?
2. **Deep dive into Phase 2** (Beacon/Scanner classes)?
3. **Help with test strategy** for validation?
4. **Clarify any specific component**?