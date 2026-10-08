@echo off
setlocal
cd /d "%~dp0"
rem Network IP detection runs inside the Java application on every startup.
call mvnw.cmd spring-boot:run
exit /b %errorlevel%
