# Background SMS Monitoring Implementation

## Overview
The PesaMind app is now configured to automatically start when the phone boots and continuously monitor incoming SMS messages even when the app is closed. This ensures that all transaction messages are captured and processed at all times.

## What Was Implemented

### 1. **BootReceiver** (`BootReceiver.kt`)
- **Location**: `app/src/main/java/cc/dlabs/pesamind/features/settings/notifications/BootReceiver.kt`
- **Purpose**: Listens for the device boot completion event
- **Functionality**:
  - Automatically starts when the phone powers on
  - Launches the `MessageMonitoringService` to ensure SMS monitoring runs in the background
  - Launches the main app activity so PesaMind is ready to use
  - Handles both standard BOOT_COMPLETED and vendor-specific QUICKBOOT_POWERON actions

### 2. **MessageMonitoringService** (`MessageMonitoringService.kt`)
- **Location**: `app/src/main/java/cc/dlabs/pesamind/features/settings/notifications/MessageMonitoringService.kt`
- **Purpose**: Keeps the app alive in the background for continuous SMS monitoring
- **Functionality**:
  - Runs as a **foreground service** (required on Android 8+)
  - Displays a persistent notification: "PesaMind Active - Monitoring SMS transactions..."
  - Cannot be terminated by the system, even under memory pressure
  - Returns `START_STICKY` to automatically restart if killed
  - Enables the `SmsReceiver` to reliably receive and process messages in the background

### 3. **Updated AndroidManifest.xml**
**New Permissions Added**:
```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

**New Receiver Registration**:
```xml
<receiver
    android:name=".features.settings.notifications.BootReceiver"
    android:exported="true"
    android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.QUICKBOOT_POWERON" />
    </intent-filter>
</receiver>
```

**New Service Registration**:
```xml
<service
    android:name=".features.settings.notifications.MessageMonitoringService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

### 4. **Updated MainActivity**
- Starts the `MessageMonitoringService` whenever the app launches
- Ensures the background monitoring is active even if explicitly started by the user
- Includes error handling for service startup

## How It Works

### Boot Sequence:
1. **Device Powers On** → System broadcasts `BOOT_COMPLETED`
2. **BootReceiver** receives the broadcast
3. **MessageMonitoringService** starts as a foreground service
4. **Main Activity** launches so the app is ready to use
5. **SMS monitoring** is now active in the background

### During Runtime:
1. User can close the app (swipe it away, back button, etc.)
2. **MessageMonitoringService** continues running with a persistent notification
3. When an SMS arrives, `SmsReceiver` processes it immediately
4. Transaction is created and notification is shown
5. User can tap the notification to open the app and view the transaction

### When User Opens App:
1. **MainActivity** starts
2. **MessageMonitoringService** is ensured to be running
3. App UI loads normally
4. User can view and manage their transactions

## User Experience

### Benefits:
✅ **Always Monitoring**: SMS messages are captured even if the app is closed
✅ **Automatic Boot**: App starts automatically when phone powers on
✅ **Persistent Notification**: Visual indicator that monitoring is active
✅ **Tap Transactions**: Users can tap notification alerts to view messages instantly
✅ **Reliable Processing**: Foreground service prevents system from terminating monitoring

### What Users Will See:
- A persistent notification in the status bar: "PesaMind Active - Monitoring SMS transactions..."
- This notification cannot be dismissed (as per Android design for essential services)
- Transaction notifications will appear when SMS messages arrive
- Tapping any transaction notification opens the app to that transaction

## Technical Details

### Android API Levels:
- **Minimum**: API 26 (Android 8.0)
- **Target**: API 36
- **Foreground Service**: Required on Android 8+ (minimum API level)

### Permissions Flow:
1. **RECEIVE_BOOT_COMPLETED**: Required for BootReceiver to listen for device boot
2. **FOREGROUND_SERVICE**: Required to run a visible background service
3. **FOREGROUND_SERVICE_SPECIAL_USE**: Declares the special use case of monitoring SMS
4. **RECEIVE_SMS**: Already required for SMS monitoring
5. **READ_PHONE_STATE**: Already required for SIM identification

### Service Lifecycle:
- Service starts with `START_STICKY` flag
- If killed by the system, it will be automatically restarted
- Persistent notification is displayed while service runs
- User cannot remove the notification (Android enforcement for essential services)

## Testing the Implementation

### Test Scenario 1: Boot Test
1. Power off the phone
2. Power on the phone
3. Verify PesaMind appears in the recent apps
4. Check the status bar for "PesaMind Active" notification

### Test Scenario 2: Message Reception (App Closed)
1. Close PesaMind completely
2. Send a test SMS to the monitored phone number
3. Verify a transaction notification appears
4. Tap the notification to open PesaMind and verify the transaction was created

### Test Scenario 3: Message Reception (App Open)
1. Open PesaMind
2. Verify "PesaMind Active" notification is present
3. Send a test SMS
4. Verify transaction is created and notification appears

## Important Notes

### Notification Behavior:
- The persistent "PesaMind Active" notification is **intentional** and **required**
- It cannot be disabled or dismissed by users (Android design requirement)
- This is standard practice for essential background services (e.g., VPN, location sharing)
- Users can tap it to open the app directly

### Battery Impact:
- Foreground services are visible to users and have lower priority for termination
- Background SMS monitoring has minimal battery impact as it only activates on SMS events
- The persistent notification helps users understand the app is running

### Permissions:
- All permissions requested in the app are listed in the manifest
- Users grant permissions on first launch
- The app will NOT function if RECEIVE_SMS permission is denied

## Future Enhancements (Optional)

1. **Settings Control**: Add a toggle in Settings to enable/disable boot startup
2. **Battery Optimization**: Add DozeMode handling for better battery life
3. **Custom Notification**: Allow users to customize the persistent notification appearance
4. **Service Status**: Display service status in the Settings screen
5. **Background Task Scheduling**: Use WorkManager for additional scheduled tasks

## Support & Troubleshooting

### Issue: App doesn't start on boot
- **Solution**: Check that RECEIVE_BOOT_COMPLETED permission is granted in Settings

### Issue: Messages not received when app is closed
- **Solution**: Verify that RECEIVE_SMS permission is granted
- **Solution**: Check that "PesaMind Active" notification is visible in status bar
- **Solution**: Some phones have aggressive battery optimization; add PesaMind to battery whitelist

### Issue: Persistent notification keeps appearing
- **Solution**: This is expected and required for background monitoring
- **Solution**: Users can tap it to open PesaMind at any time

---

**Implementation Date**: June 29, 2026
**Status**: ✅ Complete and tested

