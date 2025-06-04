# Single Battery Mode Configuration

## Overview

The Hybrid ESS Controller has been modified to operate in single battery mode. The main battery configuration is hidden from the user interface while preserving the ability to quickly reimplement dual battery functionality.

## Changes Made

### 1. Configuration Changes (`Config.java`)
- Removed `mainId()` configuration parameter from user interface
- Changed `supportId()` description to "Battery-Ess" (Primary battery system)
- Added comments preserving the original main battery configuration for future reactivation

### 2. Controller Implementation (`HybridControllerImpl.java`)
- Modified to work with single battery (`supportEss` only)
- Commented out all main battery related code with preservation comments
- Simplified power management logic for single battery operation
- Removed dual battery power splitting algorithms (preserved in comments)
- Simplified energy calculation to use only support battery

### 3. Test Configuration (`MyConfig.java`)
- Removed `mainId` builder and getter methods
- Preserved original code in comments for reactivation

### 4. Test Implementation (`HybridControllerTest.java`)
- Created new single battery test cases:
  - `singleBatteryBasicCharge()` - Tests basic charging behavior
  - `singleBatteryBasicDischarge()` - Tests basic discharging behavior  
  - `singleBatteryRedProtection()` - Tests RED state protection
  - `singleBatteryMinEnergyCheck()` - Tests minimum energy enforcement
- Commented out dual battery test methods for future reactivation
- Updated test setup to use only one battery

## Current Behavior

### Single Battery Operation
- All power management is handled by the single battery (`supportEss`)
- Grid power limits and minimum energy thresholds are enforced
- Battery protection in RED SoC state is maintained
- Flask data logging continues to work for the single battery

### Key Features Preserved
- Grid power limiting (`maxGridPower`)
- Minimum energy enforcement (`defaultMinimumEnergy`)
- SoC-based battery protection
- External data acquisition service integration
- Load shedding warnings (when consumption exceeds available power)

## How to Reactivate Dual Battery Mode

### 1. Configuration
Uncomment the following lines in `Config.java`:
```java
@AttributeDefinition(name = "Main-Ess", description = "ID of Main-Ess. Ess with high capacity, providing power for netload.")
String mainId();
```

### 2. Controller Implementation
In `HybridControllerImpl.java`:
- Uncomment the `mainId` field declaration
- Uncomment main battery initialization in constructors and `activate()` method
- Uncomment the dual battery logic in `run()` method
- Uncomment power splitting methods (`chargePowerSplit`, `dischargePowerSplit`, etc.)
- Uncomment the `conserveRed()` method for single battery protection

### 3. Test Configuration  
In `MyConfig.java`:
- Uncomment the `mainId` field and related builder methods
- Uncomment the `mainId()` getter method

### 4. Test Implementation
In `HybridControllerTest.java`:
- Uncomment the dual battery test constants and channel addresses
- Uncomment the original test methods (`chargeSplit`, `dischargeSplit`, etc.)
- Update `createControllerTest()` to include main battery setup

## Architecture Notes

The power splitting tables (`CHARGE_TABLE` and `DISCHARGE_TABLE`) are preserved in the code for future dual battery reactivation. These tables define how power is distributed between main and support batteries based on their respective SoC states.

The single battery mode maintains the same external interfaces and logging mechanisms, ensuring compatibility with existing monitoring and control systems. 