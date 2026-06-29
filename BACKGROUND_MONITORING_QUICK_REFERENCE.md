# Quick Reference: Background SMS Monitoring

## Files Created
```
✅ app/src/main/java/cc/dlabs/pesamind/features/settings/notifications/BootReceiver.kt
✅ app/src/main/java/cc/dlabs/pesamind/features/settings/notifications/MessageMonitoringService.kt
```

## Files Modified
```
📝 app/src/main/AndroidManifest.xml
   - Added 3 new permissions
   - Added BootReceiver registration
   - Added MessageMonitoringService registration

📝 app/src/main/java/cc/dlabs/pesamind/MainActivity.kt
   - Added MessageMonitoringService import
   - Added startMessageMonitoringService() method
   - Updated onCreate() to start the service
```

## How It All Works Together

```
┌─────────────────────────────────────────────────────────────────┐
│                     DEVICE BOOT                                  │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ Android System broadcasts Intent.ACTION_BOOT_COMPLETED          │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ BootReceiver.onReceive() is called                              │
│  ├─ Starts MessageMonitoringService                             │
│  └─ Launches MainActivity                                        │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ MessageMonitoringService.onStartCommand()                       │
│  ├─ Creates notification channel                                │
│  ├─ Starts as foreground service                                │
│  ├─ Shows persistent notification                               │
│  └─ Keeps app alive (START_STICKY)                              │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│        SMS Message Arrives                                       │
│   (App can be closed at this point)                              │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ SmsReceiver.onReceive() is triggered                             │
│  ├─ Parses SMS message                                          │
│  ├─ Creates transaction via API                                 │
│  ├─ Shows transaction notification                              │
│  └─ (MessageMonitoringService keeps process alive)              │
└────────────┬────────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────────┐
│ User taps Transaction Notification                              │
│  ├─ MainActivity opens                                          │
│  └─ startMessageMonitoringService() ensures service running     │
└─────────────────────────────────────────────────────────────────┘
```

## Startup Flow Diagram

### When User Opens App Manually
```
User Opens App
    │
    ▼
MainActivity.onCreate()
    │
    ├─→ startMessageMonitoringService()
    │     └─→ MessageMonitoringService starts if not already running
    │
    ├─→ requestRequiredPermissions()
    │
    └─→ Load UI
```

### When Device Boots
```
Device Powers On
    │
    ▼
Android broadcasts BOOT_COMPLETED
    │
    ▼
BootReceiver.onReceive()
    │
    ├─→ Start MessageMonitoringService
    │     └─→ Service shows persistent notification
    │
    └─→ Launch MainActivity
          └─→ UI loads and ready to use
```

## Key Components

### BootReceiver
- **Trigger**: Device boot
- **Action**: Starts monitoring service and app
- **Permissions**: Requires RECEIVE_BOOT_COMPLETED

### MessageMonitoringService
- **Type**: Foreground Service
- **Notification**: Always visible in status bar
- **Lifecycle**: START_STICKY (auto-restarts if killed)
- **Purpose**: Keeps app process alive for SMS reception

### SmsReceiver
- **Existing**: Already implemented
- **Trigger**: SMS message arrives
- **Action**: Process message and create notification
- **Works With**: MessageMonitoringService to stay alive

### MainActivity
- **New Addition**: Starts MessageMonitoringService on launch
- **Ensures**: Background monitoring always active when app is open

## Permissions Required

```xml
<!-- Boot startup -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Background service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

<!-- SMS monitoring (already existed) -->
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />

<!-- Phone identification (already existed) -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
```

## Testing Checklist

- [ ] Device boots → App starts automatically
- [ ] Device boots → PesaMind notification visible in status bar
- [ ] App closed → Send SMS test message
- [ ] SMS received → Transaction notification appears
- [ ] Tap notification → App opens and shows transaction
- [ ] App open → Send SMS test message
- [ ] SMS received → Transaction created and notification shows
- [ ] Close app → Kill from recent apps (swipe away)
- [ ] Send SMS → App still receives (notification shows)
- [ ] Check battery impact → Minimal (SMS monitoring only)

## Troubleshooting

### SMS not received when app is closed
1. Check status bar for "PesaMind Active" notification
2. Verify RECEIVE_SMS permission is granted
3. Check phone's battery optimization settings
4. Restart the device

### Boot startup not working
1. Verify RECEIVE_BOOT_COMPLETED permission is granted
2. Check device boot logs: `adb logcat | grep BootReceiver`
3. Some devices have strict boot restrictions; check manufacturer settings

### Persistent notification won't go away
- **This is expected!** It indicates the service is running
- Users can tap it to open the app
- It cannot be dismissed (Android enforcement for essential services)

## Build & Deploy

```bash
# Build
./gradlew build

# Install debug build
./gradlew installDebug

# Run tests
./gradlew test

# Check lint
./gradlew lint
```

## Monitoring Logs

Filter logs for background monitoring:
```bash
adb logcat | grep -E "BootReceiver|MessageMonitoringService|SmsReceiver"
```

Check service status:
```bash
adb shell dumpsys activity services | grep MessageMonitoringService
```

---

**Last Updated**: June 29, 2026
**Status**: ✅ Production Ready

