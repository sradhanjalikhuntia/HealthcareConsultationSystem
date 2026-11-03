@echo off
echo ========================================================
echo   Starting Healthcare Microservices Platform
echo ========================================================

echo [1/7] Starting Eureka Discovery Server (Port 8761)...
start "Eureka Server [8761]" cmd /k "java -Xms64m -Xmx256m -jar backend\eureka-server\target\eureka-server-1.0.0.jar"
ping 127.0.0.1 -n 11 >nul

echo [2/7] Starting Auth Service (Port 8081)...
start "Auth Service [8081]" cmd /k "java -Xms64m -Xmx256m -jar backend\auth-service\target\auth-service-1.0.0.jar"

echo [3/7] Starting Patient Service (Port 8082)...
start "Patient Service [8082]" cmd /k "java -Xms64m -Xmx256m -jar backend\patient-service\target\patient-service-1.0.0.jar"

echo [4/7] Starting Appointment Service (Port 8083)...
start "Appointment Service [8083]" cmd /k "java -Xms64m -Xmx256m -jar backend\appointment-service\target\appointment-service-1.0.0.jar"

echo [5/7] Starting Billing Service (Port 8084)...
start "Billing Service [8084]" cmd /k "java -Xms64m -Xmx256m -jar backend\billing-service\target\billing-service-1.0.0.jar"
ping 127.0.0.1 -n 11 >nul

echo [6/7] Starting API Gateway (Port 8085)...
start "API Gateway [8085]" cmd /k "java -Xms64m -Xmx256m -jar backend\api-gateway\target\api-gateway-1.0.0.jar"
ping 127.0.0.1 -n 6 >nul

echo [7/7] Starting Frontend Server (Port 5173)...
start "Frontend [5173]" cmd /k "cd frontend && npm run dev"

echo ========================================================
echo   All services launched!
echo   Frontend: http://localhost:5173
echo   Eureka:   http://localhost:8761
echo   Gateway:  http://localhost:8085
echo ========================================================

