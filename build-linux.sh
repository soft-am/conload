#!/usr/bin/env bash
# ============================================================
#  Build conload native Linux installer (.deb and/or .rpm)
#  Run this script ON Linux.
#
#  Usage:
#    ./build-linux.sh deb          # build .deb only
#    ./build-linux.sh rpm          # build .rpm only
#    ./build-linux.sh deb rpm      # build both (one Maven build pass)
#
#  Requirements:
#    - Liberica Full JDK 21+ (includes JavaFX)
#      https://bell-sw.com/pages/downloads/#jdk-21-lts
#      OR Azul Zulu FX JDK 21+
#    - Maven 3.8+
#    - dpkg (for .deb) or rpm (for .rpm)
#    - Place icon at  package/linux/icon.png  (optional, 48x48 or 256x256 PNG)
# ============================================================
set -e
# Choose installer types (default: deb)
TYPES=("$@")
if [ ${#TYPES[@]} -eq 0 ]; then TYPES=("deb"); fi

echo ""
echo "=== conload — Linux Native Build (types: ${TYPES[*]}) ==="
echo ""

# 1. Build JAR + deps ONCE (regardless of how many types requested)
echo "[1/3] Building JAR and copying dependencies..."
mvn clean package -Pnative -DskipTests -q

# 2. Determine icon arg
ICON_ARG=""
if [ -f "package/linux/icon.png" ]; then
    ICON_ARG="--icon package/linux/icon.png"
fi

# 3. jpackage for each requested type
for TYPE in "${TYPES[@]}"; do
    echo "[2/N] Creating Linux .${TYPE} installer..."
    jpackage \
      --input target/libs \
      --main-jar conload-1.0.0.jar \
      --main-class com.conload.App \
      --name conload \
      --app-version 1.0.0 \
      --description "Download Confluence and Jira pages as Markdown for AI/Copilot context" \
      --vendor "conload" \
      --dest target/installer \
      --type "${TYPE}" \
      --linux-shortcut \
      --java-options "--add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base" \
      --java-options "--add-reads=com.conload=ALL-UNNAMED" \
      --java-options "-Xmx512m" \
      $ICON_ARG
done

echo ""
echo "[3/3] Done!"
echo "Installers in target/installer/"
ls -la target/installer/ 2>/dev/null || true
echo ""
