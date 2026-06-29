# Unified ViewModel System - File Index & Navigation

## 📚 Complete File List

### Framework Files (7 files)

```
📁 app/src/main/java/cc/dlabs/pesamind/core/coordinator/
│
├── 📄 StateEvent.kt
│   Size: ~150 lines | ~4.5 KB
│   Purpose: Define all state change events
│   Key Content: Sealed class hierarchy with all event types
│   Use When: Adding new state change types to your app
│
├── 📄 UnifiedStateCoordinator.kt
│   Size: ~50 lines | ~1.8 KB
│   Purpose: Central event bus singleton
│   Key Content: MutableSharedFlow for broadcasting events
│   Use When: Understanding how events are published/broadcast
│
├── 📄 UnifiedViewModel.kt
│   Size: ~60 lines | ~2.0 KB
│   Purpose: Base class for all ViewModels
│   Key Content: Auto-subscription setup and publishEvent() method
│   Use When: Creating new ViewModels or understanding base behavior
│
├── 📄 ViewModelDependencyRegistry.kt
│   Size: ~50 lines | ~1.5 KB
│   Purpose: Event filtering and dependency utilities
│   Key Content: Type-safe filtering extensions
│   Use When: Setting up complex event dependencies
│
├── 📄 UnifiedStateExtensions.kt
│   Size: ~250 lines | ~8.5 KB
│   Purpose: Rich extension functions and helpers
│   Key Content: 15+ extension functions for events and ViewModels
│   Use When: Need convenient functions for event handling
│
├── 📄 UnifiedStateCoordinatorModule.kt
│   Size: ~40 lines | ~1.5 KB
│   Purpose: Hilt DI module for production builds
│   Key Content: Singleton bindings for Hilt
│   Use When: Building with dependency injection
│
└── 📄 IntegrationExamples.kt
    Size: ~300 lines | ~10 KB
    Purpose: Reference implementations and examples
    Key Content: 5 example ViewModel implementations
    Use When: Learning how to implement integration
```

### Documentation Files (6 files)

```
📁 pesa_mind_android/
│
├── 📘 UNIFIED_VIEWMODEL_GUIDE.md
│   Size: ~800 lines | ~35 KB
│   Read Time: 60 minutes
│   Level: Intermediate to Advanced
│   Purpose: Complete architectural guide and usage manual
│   Contents:
│   ├─ Overview (5 min)
│   ├─ Key Components (10 min)
│   ├─ Architecture Pattern (10 min)
│   ├─ Usage Guide (20 min)
│   ├─ Common Patterns (15 min)
│   └─ Advanced Topics (20 min)
│   Use When: Need deep understanding of the system
│
├── 📗 UNIFIED_VIEWMODEL_BEST_PRACTICES.md
│   Size: ~600 lines | ~28 KB
│   Read Time: 45 minutes
│   Level: Advanced
│   Purpose: Patterns, best practices, and anti-patterns
│   Contents:
│   ├─ Core Principles (5 min)
│   ├─ 8 Common Patterns (30 min)
│   ├─ Anti-Patterns & Fixes (10 min)
│   ├─ Testing Strategies (15 min)
│   └─ Performance Tips (10 min)
│   Use When: Building production features
│
├── 📕 QUICK_START_IMPLEMENTATION.md
│   Size: ~400 lines | ~18 KB
│   Read Time: 30 minutes
│   Level: Beginner to Intermediate
│   Purpose: Step-by-step implementation for each ViewModel
│   Contents:
│   ├─ Prerequisites (5 min)
│   ├─ 8 Step-by-Step Guides (20 min)
│   ├─ Verification Checklist (5 min)
│   └─ Testing & Summary (5 min)
│   Use When: Actually implementing the system
│
├── 📊 ARCHITECTURE_DIAGRAMS.md
│   Size: ~350 lines | ~15 KB
│   Read Time: 20 minutes
│   Level: Visual Learner
│   Purpose: ASCII diagrams of architecture and flows
│   Contents:
│   ├─ System Architecture (5 min)
│   ├─ Event Flow Examples (5 min)
│   ├─ State Management Flow (5 min)
│   ├─ Lifecycle Diagram (3 min)
│   └─ Dependency Graph (2 min)
│   Use When: Visual understanding needed
│
├── 📋 IMPLEMENTATION_SUMMARY.md
│   Size: ~300 lines | ~14 KB
│   Read Time: 20 minutes
│   Level: Quick Reference
│   Purpose: Complete summary and quick reference
│   Contents:
│   ├─ Overview (5 min)
│   ├─ Files & Quick Start (5 min)
│   ├─ Event Taxonomy (3 min)
│   ├─ Patterns & Checklist (5 min)
│   └─ Troubleshooting (2 min)
│   Use When: Need quick reference or overview
│
└── 📖 COMPLETE_FILE_STRUCTURE.md
    Size: ~250 lines | ~12 KB
    Read Time: 15 minutes
    Level: Reference
    Purpose: File structure and statistics
    Contents:
    ├─ File Descriptions (10 min)
    ├─ Statistics (3 min)
    └─ Navigation Map (2 min)
    Use When: Need to find specific files
```

