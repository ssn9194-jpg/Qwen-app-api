@echo off
setlocal enabledelayedexpansion
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set VERSION=9.5.0
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set CACHE=%GRADLE_USER_HOME%\ai-studio-wrapper\gradle-%VERSION%
set DIST=%CACHE%\gradle-%VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  if not exist "%CACHE%" mkdir "%CACHE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip'; $z='%CACHE%\gradle-%VERSION%-bin.zip'; Invoke-WebRequest -Uri $u -OutFile $z; Expand-Archive -Path $z -DestinationPath '%CACHE%' -Force"
  if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
)
call "%DIST%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
