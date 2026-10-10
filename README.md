# Roamly 🌿
### AI-Powered Personal Travel Companion
**Layover 2026 Hackathon — Travel & Tourism Track**

Repository: [https://github.com/joseph489/Roamly](https://github.com/joseph489/Roamly)

Roamly is a calm, aesthetic, and intelligent mobile travel companion designed for solo travellers, couples, families, and groups of friends. From pre-trip packing and document checklists to live day-by-day itineraries, smart belongings reminders at every departure, and an offline-first contextual AI assistant, Roamly supports the complete travel journey.

---

## 🎨 Visual Design: Light, Aesthetic & Comfortable
- **Light Theme by Default**: Warm soft ivory background (`#FAF9F6`) with pure white card surfaces (`#FFFFFF`).
- **Natural Calming Accents**: Deep soothing sage (`#477568`), soft mint containers (`#DCEBE4`), and warm sand ochre accents (`#E7B98A`).
- **High Readability**: Dark charcoal typography (`#28332F`) with muted green-gray supporting metadata (`#748078`).
- **Restrained Radii & Generous Spacing**: Elegant cards, zero clutter, comfortable touch targets, and subtle elevation.

---

## 🚀 Key Feature Highlights

### 1. 🏡 Welcoming Travel Home Screen
- Personalized traveler greeting (`"Good day, Alex & Maya 🌿"`) with local weather indicator (`"Singapore 31°C"`).
- Hero Trip Banner for the active/upcoming journey with duration, traveler count, and direct itinerary shortcuts.
- Real-time pre-trip packing progress bar.
- Quick 1-tap prompts for the AI Companion.
- Pre-departure travel tips (SG Arrival Card, Type-G UK plug, tap water advisory).

### 2. 🗺️ My Trips & 5-Day Singapore Itinerary
- Supports **Upcoming**, **Active**, and **Completed** journeys.
- Curated 5-Day Singapore demonstration trip across iconic neighborhoods:
  - **Day 1**: Marina Bay Sands SkyPark, Gardens by the Bay & Cloud Forest, Lau Pa Sat Satay Street.
  - **Day 2**: Buddha Tooth Relic Temple (Chinatown), Kampong Glam, Haji Lane murals.
  - **Day 3**: National Gallery Singapore, Fort Canning Park, Clarke Quay Bumboat River Cruise.
  - **Day 4**: S.E.A. Aquarium Sentosa, Palawan Beach & Continental Asia Islet.
  - **Day 5**: Singapore Botanic Gardens (UNESCO), Jewel Changi HSBC Rain Vortex.
- Interactive destination cards with activity checkoffs, suggested durations, practical instructions, and cultural precautions.
- Overall progress tracking (visited stops vs total planned).

### 3. 🤖 Context-Aware AI Travel Companion
- **Roamly Context Engine (Offline-First)**: Fast, deterministic, zero-hallucination assistant with local Singapore travel knowledge.
- Contextual answers for queries such as:
  - *"What should I explore near Marina Bay?"*
  - *"What can I do with three hours free?"*
  - *"Explain the history of Singapore simply"*
  - *"What activities are planned for today?"*
  - *"What should I pack for my trip?"*
  - *"What precautions should I know about this activity?"*
  - *"Can you help reorganise my remaining itinerary?"*
- One-tap suggestion chips for rapid mobile queries.

### 4. 🎒 Persistent Packing & Belongings Departure Workflow
- **Categorized Packing List**: Documents, Daily Essentials, Clothing, Tech & Gear, Health & Toiletries.
- Add and delete custom items; check/uncheck status persists across app restarts via `SharedPreferences`.
- **Zero-Lost Belongings Guarantee**:
  - Whenever departing a stop, Roamly prompts a quick pocket and bag check.
  - **Non-Destructive**: Belongings state is **never** automatically cleared or reset upon departure.
- **Official Immigration & Document Guidance**:
  - Details on the electronic SG Arrival Card (SGAC), 6-month passport validity rules, and clear reminders to verify with official authorities (ICA / MFA).

### 5. 👤 Traveller Profile & Settings
- Travel party overview (Alex Chen & Maya Lin • Couple Travel).
- Travel style badges (*Culture & Heritage*, *Hawker Foodie*, *Botanic Greenery*).
- Emergency hotlines (Police 999, Ambulance 995, Tourist Hotline 1800 736 2000).
- One-tap demonstration reset.

---

## 🏗️ Technical Stack & Architecture

- **UI Framework**: Native Android Jetpack Compose + Material 3
- **Language**: Kotlin 2.2
- **Namespace & Application ID**: `com.roamly.app`
- **Architecture**: Single Activity (`MainActivity`), reactive `StateFlow`, singleton `RoamlyRepository`
- **Local Persistence**: `SharedPreferences` with JSON serialization via Gson
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 (Android 15)
- **Compile SDK**: API 36

```
app/src/main/java/com/roamly/app/
├── MainActivity.kt                      # Application entry point
├── ai/
│   └── RoamlyAiCompanion.kt             # Offline-first travel assistant engine
├── data/
│   ├── RoamlyRepository.kt              # Reactive StateFlow repository & persistence
│   └── SampleData.kt                    # Curated 5-day Singapore journey dataset
├── model/
│   └── RoamlyModels.kt                  # Trip, Destination, Checklist & Message schemas
└── ui/
    ├── RoamlyMainApp.kt                 # Main scaffold container & bottom navigation
    ├── components/
    │   └── BelongingsReminderDialog.kt  # Non-destructive departure belongings check
    ├── screens/
    │   ├── HomeScreen.kt                # Travel companion home dashboard
    │   ├── MyTripsScreen.kt             # 5-Day itinerary timeline & activities
    │   ├── AiCompanionScreen.kt         # Travel Q&A chat with quick suggestion chips
    │   ├── ChecklistsScreen.kt          # Packing checklist & document guides
    │   └── ProfileScreen.kt             # Profile, emergency contacts & reset
    └── theme/
        ├── Color.kt                     # Light aesthetic color tokens (#FAF9F6, #477568)
        ├── Theme.kt                     # RoamlyTheme Material 3 light theme
        └── Type.kt                      # Clean typography tokens
```

---

## 📲 Building & Installing

### Build Debug APK
```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew assembleDebug
```
The compiled APK is located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests
```bash
./gradlew test
```
Runs 14 automated unit tests covering both the travel data models and the authentication system (`AuthSystemTest`).

### Install onto Connected Android Phone
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.roamly.app/.MainActivity
```

---

## 🔐 Complete Authentication & Supabase Backend Setup

Roamly integrates a production-grade authentication and authorization system powered by **Supabase Auth**, **PostgreSQL**, and **Row Level Security (RLS)**, offering distinct, role-tailored experiences for both **Travellers** and **Tour Guides**.

### 1. Dual Persona Overview
- **Traveller Portal (`TRAVELLER`)**:
  - Sign in or sign up via email & password or Google OAuth.
  - Automatically receives `approved` status.
  - Grants instant access to the personal 5-day Singapore itinerary, packing checklists, offline travel assistant, and belongings check.
- **Guide Portal (`GUIDE`)**:
  - Sign in or request access via email & password or Google OAuth.
  - Starts in `pending` approval status.
  - Cannot access guide dashboards, group rosters, or emergency medical info until explicitly verified and approved by an administrator.

---

### 2. Supabase Project Configuration

1. **Create a Supabase Project**:
   - Go to [Supabase](https://supabase.com) and create a new project.
   - Choose a region close to your target audience (e.g., `Singapore (ap-southeast-1)`).
2. **Retrieve API Credentials**:
   - In your Supabase dashboard, go to **Project Settings** → **API**.
   - Copy the **Project URL** (`https://<project-ref>.supabase.co`).
   - Copy the **`anon` `public` API Key**.
   - ⚠️ **SECURITY WARNING**: Never put the `service_role` secret key into Android source code or `build.gradle`. Roamly only requires the client `anon` key.
3. **Apply Database Migrations**:
   - In Supabase Dashboard, open **SQL Editor**.
   - Execute the contents of [`supabase/migrations/20261011000001_auth_profiles_schema.sql`](file:///home/buddy/Roamly%20/supabase/migrations/20261011000001_auth_profiles_schema.sql).
   - This sets up:
     - `profiles`: Linked to `auth.users(id)` with strict role and approval status constraints.
     - `trips`: Seeded with the canonical 5-day Singapore Explorer (`trip_singapore_2026`).
     - `trip_members`, `check_ins`, `announcements`.
     - `handle_new_user()` trigger: Automatically creates user profile on auth signup.
     - `prevent_role_escalation()` trigger: Forbids non-admin users from altering their role or self-approving.
     - Row Level Security (RLS) policies on all tables.
4. **Configure Auth Providers**:
   - In Supabase Dashboard, navigate to **Authentication** → **Providers**.
   - **Email Provider**: Enable Email signup. Toggle "Confirm email" based on your development or production policy.
   - **Password Recovery**: Set Site URL or redirect URI to `com.roamly.app://auth/callback` or your web portal.

---

### 3. Google OAuth Configuration (Credential Manager)

1. **Google Cloud Console**:
   - Navigate to [Google Cloud Console](https://console.cloud.google.com).
   - Create or select your project.
   - In **APIs & Services** → **OAuth consent screen**, configure consent screen details.
   - In **Credentials** → **Create Credentials** → **OAuth client ID**:
     - Create a **Web Application** client ID (this will be your `GOOGLE_WEB_CLIENT_ID` used by Android Credential Manager and Supabase).
     - Create an **Android** client ID:
       - Package name: `com.roamly.app`
       - SHA-1 fingerprint: Obtain using `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.
2. **Supabase Google Provider**:
   - Under Supabase **Authentication** → **Providers** → **Google**:
     - Enable the provider.
     - Paste the Web Client ID and Web Client Secret from Google Cloud Console.

---

### 4. Local Android Project Configuration

To connect the application to your live Supabase and Google OAuth backend, add your credentials to `local.properties` (which is `.gitignore`d) or `gradle.properties`:

```properties
# ~/.gradle/gradle.properties or /path/to/Roamly/local.properties
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOi...your-public-anon-key
GOOGLE_WEB_CLIENT_ID=your-google-web-client-id.apps.googleusercontent.com
```

Alternatively, export them as environment variables during build:
```bash
export SUPABASE_URL="https://your-project-id.supabase.co"
export SUPABASE_ANON_KEY="eyJhbGciOi...your-anon-key"
export GOOGLE_WEB_CLIENT_ID="your-client-id.apps.googleusercontent.com"
./gradlew assembleDebug
```

If these values are omitted, the app compiles with safe fallback placeholders and clearly prompts you to configure credentials when an online authentication action is initiated.

---

### 5. Guide Verification & Administrative Approval

Guide accounts are restricted to prevent unauthorised access to private traveller data, group check-ins, and medical notes.

When a user signs up as a Guide:
1. Their account is registered with `role = 'guide'` and `approval_status = 'pending'`.
2. The user sees the **Guide Application Pending** screen with their verification details.
3. An administrator approves the account in the Supabase Dashboard:

```sql
-- Run in Supabase SQL Editor as Administrator:
UPDATE public.profiles
SET approval_status = 'approved'
WHERE email = 'guide@example.com';
```

4. Once approved, tapping **"Refresh Status"** in the app immediately unlocks the full Guide Dashboard and traveller roster.

