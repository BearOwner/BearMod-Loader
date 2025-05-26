@echo off
echo Starting KeyAuth Log Monitor...
echo.
echo This will show real-time logs from the KeyAuth authentication system.
echo Keep this window open while testing the app to see detailed information.
echo.
echo Press Ctrl+C to stop monitoring.
echo.

REM Clear existing logs and start monitoring KeyAuth-related logs
adb logcat -c
adb logcat -s KeyAuthManager:* LoginActivity:* BearLoaderApplication:* KeyAuthTestActivity:*