---

## 🗺️ Navigation Guide

### "I want to understand the system"
```
1. Start here: IMPLEMENTATION_SUMMARY.md (5 min read)
2. Then: ARCHITECTURE_DIAGRAMS.md (10 min read)
3. Finally: UNIFIED_VIEWMODEL_GUIDE.md (full read)
```

### "I want to implement it now"
```
1. Start here: QUICK_START_IMPLEMENTATION.md
2. Follow step-by-step for each ViewModel
3. Use IntegrationExamples.kt for reference
4. Check Verification Checklist when done
```

### "I want to see examples"
```
1. Read: IntegrationExamples.kt (in code)
2. Read: QUICK_START_IMPLEMENTATION.md (in docs)
3. Read: UNIFIED_VIEWMODEL_BEST_PRACTICES.md (patterns section)
```

### "I want to learn best practices"
```
1. Read: UNIFIED_VIEWMODEL_BEST_PRACTICES.md
2. Study: Common Patterns section (8 patterns)
3. Review: Anti-Patterns section
4. Practice: Testing Strategies section
```

### "I need quick reference"
```
1. Check: IMPLEMENTATION_SUMMARY.md (Event taxonomy)
2. Check: COMPLETE_FILE_STRUCTURE.md (file descriptions)
3. Check: ARCHITECTURE_DIAGRAMS.md (flow diagrams)
```

### "I'm stuck or have questions"
```
1. Check: IMPLEMENTATION_SUMMARY.md (Troubleshooting section)
2. Check: UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Common pitfalls)
3. Check: QUICK_START_IMPLEMENTATION.md (Verification checklist)
```

---

## 📖 Reading Levels

### 🟢 Beginner (15 minutes)
1. IMPLEMENTATION_SUMMARY.md - Overview
2. QUICK_START_IMPLEMENTATION.md - Step 1 (TransactionViewModel)

### 🟡 Intermediate (45 minutes)
1. ARCHITECTURE_DIAGRAMS.md - All diagrams
2. QUICK_START_IMPLEMENTATION.md - All steps
3. IntegrationExamples.kt - Code examples

### 🔴 Advanced (2 hours)
1. UNIFIED_VIEWMODEL_GUIDE.md - Complete
2. UNIFIED_VIEWMODEL_BEST_PRACTICES.md - All patterns
3. Framework code - All 7 files

### 📚 Expert (3 hours)
- Deep dive into all documentation
- Implement in your project
- Customize for your needs
- Add custom event types

---

## 🎯 Task-Based Navigation

### Task: Add a new event type
```
→ StateEvent.kt (add sealed class)
→ UNIFIED_VIEWMODEL_GUIDE.md (Advanced section)
```

### Task: Make ViewModel auto-refresh
```
→ QUICK_START_IMPLEMENTATION.md (specific ViewModel step)
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Pattern 1: Auto-Refresh)
```

### Task: Handle user logout
```
→ QUICK_START_IMPLEMENTATION.md (Auth section)
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Pattern 3: Cascade initialization)
```

### Task: Prevent event loops
```
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Anti-pattern 1)
→ IntegrationExamples.kt (review patterns)
```

### Task: Optimize performance
```
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Pattern 5: Throttling)
→ UNIFIED_VIEWMODEL_GUIDE.md (Performance section)
```

### Task: Write unit tests
```
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Testing strategies)
→ UNIFIED_VIEWMODEL_GUIDE.md (Testing section)
```

### Task: Debug event flow
```
→ UnifiedStateExtensions.kt (logEvents function)
→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Debugging section)
```

### Task: Set up production build
```
→ UnifiedStateCoordinatorModule.kt (Hilt setup)
→ UNIFIED_VIEWMODEL_GUIDE.md (Hilt integration section)
```

---

## 📊 File Dependencies

```
Framework Dependencies:
StateEvent.kt ← Base classes for all events
    ↓
UnifiedStateCoordinator.kt ← Uses StateEvent
    ↓
UnifiedViewModel.kt ← Uses Coordinator
    ↓
ViewModelDependencyRegistry.kt ← Utilities for ViewModel
    ↓
UnifiedStateExtensions.kt ← Extensions for all above

Documentation Dependencies:
IMPLEMENTATION_SUMMARY.md ← Start here
    ├→ QUICK_START_IMPLEMENTATION.md ← Then this
    ├→ ARCHITECTURE_DIAGRAMS.md ← Visual reference
    ├→ UNIFIED_VIEWMODEL_GUIDE.md ← Deep dive
    └→ UNIFIED_VIEWMODEL_BEST_PRACTICES.md ← Advanced

Code Dependencies:
IntegrationExamples.kt ← Reference implementations
    ↓ Uses
    ↓
All framework files (1-7)
```

---

## 🔍 File Content Quick Reference

### StateEvent.kt
```
Contains these event groups:
├─ Transaction Events (3)
├─ Channel Events (5)
├─ Dashboard Events (2)
├─ Analytics Events (2)
├─ Budget Events (2)
├─ Auth Events (2)
├─ Account Events (1)
└─ Generic Events (2)
Total: 19 event types
```

