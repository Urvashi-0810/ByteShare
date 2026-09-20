# ScreenSplit 

A gamified social platform that encourages healthy screen time habits. Track device usage, compete with friends on leaderboards, join crews, and earn rewards—all while building a community focused on digital wellness.

## About the Project

ScreenSplit turns screen time management into a social competition. Instead of just limiting usage, users earn points for maintaining healthy habits, compete against friends, and unlock achievements. The platform includes crew-based challenges where teams work together toward goals, transparent bill splitting for shared subscriptions, and a ranking system that motivates consistent engagement.

The core innovation is combining **personal tracking** with **social competition** and **group collaboration**—making digital wellness fun and sustainable.

## Project Structure

```
summer-hacks/
├── backend-service/    - Go API service
├── frontend-web/       - React web application
└── mobile-client/      - Android app
```

## Features

**Screen Time Analytics**
- Track daily device usage automatically
- View usage patterns and trends over time
- Monitor app-level breakdowns
- Set healthy usage goals

**Social Competition**
- Real-time leaderboards with friend rankings
- Earn points for maintaining healthy habits
- Rise through ranks from beginner to power user
- View detailed user profiles and statistics

**Crews & Teamwork**
- Create or join crews (groups with 2-50 members)
- Complete crew-based challenges together
- Pool members to reach collective goals
- Track individual contribution within crews

**Smart Bill Splitting**
- Automatically calculate fair bill breakdowns within crews
- Split costs based on usage or equally among members
- Transparent cost allocation
- Track redemptions and settlements

**Friend System**
- Add friends and track their rankings
- Send and manage friend requests
- View friend activity and stats
- Private or public profile options

**Gamification & Challenges**
- Daily, weekly, and monthly challenges
- Earn badges and achievements
- Participate in global challenges
- Redeem points for rewards

**Security & Privacy**
- OAuth authentication (Google, GitHub, etc.)
- Two-factor authentication (OTP) support
- End-to-end encrypted messages
- User data privacy controls

**Cross-Platform**
- Web app for desktop/laptop usage
- Mobile app for on-the-go access
- Synchronized data across devices
- Offline-first capabilities

## Tech Stack

- **Backend**: Go with PocketBase
- **Frontend**: React + TypeScript + Vite
- **Mobile**: Android (Kotlin)
- **Database**: PocketBase

## Quick Setup

### Backend
```bash
cd backend-service
# Set up config.yml
go run main.go
```

### Frontend Web
```bash
cd frontend-web
bun install
bun run dev
```

### Mobile
```bash
cd mobile-client
./gradlew build
./gradlew installDebug
```

## UI Screenshots

**Dashboard & Home**

<div align="center">
<img src="Screenshots/Dashboard.png" alt="Dashboard Screenshot" width="350">
</div>

**Leaderboard**

<div align="center">
<img src="Screenshots/Leaderboard.png" alt="Leaderboard Screenshot" width="350">
</div>

**Crew Details**

<div align="center">
<img src="Screenshots/CrewDetails.jpeg" alt="Crew Details Screenshot" width="350">
</div>

**Bill Breakdown**

<div align="center">
<img src="Screenshots/bill_breakdown.jpeg" alt="Bill Breakdown Screenshot" width="350">
</div>

**User Profile**

<div align="center">
<img src="Screenshots/profile.png" alt="User Profile Screenshot" width="350">
</div>


## License

MIT — free like mass mammoth on open plain.
