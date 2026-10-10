# TripMate 🌍
### AI-Powered Group Travel & Tourist Safety Platform
**Layover 2026 Hackathon — Travel & Tourism Track**

TripMate is an intelligent, offline-capable mobile companion for tourists and tour guides. Built for group travel, it bridges the gap between travelers and tour operators through interactive real-time itineraries, proactive belonging verification, contextual AI travel assistance, and immediate emergency SOS dispatch.

---

## 🚀 Key Features

### 1. 👥 Dual-Role Architecture (Tourist & Guide Modes)
- **Role Switching**: Instantly switch between Tourist and Tour Guide perspectives via the top app bar for seamless hackathon demonstrations.
- **Synchronized State**: State changes made by the guide (itinerary changes, published trips) reflect across the shared reactive store.

### 2. 📍 Interactive Chronological Itinerary
- **Live Destination Status**: Real-time progress tracking across trip stops (e.g., *Current*, *Next*, *Upcoming*, *Completed*).
- **Activity & Precaution Guides**: Curated activity checklists, dress codes, recommended durations, and safety guidelines per stop.
- **Check-in System**: Travelers confirm their arrival at each stop, updating tour guide headcount records in real time.

### 3. 🎒 Smart Packing & Belongings Departure Checklist
- **Pre-Trip Packing List**: Categorized checklists (Documents, Essentials, Clothing, Tech, Health) with item completion stats.
- **Zero-Lost Belongings Reminder**: At every destination departure, TripMate prompts tourists to verify high-risk items (passport, wallet, camera, sunglasses, phone charger) before moving to the next stop.
- **Missing Item Reporting**: One-tap reporting alerts the tour guide immediately if an item is left behind at a venue.

### 4. 🤖 Context-Aware AI Travel Assistant
- **Offline-First & Deterministic**: Fast, zero-hallucination local assistant with rich knowledge of the current tour and destinations.
- **Contextual Suggestions**: One-tap query chips for weather precautions, local dress codes, upcoming schedules, emergency contacts, and tipping etiquette.
- **Conversational Queries**: Interactive Q&A covering local culture, logistics, and timing.

### 5. 🆘 Emergency SOS & Safety Dispatch
- **One-Tap Emergency Alert**: Instant distress beacon with real-time GPS coordinates and timestamping.
- **Direct Guide Link**: Immediate notification to tour leaders with the traveler's status and last known location.
- **Emergency Hotlines**: Direct one-tap access to local police, ambulance, and tourist police services.

### 6. 🧭 Tour Guide Command Center
- **Roster & Attendance Tracking**: Live count of checked-in vs overdue travelers at each destination.
- **Emergency Triage**: High-priority incident feed displaying active traveler SOS alerts with resolve actions.
- **Itinerary Publishing**: Dedicated interface for tour guides to design and broadcast new multi-day itineraries.

---

## 🏗️ Project Architecture

TripMate is built with modern Android native architecture:
- **UI Framework**: Jetpack Compose + Material Design 3
- **Language**: Kotlin 2.2
- **State Management**: Reactive `StateFlow` backed by `TripMateRepository`
- **Persistence**: Local offline-first JSON storage via `SharedPreferences`
- **Location Services**: Google Play Services Fused Location Provider
- **Minimum Android SDK**: API 26 (Android 8.0 Oreo)
- **Target Android SDK**: API 35 (Android 15)

```
app/src/main/java/com/railguard/mobile/
├── tripmate/
│   ├── ai/
│   │   └── TripMateAiAssistant.kt       # Local context-aware travel assistant
│   ├── data/
│   │   ├── SampleData.kt                # Curated demonstration tour datasets
│   │   └── TripMateRepository.kt        # Reactive state repository & persistence
│   ├── models/
│   │   └── TripMateModels.kt            # Data schemas (Trip, Destination, SOS, etc.)
│   └── ui/
│       ├── TripMateApp.kt               # Main scaffold, navigation bar & role switcher
│       ├── tourist/
│       │   ├── TouristHomeScreen.kt     # Tourist dashboard & quick actions
│       │   ├── ItineraryScreen.kt       # Step-by-step timeline & check-in flow
│       │   ├── DestinationDetailScreen.kt# Stop overview, activities & precautions
│       │   ├── BelongingsReminderDialog.kt# Departure checklist & missing item alerts
│       │   ├── PackingChecklistScreen.kt# Categorized preparation checklists
│       │   ├── AiAssistantScreen.kt     # Interactive travel concierge chat
│       │   └── SosScreen.kt             # Emergency assistance & GPS coordinates
│       └── guide/
│           ├── GuideDashboardScreen.kt  # Attendance tracking, alerts & traveler status
│           └── TripCreateEditScreen.kt  # Itinerary builder & publisher
└── MainActivity.kt                      # Application entry point
```

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17 or JDK 21 (`JAVA_HOME` configured)
- Android SDK (API 35+ with Build Tools 35.0.0+)

### Build Debug APK
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install onto Connected Android Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.railguard.mobile/.MainActivity
```