### UnifiedStateCoordinator.kt
```
Key components:
├─ _events: MutableSharedFlow<StateEvent>
├─ events: Flow<StateEvent> (public)
├─ publishEvent(event): suspend function
└─ publishEventNonSuspending(event): non-suspend variant
```

### UnifiedViewModel.kt
```
Key components:
├─ init block: auto-subscribe to events
├─ onStateEvent(event): override hook
└─ publishEvent(event): helper method
```

### UnifiedStateExtensions.kt
```
Key extension functions:
├─ filterEventType<T>()
├─ filterEvents(predicate)
├─ filterEventTypes(vararg)
├─ subscribeToEvent<T>()
├─ publishEvent(event)
├─ waitForEvent<T>()
└─ logEvents()
```

### IntegrationExamples.kt
```
Contains examples for:
├─ TransactionViewModel
├─ DashboardViewModel
├─ ChannelViewModel
├─ AnalyticsViewModel
└─ AuthViewModel
```

---

## ⏱️ Time Estimates

| Activity | Time | Files |
|----------|------|-------|
| Read overview | 10 min | IMPLEMENTATION_SUMMARY.md |
| Understand architecture | 30 min | + ARCHITECTURE_DIAGRAMS.md |
| Read examples | 20 min | + IntegrationExamples.kt |
| Implement first ViewModel | 30 min | + QUICK_START_IMPLEMENTATION.md |
| Implement remaining (7) | 90 min | + All 7 ViewModels |
| Testing & optimization | 30 min | + Test files |
| **Total** | **4 hours** | **All files** |

---

## 📝 Print-Friendly Files

Best files to print for reference:

1. **Quick Reference Card** (1 page)
   → IMPLEMENTATION_SUMMARY.md (Event taxonomy section)

2. **Implementation Guide** (4 pages)
   → QUICK_START_IMPLEMENTATION.md (First half)

3. **Pattern Reference** (5 pages)
   → UNIFIED_VIEWMODEL_BEST_PRACTICES.md (Patterns section)

4. **Architecture** (2 pages)
   → ARCHITECTURE_DIAGRAMS.md (Main diagrams)

---

## 💾 File Access

All files are in the repository:

**Framework Code**:
```
app/src/main/java/cc/dlabs/pesamind/core/coordinator/
├── StateEvent.kt
├── UnifiedStateCoordinator.kt
├── UnifiedViewModel.kt
├── ViewModelDependencyRegistry.kt
├── UnifiedStateExtensions.kt
├── UnifiedStateCoordinatorModule.kt
└── IntegrationExamples.kt
```

**Documentation**:
```
pesa_mind_android/
├── UNIFIED_VIEWMODEL_GUIDE.md
├── UNIFIED_VIEWMODEL_BEST_PRACTICES.md
├── QUICK_START_IMPLEMENTATION.md
├── ARCHITECTURE_DIAGRAMS.md
├── IMPLEMENTATION_SUMMARY.md
└── COMPLETE_FILE_STRUCTURE.md
```

---

## 🎓 Learning Paths

### Path A: Visual Learner
1. ARCHITECTURE_DIAGRAMS.md (15 min)
2. IntegrationExamples.kt (20 min)
3. QUICK_START_IMPLEMENTATION.md (30 min)
**Total**: 65 minutes

### Path B: Hands-On Learner
1. IMPLEMENTATION_SUMMARY.md (10 min)
2. QUICK_START_IMPLEMENTATION.md (60 min)
3. Implement first ViewModel (30 min)
**Total**: 100 minutes

### Path C: Theory-First Learner
1. UNIFIED_VIEWMODEL_GUIDE.md (60 min)
2. ARCHITECTURE_DIAGRAMS.md (20 min)
3. QUICK_START_IMPLEMENTATION.md (40 min)
**Total**: 120 minutes

### Path D: Deep Dive
1. All documentation (180 min)
2. All code files (60 min)
3. Implement complete system (120 min)
**Total**: 360 minutes (6 hours)

---

## 🚀 Next Steps

1. **Choose your path** above based on learning style
2. **Start with** appropriate first file
3. **Follow the navigation** guides
4. **Implement** using QUICK_START_IMPLEMENTATION.md
5. **Test** using provided examples
6. **Reference** UNIFIED_VIEWMODEL_BEST_PRACTICES.md as needed

---

## 📞 Quick FAQ

**Q: Where do I start?**
A: Read IMPLEMENTATION_SUMMARY.md (10 min)

**Q: How do I implement this?**
A: Follow QUICK_START_IMPLEMENTATION.md

**Q: What are best practices?**
A: See UNIFIED_VIEWMODEL_BEST_PRACTICES.md

**Q: I need visual explanation**
A: Check ARCHITECTURE_DIAGRAMS.md

**Q: Where are the code files?**
A: In `app/src/main/java/cc/dlabs/pesamind/core/coordinator/`

**Q: Do I need all 7 framework files?**
A: Yes, they work together as one system

**Q: Can I use this in production?**
A: Yes, it includes Hilt integration

**Q: How long to implement?**
A: 4 hours for complete system

---

**Ready to get started?** 🚀
Pick your reading path above and begin!

