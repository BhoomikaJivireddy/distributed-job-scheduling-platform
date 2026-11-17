@echo off
setlocal

set "BASE_DIR=%~dp0"
set "PROPERTIES_FILE=%BASE_DIR%.mvn\wrapper\maven-wrapper.properties"

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES_FILE%") do (
    if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
)

for %%F in ("%DISTRIBUTION_URL%") do set "ARCHIVE_NAME=%%~nxF"
set "DISTRIBUTION_NAME=%ARCHIVE_NAME:-bin.zip=%"

if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "INSTALL_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DISTRIBUTION_NAME%"

if not exist "%INSTALL_DIR%\bin\mvn.cmd" (
    set "TEMP_DIR=%TEMP%\mvnw-%RANDOM%"
    mkdir "%TEMP_DIR%"
    powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -Uri '%DISTRIBUTION_URL%' -OutFile '%TEMP_DIR%\maven.zip'; Expand-Archive -Path '%TEMP_DIR%\maven.zip' -DestinationPath '%TEMP_DIR%' -Force"
    mkdir "%INSTALL_DIR%"
    xcopy /E /Q /Y "%TEMP_DIR%\%DISTRIBUTION_NAME%\*" "%INSTALL_DIR%\" >nul
    rmdir /S /Q "%TEMP_DIR%"
)

call "%INSTALL_DIR%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
