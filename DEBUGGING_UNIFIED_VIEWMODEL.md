# Unified ViewModel Debugging Guide

## Issue: Dashboard and Analytics Not Auto-Updating After Transaction Creation

The unified ViewModel system has been enhanced with comprehensive logging. Use this guide to debug why screens don't update automatically.

## How to Debug

### Step 1: Filter Logs by Tags
In Android Studio Logcat, filter by these tags to see only ViewModel events:
- `TransactionViewModel` - Transaction operations
- `DashboardViewModel` - Dashboard updates
- `AnalyticsViewModel` - Analytics updates
- `UnifiedViewModel` - Base class events
- `UnifiedStateCoordinator` - Event bus

### Step 2: Create a Transaction and Watch Logs

Run the app and create a transaction. You should see logs like:

```
📝 TransactionViewModel: Creating transaction: amount=20000, channelID=xxx, type=expense
✅ TransactionViewModel: Transaction created successfully: 671f5c60-3b8a-411d-9fef-5e7b2bea7625
📢 TransactionViewModel: Publishing TransactionCreated event...
✅ TransactionViewModel: Event published successfully
```

### Step 3: Check if Event is Received

Then look for:

```
🔵 DashboardViewModel: DashboardViewModel received event: TransactionCreated
🎯 DashboardViewModel: DashboardViewModel received event: TransactionCreated
🔄 DashboardViewModel: TransactionCreated received! Refreshing dashboard...
📍 DashboardViewModel: refresh() called. Current isRefreshing: false
🟢 DashboardViewModel: Starting refresh...
🌐 DashboardViewModel: Connected, fetching from network...
✅ DashboardViewModel: Network fetch completed
✨ DashboardViewModel: Refresh completed
```

If you see all these logs, the system is working perfectly!

### Step 4: Common Issues and What to Look For

#### Issue 1: Event Not Being Published
**Look for**: Missing `📢 Publishing TransactionCreated event...` log

**Solution**:
- Check if transaction creation actually succeeded (look for `✅ Transaction created successfully`)
- Verify the `publishEvent()` call isn't being skipped by exception handling

#### Issue 2: Event Not Being Received
**Look for**: Missing `🔵 DashboardViewModel received event` log

**Probable causes**:
1. **DashboardViewModel doesn't exist yet** - It wasn't created/subscribed before the event was published
   - Solution: Open Dashboard screen BEFORE creating transaction
   
2. **Event published before subscriber was ready** - Timing issue in initialization
   - Solution: Wait a few seconds after app startup before creating transaction

3. **Multiple ViewModel instances** - Hilt might be creating different instances
   - Solution: Check that @HiltViewModel is used and Hilt is properly configured

#### Issue 3: Event Received But Refresh Called with Already Refreshing = true
**Look for**: `⚠️ refresh() called but already refreshing, ignoring...`

**Cause**: Dashboard was already refreshing when the event arrived

**Solution**: This is a safety mechanism. The current refresh will finish and include the new data.

#### Issue 4: refresh() Called But No Network Request
**Look for**: `🟢 Starting refresh...` but no `🌐 Connected, fetching from network...`

**Probable causes**:
1. Network is offline - Check `📡 Offline` log
2. ViewModelScope is destroyed - Shouldn't happen if ViewModel still exists

## Complete Log Flow (Expected)

Here's what a successful sequence looks like:

```
=== USER CREATES TRANSACTION ===
📝 TransactionViewModel: Creating transaction: amount=20000, channelID=ac68c0a4-b688-4c32-8355-9853074794b6, type=expense

=== API REQUEST ===
✓ Token added to request for https://api.dlabs.cc/api/v1/transactions
--> POST https://api.dlabs.cc/api/v1/transactions
<-- 201 https://api.dlabs.cc/api/v1/transactions (293ms)

=== TRANSACTION CREATED ===
✅ TransactionViewModel: Transaction created successfully: 671f5c60-3b8a-411d-9fef-5e7b2bea7625
📢 TransactionViewModel: Publishing TransactionCreated event...
✅ TransactionViewModel: Event published successfully

=== DASHBOARD RECEIVES EVENT ===
🔵 DashboardViewModel: DashboardViewModel received event: TransactionCreated
🎯 DashboardViewModel: DashboardViewModel received event: TransactionCreated
🔄 DashboardViewModel: TransactionCreated received! Refreshing dashboard...
📍 DashboardViewModel: refresh() called. Current isRefreshing: false
🟢 DashboardViewModel: Starting refresh...
🌐 DashboardViewModel: Connected, fetching from network...

=== DASHBOARD API REQUEST ===
✓ Token added to request for https://api.dlabs.cc/api/v1/data/dashboard
--> GET https://api.dlabs.cc/api/v1/data/dashboard
<-- 200 https://api.dlabs.cc/api/v1/data/dashboard (263ms)

=== DASHBOARD UPDATED ===
✅ DashboardViewModel: Network fetch completed
✨ DashboardViewModel: Refresh completed

=== ANALYTICS RECEIVES EVENT ===
🔵 AnalyticsViewModel: AnalyticsViewModel received event: TransactionCreated
🎯 AnalyticsViewModel: AnalyticsViewModel received event: TransactionCreated
🔄 AnalyticsViewModel: TransactionCreated received! Refreshing analytics...
📍 AnalyticsViewModel: refresh() called. Current isRefreshing: false
🟢 AnalyticsViewModel: Starting refresh...
🌐 AnalyticsViewModel: Fetching from network...

=== ANALYTICS API REQUEST ===
✓ Token added to request for https://api.dlabs.cc/api/v1/data/analytics
--> GET https://api.dlabs.cc/api/v1/data/analytics
<-- 200 https://api.dlabs.cc/api/v1/data/analytics (263ms)

=== ANALYTICS UPDATED ===
✅ AnalyticsViewModel: Network fetch completed
✨ AnalyticsViewModel: Refresh completed
```

## Checking Logs in Android Studio

1. Open Android Studio Terminal or Logcat
2. Create filter: `tag:TransactionViewModel|tag:DashboardViewModel|tag:AnalyticsViewModel`
3. Create transaction
4. Observe the complete flow

## If You Don't See the Events

Try these steps:

1. **Make sure both screens are visible/created**:
   - Open Dashboard screen
   - Open Analytics screen  
   - Then go back and create transaction

2. **Add manual event publishing**:
   ```kotlin
   // In your Transaction creation callback, add this line AFTER successful API response:
   UnifiedStateCoordinator.publishEventNonSuspending(StateEvent.TransactionCreated(...))
   ```

3. **Check Hilt configuration**:
   - Ensure @HiltViewModel is on all ViewModels
   - Ensure UnifiedViewModel is in coordinator package
   - Rebuild project: Build > Clean Project > Rebuild Project

4. **Restart the app completely**:
   - Force stop app
   - Clear app cache
   - Restart

## Performance Tip

The first successful update might take 5-10 seconds due to:
1. Event publishing (< 1ms)
2. Event delivery to subscribers (< 5ms)
3. Coroutine scheduling (< 100ms)
4. Network request (~300-500ms)
5. Response parsing and state update (< 100ms)

## Success Indicator

You'll know it's working when:
- ✅ You create a transaction
- ✅ Dashboard refreshes automatically (within 1-2 seconds)
- ✅ Analytics refreshes automatically (within 1-2 seconds)
- ✅ No manual refresh needed
- ✅ Logs show the complete flow as shown above

## Next Steps if Still Not Working

1. Share the complete log output (from transaction creation to 10 seconds after)
2. Note which screen was visible when you created the transaction
3. Check if Dashboard/Analytics had loaded data before you created transaction
4. Verify network connection is working (other API calls succeed)

---

**Debug Output Format**: When reporting issues, include logs filtered by:
- `TransactionViewModel`
- `DashboardViewModel`  
- `AnalyticsViewModel`
- `UnifiedStateCoordinator`

From the exact moment you create the transaction through 10 seconds after.

