@echo off
REM ============================================================
REM  Build conload native Windows installer (.exe)
REM  Run this script ON WINDOWS.
REM
REM  Requirements:
REM    - Liberica Full JDK 21+  (includes JavaFX)
REM      https://bell-sw.com/pages/downloads/#jdk-21-lts
REM      OR Azul Zulu FX JDK 21+
REM      https://www.azul.com/downloads/?version=java-21-lts&package=jdk-fx
REM    - Maven 3.8+
REM    - Place icons/icon.ico in  package\windows\  (optional)
REM ============================================================

setlocal

echo.
echo === conload - Windows Native Build ===
echo.

REM Verify Java version
java -version 2>&1 | findstr /i "liberica\|zulu\|21\." > nul
if errorlevel 1 (
    echo [WARN] Make sure you are using Liberica Full JDK or Azul Zulu FX JDK 21
    echo        which includes JavaFX modules.
    echo.
)

REM 1. Compile + package all dependencies
echo [1/3] Building JAR and copying dependencies...
call mvn clean package -Pnative -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Maven build failed.
    exit /b 1
)

REM 2. jpackage is called by Maven profile — but add icon if present
set ICON_ARG=
if exist "package\windows\icon.ico" (
    set ICON_ARG=--icon package\windows\icon.ico
)

echo [2/3] Creating Windows installer...
jpackage ^
  --input target\libs ^
  --main-jar conload-1.0.1.jar ^
  --main-class com.conload.App ^
  --name conload ^
  --app-version 1.0.1 ^
  --description "Download Confluence and Jira pages as Markdown for AI/Copilot context" ^
  --vendor "conload" ^
  --dest target\installer ^
  --type exe ^
  --win-dir-chooser ^
  --win-shortcut ^
  --win-menu ^
  --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base" ^
  --java-options "--add-reads=com.conload=ALL-UNNAMED" ^
  --java-options "-Xmx512m" ^
  %ICON_ARG%

if errorlevel 1 (
    echo [ERROR] jpackage failed. Make sure you are using Liberica Full JDK or Azul Zulu FX JDK.
    exit /b 1
)

echo.
echo [3/3] Done!
echo Installer: target\installer\conload-1.0.1.exe
echo.

