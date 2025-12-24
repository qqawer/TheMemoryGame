# Android Client - Leaderboard + Ads

This Android module implements:
- Login + JWT storage (SessionManager)
- Main screen shows Ads for non-paid users (calls GET /api/Ad/active)
- Submit demo score (POST /api/Scores/submit)
- Leaderboard page (GET /api/Scores/leaderboard)

Notes:
- Android emulator访问本机后端使用: http://10.0.2.2:5011
