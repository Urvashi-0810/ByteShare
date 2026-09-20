# ByteShare Agent Instructions

This document provides strict guidelines for any AI coding agent working on the ByteShare project. The primary goal is to maintain UI consistency, ensure feature integrity, and adhere to the project's design philosophy.

## 1. UI Consistency and Design Language

ByteShare uses a "Neo-Brutalism" inspired design language.

- **Color Palette:**
    - Background: Off-white/Cream (`#FDFBF0`).
    - Primary Action: Deep Black (`#1A1A1A`).
    - Accents: Lime Green (`#C6FF00`), Vibrant Orange/Peach for streaks.
    - Card Borders: Thick black strokes (2dp) with an offset "shadow" effect.
- **Layouts:**
    - Use traditional XML layouts (`ConstraintLayout` preferred).
    - Maintain the specific card styles and icon types seen in the provided UI images.
    - Navigation: Bottom Navigation strictly following (Friends, Crews, Stats, Tasks, Rank, Me).

## 2. Core Mechanics (The Fame Engine)

Agents must strictly adhere to the following logic:

- **Weighted App Categories:**
    - Social Media (Instagram, Twitter, Snapchat): **2.0x**
    - Streaming (YouTube, Netflix, Reels): **1.5x**
    - Neutral (Maps, Weather, Calculator): **1.0x**
    - Productivity (Notion, Calendar, Duolingo): **0.5x**
- **Bill Multipliers (Group of 4 Example):**
    - Rank 1: **0.5x**
    - Rank 2: **0.8x**
    - Rank 3: **1.2x**
    - Rank 4: **1.5x**
    - *Total must sum to the number of members.*
- **Redemption Mechanics:**
    - **Screen Sabbaths:** Users can pledge no-phone windows for score reductions.
    - **Detox Challenges:** Group mini-competitions with penalty/reward bumps.
- **Social Triggers:**
    - **Scarlet Score:** Visual badge for the worst offender ("In Jail").
    - **Bill Hostage:** Worst offender blocked from redemption unless "vouched" for.
    - **Intervention Push:** One-tap "nudges" (roasts) sent to the biggest offender.

## 3. Technical Requirements

- **Group Management:** Users can Create or Join groups (using Invite Codes).
- **Permissions:** Gracefully handle `PACKAGE_USAGE_STATS` and `POST_NOTIFICATIONS`.
- **Global Theme:** Use the defined `Theme.ByteShare` and centralized styles in `styles.xml` and `colors.xml`.
- **Modular Code:** Maintain separation between UI, Business Logic (ViewModels), and Data (Usage Stats collection).

## 4. Verification

- **Visual Audit:** Compare UI against mockups after every change.
- **Algorithm Check:** Verify bill split math and weighted score calculations.
- **Persistence:** Ensure group data and rankings are correctly synchronized or stored locally.

---
*Follow these rules to ensure ByteShare remains a high-quality, deployable application.*
