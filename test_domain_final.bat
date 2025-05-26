@echo off
echo Testing Custom Domain: https://enc.mod-key.click/1.2/
echo.

echo Step 1: Basic domain test (should return "Unhandled Type")
curl https://enc.mod-key.click/1.2/ -H "user-agent:1"
echo.
echo.

echo Step 2: Test initialization
curl -X POST https://enc.mod-key.click/1.2/ ^
  -d "type=init&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&hash=testhash" ^
  -H "user-agent:1"
echo.
echo.

echo Step 3: Test license validation
curl -X POST https://enc.mod-key.click/1.2/ ^
  -d "type=license&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&key=lEOEtm-OvCMIO-FgUWb4-wciL32-gzHm3g&hwid=test-hwid" ^
  -H "user-agent:1"
echo.
echo.

pause
