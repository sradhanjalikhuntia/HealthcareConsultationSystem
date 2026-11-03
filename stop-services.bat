@echo off
echo ========================================================
echo   Stopping Healthcare Platform Services
echo ========================================================

echo Stopping Java services on ports 8761, 8081, 8082, 8083, 8084, 8085...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8761 :8081 :8082 :8083 :8084 :8085 :9095"') do (
    taskkill /F /PID %%a 2>nul
)

echo Stopping Frontend on port 5173...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173"') do (
    taskkill /F /PID %%a 2>nul
)

echo All services stopped.

