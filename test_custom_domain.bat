@echo off
echo Testing KeyAuth Custom Domain...
echo.

echo Custom Domain: https://enc.mod-key.click/1.2/
echo App Name: com.bearmod.loader
echo Owner ID: yLoA9zcOEF
echo Version: 1.0
echo.

echo Testing INITIALIZATION with custom domain:
curl -X POST "https://enc.mod-key.click/1.2/" ^
  -d "type=init&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&hash=testhash" ^
  --connect-timeout 10 -w "HTTP Status: %%{http_code}\n"

echo.
echo.

echo Testing LICENSE VALIDATION with custom domain:
curl -X POST "https://enc.mod-key.click/1.2/" ^
  -d "type=license&name=com.bearmod.loader&ownerid=yLoA9zcOEF&secret=e99363a37eaa69acf4db6a6d4781fdf464cd4b429082de970a08436cac362d7d&ver=1.0&sessionid=test123&key=lEOEtm-OvCMIO-FgUWb4-wciL32-gzHm3g&hwid=test-hwid" ^
  --connect-timeout 10 -w "HTTP Status: %%{http_code}\n"

echo.
echo.

echo Testing DNS resolution:
nslookup enc.mod-key.click

pause
