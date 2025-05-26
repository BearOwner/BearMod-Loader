@echo off
echo Testing KeyAuth Server Connectivity...
echo.

echo Testing Primary Server (keyauth.win):
curl -v -X POST "https://keyauth.win/api/1.3/" -d "type=init&name=test&ownerid=test&ver=1.0&sessionid=test" --connect-timeout 10
echo.
echo.

echo Testing Backup Server (keyauth.cc):
curl -v -X POST "https://keyauth.cc/api/1.3/" -d "type=init&name=test&ownerid=test&ver=1.0&sessionid=test" --connect-timeout 10
echo.
echo.

echo Testing DNS Resolution:
nslookup keyauth.win
echo.
nslookup keyauth.cc
echo.

pause
