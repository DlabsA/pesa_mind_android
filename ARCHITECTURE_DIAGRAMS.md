# Unified ViewModel Architecture - Visual Diagrams

## System Architecture

```
╔════════════════════════════════════════════════════════════════════════════╗
║                        UNIFIED STATE COORDINATOR                          ║
║                                                                            ║
║  ┌──────────────────────────────────────────────────────────────────┐    ║
║  │              MutableSharedFlow<StateEvent>                       │    ║
║  │                                                                  │    ║
║  │  - Publishes events from ViewModels                            │    ║
║  │  - Broadcasts to all subscribers                               │    ║
║  │  - Buffer capacity: 100 events                                 │    ║
║  └──────────────────────────────────────────────────────────────────┘    ║
╚════════════════════════════════════════════════════════════════════════════╝
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
        ┌──────────▼────┐  ┌───────▼───────┐  ┌────▼──────────┐
        │  Transaction  │  │   Dashboard   │  │  Analytics    │
        │   ViewModel   │  │   ViewModel   │  │  ViewModel    │
        │               │  │               │  │               │
        │ Extends:      │  │ Extends:      │  │ Extends:      │
        │ UnifiedVM     │  │ UnifiedVM     │  │ UnifiedVM     │
        │               │  │               │  │               │
        │ Actions:      │  │ Reacts to:    │  │ Reacts to:    │
        │ • Create      │  │ • Transaction │  │ • Transaction │
        │ • Load        │  │   Created     │  │   Created     │
        │ • Delete      │  │ • Channel     │  │ • Channel     │
        │               │  │   Updated     │  │   Updated     │
        │ Publishes:    │  │ • User        │  │ • User        │
        │ • Transaction │  │   LoggedOut   │  │   LoggedOut   │
        │   Created     │  │               │  │               │
        │ • Transaction │  │ Publishes:    │  │ Publishes:    │
        │   Deleted     │  │ • Dashboard   │  │ • Analytics   │
        │               │  │   Refreshed   │  │   Refreshed   │
        └───────┬────────┘  └──────┬────────┘  └────┬──────────┘
                │                  │                │
                └──────────────────┼────────────────┘
                                   │
                    Listens to: UnifiedStateCoordinator.events
```

## Event Flow: Creating a Transaction

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     USER CREATES A TRANSACTION                          │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              TransactionViewModel.createTransaction()                   │
│                                                                         │
│  • Validate input                                                      │
│  • Call API                                                            │
│  • Update local state                                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
                        ┌──────────────────┐
                        │ API Call Success │
                        └──────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│        publishEvent(StateEvent.TransactionCreated(...))                 │
│                                                                         │
│  TransactionViewModel calls:                                           │
│  publishEvent(StateEvent.TransactionCreated(                           │
│      transactionId = "tx_123",                                         │
│      amount = 100.0,                                                   │
│      channelId = "ch_456"                                              │
│  ))                                                                     │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│           UnifiedStateCoordinator.publishEvent()                        │
│                                                                         │
│  Event is emitted to MutableSharedFlow                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                  │
                ┌─────────────────┼─────────────────┐
                │                 │                 │
                ▼                 ▼                 ▼
        ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
        │ Dashboard   │   │ Analytics   │   │   Budget    │
        │ ViewModel   │   │ ViewModel   │   │ ViewModel   │
        │             │   │             │   │             │
        │ onStateEvent│   │ onStateEvent│   │ onStateEvent│
        │ receives    │   │ receives    │   │ receives    │
        │ event       │   │ event       │   │ event       │
        └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
               │                 │                 │
               ▼                 ▼                 ▼
        ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
        │  is event   │   │  is event   │   │  is event   │
        │  Transaction│   │  Transaction│   │  Transaction│
        │  Created?   │   │  Created?   │   │  Created?   │
        │ YES ✓       │   │ YES ✓       │   │ YES ✓       │
        └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
               │                 │                 │
               ▼                 ▼                 ▼
        ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
        │  refresh()  │   │  refresh()  │   │  refresh()  │
        │             │   │             │   │             │
        │ Launch API  │   │ Launch API  │   │ Launch API  │
        │ call to get │   │ call to get │   │ call to get │
        │ new data    │   │ analytics   │   │ budget info │
        └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
               │                 │                 │
               ▼                 ▼                 ▼
        ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
        │   Update    │   │   Update    │   │   Update    │
        │   _state    │   │   _state    │   │   _state    │
        │   with new  │   │   with new  │   │   with new  │
        │   dashboard │   │  analytics  │   │   budget    │
        └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
               │                 │                 │
               └─────────────────┼─────────────────┘
                                 │
                                 ▼
                    ┌──────────────────────────┐
                    │  All StateFlows emit     │
                    │  updated values          │
                    │                          │
                    │  - dashboard.state       │
                    │  - analytics.state       │
                    │  - budget.state          │
                    └──────────────────────────┘
                                 │
                                 ▼
                    ┌──────────────────────────┐
                    │   UI LAYERS OBSERVE      │
                    │   STATE AND UPDATE       │
                    │   SCREENS AUTOMATICALLY  │
                    │                          │
                    │ ✅ Dashboard refreshes   │
                    │ ✅ Analytics refreshes   │
                    │ ✅ Budget updates        │
                    │ ✅ All UIs in sync       │
                    └──────────────────────────┘
