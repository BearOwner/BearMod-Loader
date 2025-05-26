@echo off
echo Testing KeyAuth Configuration...
echo.

echo Current Configuration:
echo App Name: com.bearmod.loader
echo Owner ID: yLoA9zcOEF
echo Version: 1.0
echo.

echo Testing with current config (WITH SECRET):
curl -X POST "https://keyauth.win/api/1.3/" ^
  -d "type=init&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&hash=testhash" ^
  --connect-timeout 10 -w "HTTP Status: %%{http_code}\n"

echo.
echo.

echo Testing WITHOUT secret (old way):
curl -X POST "https://keyauth.win/api/1.3/" ^
  -d "type=init&name=com.bearmod.loader&ownerid=yLoA9zcOEF&ver=1.0&sessionid=test123&hash=testhash" ^
  --connect-timeout 10 -w "HTTP Status: %%{http_code}\n"

echo.
echo.

echo Testing license validation with your key (WITH SECRET):
curl -X POST "https://keyauth.win/api/1.3/" ^
  -d "type=license&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&key=lEOEtm-OvCMIO-FgUWb4-wciL32-gzHm3g&hwid=test-hwid" ^
  --connect-timeout 10 -w "HTTP Status: %%{http_code}\n"

pause
