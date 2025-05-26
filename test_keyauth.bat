@echo off
echo Starting KeyAuth Test Activity...
echo.
echo This will launch the KeyAuth test interface on your connected Android device.
echo Make sure your device is connected and USB debugging is enabled.
echo.

REM Launch the KeyAuth test activity directly
adb shell am start -n com.bearmod.loader/.test.KeyAuthTestActivity

echo.
echo KeyAuth Test Activity launched!
echo.
echo Instructions:
echo 1. The test activity should now be open on your device
echo 2. The license key is pre-filled: lEOEtm-OvCMIO-FgUWb4-wciL32-gzHm3g
echo 3. Follow these steps to test:
echo    a) Tap "Initialize KeyAuth" first
echo    b) Wait for initialization to complete
echo    c) Tap "Login" to test authentication
echo    d) Tap "Validate License" to test validation
echo    e) Tap "Logout" to test logout functionality
echo.
echo Watch the Status and Result sections for feedback.
echo Check the Android logs for detailed information:
echo   adb logcat -s KeyAuthManager:* KeyAuthTestActivity:*
echo.
pause