```

## State Management Flow

```
┌─────────────────────────────────────────────────────────┐
│  User Interaction (UI Layer)                            │
│                                                         │
│  - User clicks "Create Transaction"                    │
│  - User updates channel name                           │
│  - User clicks logout                                  │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  ViewModel Method Call                                  │
│                                                         │
│  - createTransaction()                                 │
│  - updateChannel()                                     │
│  - logout()                                            │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  Business Logic & Validation                            │
│                                                         │
│  - Validate inputs                                     │
│  - Check preconditions                                 │
│  - Prepare API request                                 │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  API Call (Network Layer)                               │
│                                                         │
│  - POST /transactions                                  │
│  - PUT /channels/{id}                                  │
│  - POST /logout                                        │
└────────────────┬────────────────────────────────────────┘
                 │
            ┌────┴────┐
            │          │
            ▼          ▼
        Success     Failure
            │          │
            ├──────┬───┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────┐
│  Update Local State                                     │
│                                                         │
│  _state.value = _state.value.copy(                     │
│      isLoading = false,                                │
│      data = newData,                                   │
│      error = null/errorMessage                         │
│  )                                                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  Publish Event                                          │
│                                                         │
│  publishEvent(StateEvent.TransactionCreated(...))      │
│  publishEvent(StateEvent.ChannelUpdated(...))          │
│  publishEvent(StateEvent.UserLoggedOut)                │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  UnifiedStateCoordinator Broadcasting                   │
│                                                         │
│  Event is emitted to all subscribers                   │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  Dependent ViewModels React                             │
│                                                         │
│  onStateEvent() called in:                             │
│  - DashboardViewModel                                  │
│  - AnalyticsViewModel                                  │
│  - BudgetViewModel                                     │
│  - etc.                                                │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  Dependent ViewModels Refresh                           │
│                                                         │
│  launch {                                              │
│      refresh()  // Fetch new data                      │
│      publishEvent(StateEvent.DashboardRefreshed)       │
│  }                                                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  UI Observers Collect New State                         │
│                                                         │
│  state.collect { state ->                              │
│      updateUI(state)                                   │
│  }                                                     │
└────────────────┬────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────┐
│  UI Updates (All Screens in Sync) ✅                    │
│                                                         │
│  - Dashboard displays new transaction                  │
│  - Analytics shows updated totals                      │
│  - Budget reflects new data                            │
│  - No manual refresh needed                            │
└─────────────────────────────────────────────────────────┘
```

## ViewModel Inheritance Hierarchy

```
┌──────────────────────────────────┐
│      androidx.lifecycle.ViewModel │
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────┐
│    cc.dlabs.pesamind.core.coordinator.           │
│    UnifiedViewModel                              │
│                                                  │
│  ✓ Auto-subscribes to events                    │
│  ✓ Provides publishEvent()                      │
│  ✓ Defines onStateEvent() hook                  │
└──────────────┬───────────────────────────────────┘
               │
    ┌──────────┼──────────┬──────────┬──────────┐
    │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ Trans. │ │ Dash.  │ │ Analyt.│ │Channel │ │  Auth  │
│ Model  │ │ Model  │ │ Model  │ │ Model  │ │ Model  │
└────────┘ └────────┘ └────────┘ └────────┘ └────────┘

