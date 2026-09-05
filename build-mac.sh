#!/usr/bin/env bash
# ============================================================
#  Build conload native macOS installer (.dmg)
#  Run this script ON macOS.
#
#  Requirements:
#    - Liberica Full JDK 21+ (includes JavaFX)
#      https://bell-sw.com/pages/downloads/#jdk-21-lts
#      OR Azul Zulu FX JDK 21+
#      https://www.azul.com/downloads/?version=java-21-lts&package=jdk-fx
#    - Maven 3.8+
#    - Place icon at  package/mac/icon.icns  (optional)
# ============================================================
set -e
echo ""
echo "=== conload — macOS Native Build ==="
echo ""
# 1. Build JAR + deps
echo "[1/3] Building JAR and copying dependencies..."
mvn clean package -Pnative -DskipTests -q
# 2. Determine icon arg
ICON_ARG=""
if [ -f "package/mac/icon.icns" ]; then
    ICON_ARG="--icon package/mac/icon.icns"
fi
# 3. jpackage
echo "[2/3] Creating macOS .dmg installer..."
jpackage \
  --input target/libs \
  --main-jar conload-1.0.1.jar \
  --main-class com.conload.App \
  --name conload \
  --app-version 1.0.1 \
  --description "Download Confluence and Jira pages as Markdown for AI/Copilot context" \
  --vendor "conload" \
  --dest target/installer \
  --type dmg \
  --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base" \
  --java-options "--add-reads=com.conload=ALL-UNNAMED" \
  --java-options "-Xmx512m" \
  $ICON_ARG
echo ""
echo "[3/3] Done!"
echo "Installer: target/installer/conload-1.0.1.dmg"
echo ""
