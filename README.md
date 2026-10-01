<div align="center">
  <h1>ByteShare</h1>
  <p><b>Don't just measure screen time. Make it matter.</b></p>
  <p>ByteShare turns screen-time into something that actually matters by connecting your screen-time behavior with social competition and shared bill splitting.</p>
</div>

<br>

## 📖 Table of Contents
- [Project Overview](#project-overview)
- [Problem & Solution](#problem--solution)
- [How ByteShare Works](#how-byteshare-works)
- [Weighted Screen-Time System](#weighted-screen-time-system)
- [Features](#features)
  - [Friends & Leaderboard](#friends--leaderboard)
  - [Friend Usage Detail](#friend-usage-detail)
  - [Stats](#stats)
  - [Crews](#crews)
  - [Bill Splitting / Fame Engine](#bill-splitting--fame-engine)
  - [Challenges / Tasks](#challenges--tasks)
  - [Profile / Me](#profile--me)
- [User Experience / UI Design](#user-experience--ui-design)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [Android Permissions & Privacy](#android-permissions--privacy)
- [Project Structure](#project-structure)
- [Setup & Installation](#setup--installation)
- [Testing & Current Status](#testing--current-status)
- [Challenges & Learnings](#challenges--learnings)
- [Why ByteShare?](#why-byteshare)
- [Blog & Documentation](#blog--documentation)
- [Screenshots](#screenshots)

---

## 🌟 Project Overview
ByteShare is an Android digital-wellness application that connects screen-time behavior with social competition and shared bill splitting.

Most screen-time applications tell users how much time they spend on their phones, but simply knowing the number does not necessarily change behavior. ByteShare introduces a consequence and incentive: **your screen-time behavior influences your weighted score, ranking, and ultimately your share of a group bill.**

The application combines:
- Screen-time tracking
- Weighted usage scoring
- Friends
- Leaderboards
- Crews
- Challenges
- Bill splitting
- Personal activity/profile information

---

## 🛑 Problem & Solution

### The Problem
Social media and entertainment apps compete aggressively for user attention. Users often know they are spending too much time on their phones. Traditional screen-time tools primarily provide information, but information alone does not always create an immediate behavioral incentive to put the phone down.

### The Solution
ByteShare explores a different approach by connecting phone usage to a shared financial and social outcome. 

**Core flow:**
1. Phone usage
2. App/category classification
3. Weighted screen-time score
4. Friends / leaderboard
5. Crew
6. Bill calculation

Lower weighted usage can result in a more favorable position in the bill-splitting system. ByteShare does not simply count raw screen time; it assigns context to how you use your device.

---

## ⚙️ How ByteShare Works

1. **User installs ByteShare.**
2. **ByteShare reads Android app usage statistics** (requires Android usage-access permission).
3. **App usage is categorized** (e.g., Social, Streaming, Productive).
4. **Different categories receive different score weights.**
5. **The user's weighted score is calculated.**
6. **Users can compare their performance with friends.**
7. **Users can create or join a Crew.**
8. **Crew members can use their screen-time performance when calculating a shared bill.**
9. **Users can participate in challenges** to reduce their usage and earn applicable rewards.
10. **Users can view their activity** through the Profile section.

---

## ⚖️ Weighted Screen-Time System

Not all screen time represents the same kind of phone usage. ByteShare distinguishes between different types of phone usage rather than treating every minute identically. 

The current categories are weighted as follows:
- **SOCIAL:** Weight `2.0x`
- **STREAMING:** Weight `1.5x`
- **NEUTRAL:** Weight `1.0x`
- **PRODUCTIVE:** Weight `0.5x`

**Formula:**
`Weighted Minutes = Actual Usage Minutes × Category Weight`

**Example:**
- 60 minutes of Social usage → 60 × 2.0 = 120 weighted minutes
- 60 minutes of Productive usage → 60 × 0.5 = 30 weighted minutes

---

## ✨ Features

### Key Features Summary
- 📱 Android screen-time tracking
- ⚖️ Weighted screen-time scoring
- 👥 Friends and leaderboard
- 🏠 Crew creation and joining
- 💰 Screen-time-based bill splitting
- 🎯 Screen-time challenges
- 🏆 Rewards / multiplier reductions where implemented
- 👤 Personal profile and activity
- 🔔 Social nudges where implemented

### Friends & Leaderboard
Users can view their friends and crew members via a leaderboard. Members are ranked based on their relevant weighted screen-time performance. This fosters friendly social competition. You can see raw usage, weighted usage, and the UI includes a "Send Nudge" interaction (where implemented).

### Friend Usage Detail
A detailed view for friends/members shows:
- Profile/avatar
- Raw screen time
- Weighted score
- Top usage category
- Total usage for the day

### Stats
The Stats screen breaks down daily usage:
- Today's screen time
- Daily target
- Today's weighted score
- Usage breakdown by category
- Category percentages
- Category-specific weights

*Example: 5h 9m total screen time, 440m weighted score. (Social: 1h 42m × 2.0, Streaming: 58m × 1.5, Neutral: 2h 28m × 1.0, Productive: 0m × 0.5)*

### Crews
A Crew is a group where users participate together. Users can create a new Crew or join an existing one using a code. Crew members can participate in the shared screen-time/bill experience. The Crew Hub UI displays the member count, bill information, and amount owed.

### Bill Splitting / Fame Engine
The core bill calculation system uses a user's weighted screen-time performance to influence a multiplier.

*Example for a ₹2,000 bill with four members:*
- **Rank 1:** 0.5× multiplier → ₹250
- **Rank 2:** 0.8× multiplier → ₹400
- **Rank 3:** 1.2× multiplier → ₹600
- **Rank 4:** 1.5× multiplier → ₹750

*(Multipliers total 4.0, so: ₹250 + ₹400 + ₹600 + ₹750 = ₹2,000)*
This preserves the total bill while changing how much each member contributes based on their focus. 
*(Note: Actual payment settlement features are in development; the bill calculation engine is the implemented functionality).*

### Challenges / Tasks
Challenges provide concrete goals for reducing or controlling usage:
- **Screen Sabbath**
- **The Morning Fast**
- **Dinner Table Detox**
- **Stream Curfew**

These can include limits on social media, morning usage, or streaming. Some challenges provide multiplier reductions/rewards (e.g., -0.2 or -0.1 multipliers).

### Profile / Me
The Profile screen displays:
- User identity/profile
- Current streak
- XP/level information
- Crew count
- Amount owed / "You Owe This Week"
- Screen-time overview and history

---

## 🎨 User Experience / UI Design
ByteShare uses a bold, modern, Neo-Brutalism inspired design:
- Bold typography
- Rounded cards with thick borders and high-contrast black/dark elements
- Lime-green accent against a light/off-white background
- Gamified visual language
- Bottom navigation with clear hierarchy
- Social/competitive visual elements
- Mobile-first Android experience using traditional Android XML layouts and Material components.

---

## 🏗️ Architecture & Tech Stack

### Tech Stack
- **Language:** Kotlin
- **Platform:** Android SDK (minSdk 24, targetSdk 35)
- **UI:** XML Layouts & Fragments, Material Components
- **Data Collection:** Android UsageStats / UsageStatsManager
- **Build System:** Gradle (Kotlin DSL)
- **Backend/Services:** Firebase (Auth, Firestore, Realtime Database, Analytics, BoM)
- **Monetization/Payments:** AdMob, RevenueCat, Stripe

### Architecture
ByteShare follows a decoupled MVVM architecture:
- **UI Layer:** XML Layouts and ViewModels
- **Usage Statistics Layer:** Uses `UsageStatsCollector` to read from the OS.
- **Scoring/Category Logic:** Processes raw stats into weighted scores.
- **Crew/Bill Logic:** Calculations and multiplier engines.
- **Persistence/Network Layer:** Firebase repositories for syncing crew and user data.

---

## 🔒 Android Permissions & Privacy
ByteShare requires the following Android permissions to function:
- **`PACKAGE_USAGE_STATS`:** Required for the `UsageStatsManager` to access device usage history and calculate screen-time. Users must manually grant this in Android Settings.
- **`INTERNET`:** To sync Crews, leaderboards, and bills via Firebase.
- **`READ_CONTACTS`:** To find friends.

**Privacy:**
Usage access is entirely opt-in. Raw usage stats are processed to determine categories and times. Be sure to review the specific implementations in the source code regarding local processing vs. cloud synchronization for your data.

---

## 📁 Project Structure

```text
ByteShare/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/
│       ├── main/
│       │   ├── java/com/example/byteshare/
│       │   │   ├── data/
│       │   │   ├── logic/
│       │   │   └── ui/
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       └── test/
├── Screenshots/
├── backend-python/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 Setup & Installation

### Prerequisites
- Android Studio (latest stable recommended)
- JDK 11
- Android SDK 35 (minSdk 24)

### Installation
1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd ByteShare
   ```
2. Open the project in Android Studio.
3. Allow Gradle to sync.
4. **Configuration:** A valid `google-services.json` must be placed in the `app/` directory for Firebase services to work.

### Running the Project
1. Connect a physical Android device or start an emulator (API 24+).
2. Click **Run** in Android Studio or use the CLI: `./gradlew assembleDebug`
3. Install the application on the device.
4. Go through onboarding and **grant the Usage Access permission** when prompted.
5. Start tracking your ByteShare score!

---

## 🧪 Testing & Current Status

### Testing
The project includes structures for:
- **Unit Tests:** Found in `app/src/test`.
- **Instrumentation Tests:** Found in `app/src/androidTest`.
- **Manual Testing:** Requires physical devices for accurate `UsageStatsManager` behavior since emulators often lack natural app usage history.

### Project Status

**Implemented:**
- Android screen-time tracking and categorization
- Weighted scoring system
- Firebase authentication and Crew syncing
- Bill calculation engine
- XML-based UI and dashboard navigation

**Future Scope / Planned:**
- Real-money payment integration/settlement (Stripe/RevenueCat integrations exist in codebase but complete settlement flows are planned)
- More sophisticated behavioral insights and dynamic categorization
- Expanded challenge types and social nudges
- Improved offline caching

---

## 🧠 Challenges & Learnings
Building ByteShare required overcoming several technical and product challenges:
- **Reading Android Usage Statistics:** Handling edge cases and OEM-specific permission screens for `UsageStatsManager`.
- **Designing a Weighted Scoring System:** Creating a balanced algorithm that feels fair to users.
- **Transparent Bill Calculations:** Ensuring the Fame Engine's math is easy to understand in the UI.
- **Gamified Experience:** Maintaining a consistent, engaging UI across screens using traditional XML layouts.

---

## 💡 Why ByteShare?
**"Don't just measure screen time. Make it matter."**
ByteShare attempts to turn passive screen-time awareness into an active social and financial incentive. By attaching real-world group consequences to digital habits, we aim to help people stay present when it counts.

---

## 🎥 Video / Demo
*https://youtu.be/B5pr0lFGqRw*

---

## 📝 Blog & Documentation

### Blog
Read the full story on Medium → 
[We Didn't Build Another Screen-Time Tracker. We Made Screen Time Consequential.](https://medium.com/)

### Documentation
Project Documentation (Notion) → 
[Project Documentation](https://notion.so/)

---

## 📸 Screenshots

<div align="center">

<h3>Friends / Leaderboard</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.54%20PM.jpeg" alt="Friends and Leaderboard" width="300" />
<br>

<h3>Friend/Member Detail</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.54%20PM%20(1).jpeg" alt="Friend Detail View" width="300" />
<br>

<h3>Crew Hub</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.55%20PM.jpeg" alt="Crew Hub" width="300" />
<br>

<h3>Stats</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.55%20PM%20(1).jpeg" alt="Stats Breakdown" width="300" />
<br>

<h3>Challenges</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.55%20PM%20(2).jpeg" alt="Challenges View" width="300" />
<br>

<h3>Profile / Me</h3>
<img src="Screenshots/WhatsApp%20Image%202026-10-01%20at%2011.40.55%20PM%20(3).jpeg" alt="User Profile" width="300" />
<br>

</div>