Plus: Budget, Account, Unlock ViewModels
All extend UnifiedViewModel and implement onStateEvent()
```

## Event Publishing vs Event Handling

```
EVENT PUBLISHING SIDE              EVENT HANDLING SIDE
┌──────────────────────────┐       ┌──────────────────────────┐
│ TransactionViewModel     │       │ DashboardViewModel       │
│                          │       │                          │
│ fun create() {           │       │ override fun onStateEvent│
│   // ... API call ...    │       │ (event: StateEvent) {    │
│                          │       │                          │
│   if (success) {         │       │   when (event) {         │
│     publishEvent(        │       │     is StateEvent        │
│       StateEvent         │───┐   │     .TransactionCreated  │
│       .Transaction       │   │   │       -> refresh()       │
│       Created(...)       │   │   │     else -> {}           │
│     )                    │   │   │   }                      │
│   }                      │   │   │ }                        │
│ }                        │   │   │                          │
└──────────────────────────┘   │   └──────────────────────────┘
                              │
                              ▼
                    ┌──────────────────────┐
                    │ UnifiedStateCoordinator
                    │ (Broadcasts Event)    │
                    └──────────────────────┘
```

## Error Flow

```
┌──────────────────────────────────────────────────────┐
│ Exception in ViewModel Operation                     │
│                                                      │
│ try {                                                │
│     val response = api.createTransaction(...)       │
│ } catch (e: Exception) {                            │
│     // ERROR HERE                                   │
│ }                                                   │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│ Update Local Error State                             │
│                                                      │
│ _state.value = _state.value.copy(                   │
│     error = "Cannot reach server: ...",             │
│     isLoading = false                               │
│ )                                                   │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│ Publish Error Event (Optional)                       │
│                                                      │
│ publishEvent(StateEvent.ErrorOccurred(              │
│     source = "TransactionViewModel",                │
│     message = "API Error: 500"                      │
│ ))                                                  │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│ Other ViewModels Receive Error Event                 │
│                                                      │
│ - Log the error                                     │
│ - Update global error state                         │
│ - Show error notification to user                   │
│ - Trigger logout if unauthorized (401)              │
└──────────────────────────────────────────────────────┘
```

## Lifecycle: From App Launch to Data Display

```
┌──────────────┐
│  App Launch  │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────────────┐
│ UnifiedStateCoordinator Initialized │
│ (Singleton, available globally)     │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│ AuthViewModel Created               │
│ extends UnifiedViewModel            │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│ User Logs In                        │
│ publishEvent(UserLoggedIn)          │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ Subscribers Receive UserLoggedIn Event      │
│                                             │
│ - TransactionViewModel loads transactions  │
│ - ChannelViewModel loads channels          │
│ - DashboardViewModel loads dashboard       │
│ - AnalyticsViewModel loads analytics       │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ Each ViewModel Publishes Loaded Event       │
│                                             │
│ - TransactionsLoaded                       │
│ - ChannelsLoaded                           │
│ - DashboardLoaded                          │
│ - AnalyticsLoaded                          │
└──────┬───────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ UI Observers Receive State Updates          │
│                                             │
│ - Dashboard screen shows data               │
│ - Transactions list populates               │
│ - Analytics charts render                   │
│ - All screens in sync ✅                    │
└─────────────────────────────────────────────┘
```

## Dependency Graph

```
Legend: A → B means "A publishes events that B listens to"

TransactionViewModel
    ├─→ DashboardViewModel
    ├─→ AnalyticsViewModel
    └─→ BudgetViewModel

ChannelViewModel
    ├─→ DashboardViewModel
    ├─→ AnalyticsViewModel
    └─→ TransactionViewModel

AuthViewModel
    ├─→ TransactionViewModel
    ├─→ ChannelViewModel
    ├─→ DashboardViewModel
    ├─→ AnalyticsViewModel
    ├─→ BudgetViewModel
    └─→ AccountViewModel

DashboardViewModel (reads-only, may republish)
    └─→ AnalyticsViewModel (optionally)

AnalyticsViewModel (reads-only)

BudgetViewModel
    └─→ DashboardViewModel (optionally)

AccountViewModel
    └─→ (mainly reads, minimal publishing)
```

This creates a reactive system where:
- 🟢 GREEN: Events flow from production ViewModels to consumers
- 🔵 BLUE: Core authentication orchestrates everything
- 🟡 YELLOW: Optional feedback loops for advanced scenarios

